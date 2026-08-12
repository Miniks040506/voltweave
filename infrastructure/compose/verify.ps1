param(
  [string]$EnvFile
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $EnvFile) {
  $localEnv = Join-Path $PSScriptRoot ".env"
  $EnvFile = if (Test-Path $localEnv) {
    $localEnv
  } else {
    Join-Path $PSScriptRoot ".env.example"
  }
}

$EnvFile = (Resolve-Path $EnvFile).Path
$composeFile = Join-Path $PSScriptRoot "compose.yml"
$settings = @{}

Get-Content $EnvFile |
  Where-Object { $_ -match "^[A-Z0-9_]+=" } |
  ForEach-Object {
    $name, $value = $_ -split "=", 2
    $settings[$name] = $value
  }

function Get-Setting([string]$Name) {
  if (-not $settings.ContainsKey($Name) -or -not $settings[$Name]) {
    throw "Missing $Name in $EnvFile"
  }
  $settings[$Name]
}

function Assert-Equal([string]$Label, $Actual, $Expected) {
  if ($Actual -ne $Expected) {
    throw "$Label failed. Expected '$Expected', got '$Actual'."
  }
  Write-Host "PASS $Label"
}

function Invoke-AdminSql([string]$Database, [string]$Sql) {
  $password = Get-Setting "POSTGRES_ADMIN_PASSWORD"
  $result = & docker compose --env-file $EnvFile -f $composeFile exec -T `
    -e "PGPASSWORD=$password" postgres psql `
    -U (Get-Setting "POSTGRES_ADMIN_USER") -d $Database -tA -c $Sql
  if ($LASTEXITCODE -ne 0) {
    throw "SQL verification failed for $Database."
  }
  ($result -join "`n").Trim()
}

& docker compose --env-file $EnvFile -f $composeFile config --quiet
if ($LASTEXITCODE -ne 0) {
  throw "Compose configuration is invalid."
}

& docker compose --env-file $EnvFile -f $composeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
  throw "Sandbox did not become healthy."
}
Write-Host "PASS Compose services are healthy"

$expectedTopics = @(
  "vw.audit.v1"
  "vw.audit.v1.dlq"
  "vw.command.lifecycle.v1"
  "vw.dispatch.lifecycle.v1"
  "vw.portfolio.lifecycle.v1"
  "vw.settlement.lifecycle.v1"
  "vw.telemetry.normalized.v1"
  "vw.telemetry.raw.v1"
) -join "`n"
$actualTopics = ""
for ($attempt = 1; $attempt -le 20; $attempt++) {
  $topicLines = & docker compose --env-file $EnvFile -f $composeFile exec -T `
    kafka /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server localhost:19092 --list
  if ($LASTEXITCODE -eq 0) {
    $actualTopics = ($topicLines | Sort-Object) -join "`n"
    if ($actualTopics -eq $expectedTopics) {
      break
    }
  }
  Start-Sleep -Seconds 1
}
Assert-Equal "Kafka topics" $actualTopics $expectedTopics

$mqttAdminResult = & docker compose --env-file $EnvFile -f $composeFile exec -T `
  mosquitto mosquitto_ctrl -h localhost -p 1883 `
  -u (Get-Setting "MOSQUITTO_ADMIN_USERNAME") `
  -P (Get-Setting "MOSQUITTO_ADMIN_PASSWORD") `
  dynsec listRoles
if ($LASTEXITCODE -ne 0) {
  throw "Mosquitto Dynamic Security admin authentication failed."
}
Assert-Equal "Mosquitto Dynamic Security" `
  (($mqttAdminResult -join "`n") -match "admin") $true

$mqttClientResult = & docker compose --env-file $EnvFile -f $composeFile exec -T `
  mosquitto mosquitto_ctrl -h localhost -p 1883 `
  -u (Get-Setting "MOSQUITTO_ADMIN_USERNAME") `
  -P (Get-Setting "MOSQUITTO_ADMIN_PASSWORD") `
  dynsec getClient (Get-Setting "MQTT_TELEMETRY_USERNAME")
if ($LASTEXITCODE -ne 0) {
  throw "Telemetry MQTT identity was not initialized."
}
Assert-Equal "Telemetry MQTT client id" `
  (($mqttClientResult -join "`n") -match "telemetry-service") $true
Assert-Equal "Telemetry MQTT role" `
  (($mqttClientResult -join "`n") -match "telemetry-reader") $true

$mqttRoleResult = & docker compose --env-file $EnvFile -f $composeFile exec -T `
  mosquitto mosquitto_ctrl -h localhost -p 1883 `
  -u (Get-Setting "MOSQUITTO_ADMIN_USERNAME") `
  -P (Get-Setting "MOSQUITTO_ADMIN_PASSWORD") `
  dynsec getRole telemetry-reader
if ($LASTEXITCODE -ne 0) {
  throw "Telemetry MQTT role was not initialized."
}
Assert-Equal "Telemetry MQTT topic scope" `
  (($mqttRoleResult -join "`n") -match "voltweave/\+/\+/\+/telemetry") $true

