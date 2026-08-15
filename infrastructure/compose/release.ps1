param(
  [string]$EnvFile,
  [string]$ProjectName = "voltweave",
  [switch]$NoBuild
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
  $override = [Environment]::GetEnvironmentVariable($Name)
  if ($override) { return $override }
  if (-not $settings.ContainsKey($Name) -or -not $settings[$Name]) {
    throw "Missing $Name in environment or $EnvFile"
  }
  $settings[$Name]
}

function Assert-Equal([string]$Label, $Actual, $Expected) {
  if ($Actual -ne $Expected) {
    throw "$Label failed. Expected '$Expected', got '$Actual'."
  }
  Write-Host "PASS $Label"
}

$composeArgs = @(
  "compose", "--project-name", $ProjectName,
  "--env-file", $EnvFile, "-f", $composeFile, "--profile", "app"
)

& docker @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw "Compose configuration is invalid." }

$upArgs = @("up", "-d", "--wait")
if (-not $NoBuild) { $upArgs += "--build" }
& docker @composeArgs @upArgs
if ($LASTEXITCODE -ne 0) { throw "VoltWeave services did not become healthy." }

$webBase = "http://localhost:$(Get-Setting 'WEB_HOST_PORT')"
$gatewayBase = "http://localhost:$(Get-Setting 'GATEWAY_HOST_PORT')"
$keycloakBase = "http://localhost:$(Get-Setting 'KEYCLOAK_HOST_PORT')"

$web = Invoke-WebRequest $webBase -UseBasicParsing
Assert-Equal "web HTTP status" $web.StatusCode 200

$health = Invoke-RestMethod "$gatewayBase/actuator/health"
Assert-Equal "gateway health" $health.status "UP"

try {
  Invoke-WebRequest "$gatewayBase/api/v1/organizations" -UseBasicParsing | Out-Null
  $anonymousStatus = 200
} catch {
  $anonymousStatus = [int]$_.Exception.Response.StatusCode
}
Assert-Equal "anonymous API rejection" $anonymousStatus 401

$token = Invoke-RestMethod -Method Post `
  -Uri "$keycloakBase/realms/voltweave/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    client_id = "voltweave-e2e"
    grant_type = "password"
    username = "customer"
    password = Get-Setting "DEMO_CUSTOMER_PASSWORD"
  }
if (-not $token.access_token) { throw "Keycloak did not issue an access token." }
Write-Host "PASS customer token"

$authorized = Invoke-WebRequest "$gatewayBase/api/v1/organizations" `
  -Headers @{ Authorization = "Bearer $($token.access_token)" } `
  -UseBasicParsing
Assert-Equal "authenticated Gateway route" $authorized.StatusCode 200

Write-Host "VoltWeave V1 release is healthy at $webBase"
