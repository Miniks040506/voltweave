# VoltWeave performance benchmark

The benchmark measures authenticated read traffic through the API Gateway and
fails when any route exceeds the V1 transactional API p95 budget of 300 ms.

## Reproducible run

Docker Desktop must be running. From the repository root:

```powershell
mvn "-Pe2e,performance" verify
```

This command builds the complete reactor, creates the same isolated platform as
the E2E suite, provisions real tenant/device data and runs a one-user, 30-second
latency baseline. No pre-existing database or IDs are required.

## Measured paths

- `GET /api/v1/sites/{siteId}/devices`: authorization and Portfolio database read.
- `GET /api/v1/sites/{siteId}/live`: authorization and Telemetry twin projection.
- `GET /api/v1/devices/{deviceId}/twin`: authorization and one durable twin read.

Each request enters through Gateway and carries a real customer JWT obtained once
in k6 `setup()`. Keep custom durations below the configured access-token lifespan.
The test fails
if checks drop to 99%, failed HTTP requests reach 1%, or any route p95 reaches
300 ms.

## Record the environment

Performance numbers are meaningful only with their hardware and command. Capture:

```powershell
Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory
docker version
java -version
mvn -version
```

Keep the k6 summary with those values when publishing a result.

## Scope of the claim

This script proves the transactional read latency budget. It does not claim the
stretch target of 5,000 simulated devices or 1,000 MQTT messages/second. That
requires a credential fleet generator and distributed MQTT load workers; add them
only when a test environment sized for that benchmark exists.