$expectedOwners = @(
  "dispatch_db:dispatch_app"
  "intelligence_db:intelligence_app"
  "keycloak_db:keycloak_app"
  "portfolio_db:portfolio_app"
  "settlement_db:settlement_app"
  "telemetry_db:telemetry_app"
) -join "`n"
$ownerSql = @"
SELECT datname || ':' || pg_get_userbyid(datdba)
FROM pg_database
WHERE datname IN ('portfolio_db', 'telemetry_db', 'intelligence_db',
                  'dispatch_db', 'settlement_db', 'keycloak_db')
ORDER BY datname
"@
Assert-Equal "database ownership" (Invoke-AdminSql "postgres" $ownerSql) $expectedOwners

$restrictedRolesSql = @"
SELECT count(*)
FROM pg_roles
WHERE rolname IN ('portfolio_app', 'telemetry_app', 'intelligence_app',
                  'dispatch_app', 'settlement_app', 'keycloak_app')
  AND NOT rolsuper
  AND NOT rolcreatedb
  AND NOT rolcreaterole
  AND NOT rolreplication
"@
Assert-Equal "restricted application roles" `
  (Invoke-AdminSql "postgres" $restrictedRolesSql) "6"

$databases = @(
  "postgres", "template1", "portfolio_db", "telemetry_db",
  "intelligence_db", "dispatch_db", "settlement_db", "keycloak_db"
)
foreach ($database in $databases) {
  $version = Invoke-AdminSql $database `
    "SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'"
  $expected = if ($database -eq "telemetry_db") { "2.29.0" } else { "" }
  Assert-Equal "TimescaleDB in $database" $version $expected
}

$keycloakBaseUrl = "http://localhost:$(Get-Setting 'KEYCLOAK_HOST_PORT')"
$discovery = Invoke-RestMethod `
  "$keycloakBaseUrl/realms/voltweave/.well-known/openid-configuration"
Assert-Equal "OIDC issuer" $discovery.issuer "$keycloakBaseUrl/realms/voltweave"

$adminToken = Invoke-RestMethod -Method Post `
  -Uri "$keycloakBaseUrl/realms/master/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    client_id = "admin-cli"
    username = Get-Setting "KEYCLOAK_ADMIN_USERNAME"
    password = Get-Setting "KEYCLOAK_ADMIN_PASSWORD"
    grant_type = "password"
  }
$headers = @{ Authorization = "Bearer $($adminToken.access_token)" }
$roles = Invoke-RestMethod -Headers $headers `
  -Uri "$keycloakBaseUrl/admin/realms/voltweave/roles"
$appRoles = ($roles.name | Where-Object {
  $_ -in @("CUSTOMER", "VPP_OPERATOR", "ADMIN")
} | Sort-Object) -join ","
Assert-Equal "realm roles" $appRoles "ADMIN,CUSTOMER,VPP_OPERATOR"

$users = Invoke-RestMethod -Headers $headers `
  -Uri "$keycloakBaseUrl/admin/realms/voltweave/users"
$demoUsers = ($users.username | Where-Object {
  $_ -in @("customer", "operator", "admin")
} | Sort-Object) -join ","
Assert-Equal "demo users" $demoUsers "admin,customer,operator"

$expectedUserRoles = @{
  admin = "ADMIN"
  customer = "CUSTOMER"
  operator = "VPP_OPERATOR"
}
foreach ($username in $expectedUserRoles.Keys) {
  $user = $users | Where-Object username -eq $username
  $assignedRoles = Invoke-RestMethod -Headers $headers `
    -Uri "$keycloakBaseUrl/admin/realms/voltweave/users/$($user.id)/role-mappings/realm"
  $assignedAppRoles = ($assignedRoles.name | Where-Object {
    $_ -in @("CUSTOMER", "VPP_OPERATOR", "ADMIN")
  }) -join ","
  Assert-Equal "$username role mapping" $assignedAppRoles $expectedUserRoles[$username]
}

$clients = Invoke-RestMethod -Headers $headers `
  -Uri "$keycloakBaseUrl/admin/realms/voltweave/clients"
$webClient = $clients | Where-Object clientId -eq "voltweave-web"
$internalClient = $clients | Where-Object clientId -eq "voltweave-internal"
Assert-Equal "web client uses public PKCE flow" `
  ($webClient.publicClient -and $webClient.standardFlowEnabled -and
    -not $webClient.directAccessGrantsEnabled) $true
Assert-Equal "internal client uses service account" `
  (-not $internalClient.publicClient -and $internalClient.serviceAccountsEnabled -and
    -not $internalClient.directAccessGrantsEnabled) $true

$serviceToken = Invoke-RestMethod -Method Post `
  -Uri "$keycloakBaseUrl/realms/voltweave/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    client_id = "voltweave-internal"
    client_secret = Get-Setting "KEYCLOAK_INTERNAL_CLIENT_SECRET"
    grant_type = "client_credentials"
  }
Assert-Equal "internal client token type" $serviceToken.token_type "Bearer"

Write-Host "VoltWeave infrastructure sandbox verification passed."
