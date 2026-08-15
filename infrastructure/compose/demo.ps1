param(
  [string]$EnvFile,
  [string]$ScenarioFile
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
if (-not $ScenarioFile) {
  $ScenarioFile = Join-Path $PSScriptRoot `
    "../../simulator/simulation-service/scenario.local.json"
}

$EnvFile = (Resolve-Path $EnvFile).Path
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

$gateway = "http://localhost:$(Get-Setting 'GATEWAY_HOST_PORT')"
$keycloak = "http://localhost:$(Get-Setting 'KEYCLOAK_HOST_PORT')/realms/voltweave"

function Get-Token([string]$Username, [string]$Password) {
  (Invoke-RestMethod -Method Post `
    -Uri "$keycloak/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
      client_id = "voltweave-e2e"
      grant_type = "password"
      username = $Username
      password = $Password
    }).access_token
}

function Get-Subject([string]$Token) {
  $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
  $payload += '=' * ((4 - $payload.Length % 4) % 4)
  $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
  ($json | ConvertFrom-Json).sub
}

function Invoke-Api(
  [string]$Method,
  [string]$Path,
  [string]$Token,
  $Body,
  [hashtable]$ExtraHeaders = @{}
) {
  $headers = @{ Authorization = "Bearer $Token" }
  foreach ($item in $ExtraHeaders.GetEnumerator()) {
    $headers[$item.Key] = $item.Value
  }
  $request = @{
    Method = $Method
    Uri = "$gateway$Path"
    Headers = $headers
  }
  if ($null -ne $Body) {
    $request.ContentType = "application/json"
    $request.Body = $Body | ConvertTo-Json -Depth 8
  }
  Invoke-RestMethod @request
}

$adminToken = Get-Token "admin" (Get-Setting "DEMO_ADMIN_PASSWORD")
$customerToken = Get-Token "customer" (Get-Setting "DEMO_CUSTOMER_PASSWORD")
$operatorToken = Get-Token "operator" (Get-Setting "DEMO_OPERATOR_PASSWORD")
$customerSubject = Get-Subject $customerToken
$operatorSubject = Get-Subject $operatorToken
$suffix = (Get-Date).ToUniversalTime().ToString("yyyyMMddHHmmss")

$customerOrganization = Invoke-Api POST "/api/v1/organizations" $adminToken @{
  type = "COMMERCIAL_CUSTOMER"
  legalName = "Demo Energy Customer Ltd"
  displayName = "Demo Customer $suffix"
  tenantCode = "demo-customer-$suffix"
  country = "TH"
  timezone = "Asia/Bangkok"
}
$operatorOrganization = Invoke-Api POST "/api/v1/organizations" $adminToken @{
  type = "VPP_OPERATOR"
  legalName = "Demo VPP Operator Ltd"
  displayName = "Demo Operator $suffix"
  tenantCode = "demo-operator-$suffix"
  country = "TH"
  timezone = "Asia/Bangkok"
}
Invoke-Api POST "/api/v1/organizations/$($customerOrganization.id)/members" `
  $adminToken @{ subjectId = $customerSubject; role = "MEMBER" } | Out-Null
Invoke-Api POST "/api/v1/organizations/$($operatorOrganization.id)/members" `
  $adminToken @{ subjectId = $operatorSubject; role = "MEMBER" } | Out-Null

$site = Invoke-Api POST "/api/v1/sites" $customerToken @{
  organizationId = $customerOrganization.id
  name = "Bangkok Demo Battery"
  timezone = "Asia/Bangkok"
  region = "Bangkok"
  country = "TH"
}
Invoke-Api PATCH "/api/v1/sites/$($site.id)/preferences" $customerToken @{
  vppOptIn = $true
  minimumBatteryReservePercent = 20
} | Out-Null

$device = Invoke-Api POST "/api/v1/devices" $customerToken @{
  siteId = $site.id
  externalDeviceId = "battery-demo-$suffix"
  type = "BATTERY"
  manufacturer = "VoltWeave"
  model = "Sandbox Battery"
  ratedPowerKw = 20
  battery = @{
    capacityKwh = 40
    maxChargeKw = 20
    maxDischargeKw = 20
    minSocPercent = 20
    maxSocPercent = 90
    efficiency = 0.95
  }
  evCharger = $null
}
$provisioning = Invoke-Api POST "/api/v1/devices/$($device.id)/provision" `
  $customerToken $null @{ "Idempotency-Key" = "demo-provision-$suffix" }

$vpp = Invoke-Api POST "/api/v1/vpps" $operatorToken @{
  organizationId = $operatorOrganization.id
  name = "Bangkok Demo VPP $suffix"
  region = "Bangkok"
}
Invoke-Api POST "/api/v1/vpps/$($vpp.id)/sites/$($site.id)" `
  $operatorToken $null | Out-Null

$scenario = @{
  brokerUri = "tcp://localhost:$(Get-Setting 'MQTT_HOST_PORT')"
  telemetryIntervalSeconds = 5
  devices = @(@{
    deviceId = $device.id
    type = "BATTERY"
    mqtt = $provisioning.credential
    ratedPowerKw = 20
    initialSocPercent = 70
    capacityKwh = 40
    minSocPercent = 20
    maxSocPercent = 90
    efficiency = 0.95
    seed = 303
  })
}
$scenario | ConvertTo-Json -Depth 8 | Set-Content $ScenarioFile -Encoding utf8

Write-Host "Demo data created:"
Write-Host "  customerOrganizationId=$($customerOrganization.id)"
Write-Host "  operatorOrganizationId=$($operatorOrganization.id)"
Write-Host "  siteId=$($site.id)"
Write-Host "  deviceId=$($device.id)"
Write-Host "  vppId=$($vpp.id)"
Write-Host "Simulator scenario written to $ScenarioFile"
