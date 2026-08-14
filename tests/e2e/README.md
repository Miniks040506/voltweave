# VoltWeave end-to-end tests

This module verifies the V1 platform through the public API Gateway while using
real local infrastructure and service processes. It is opt-in so a normal
`mvn test` remains fast.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop with Linux containers
- Available local CPU and memory for PostgreSQL, Keycloak, Kafka, Mosquitto and
  six Spring Boot processes

No separately installed PostgreSQL, Kafka, Keycloak or Mosquitto is required.

## Run from a clean checkout

From the repository root:

```powershell
mvn -Pe2e verify
```

The root reactor command is the recommended command. It builds every executable
service JAR and the simulator dependency before Failsafe starts the E2E suite.

For a quicker rerun after those artifacts already exist:

```powershell
mvn -pl tests/e2e -Pe2e verify
```

Running only the module on a clean machine can fail if the service JARs or the
`simulation-service` artifact have not been built yet. In that case, use the
root reactor command above.

## Runtime lifecycle

`PlatformEnvironment` performs the following work for each test run:

1. Reserves random host ports.
2. Creates a unique Docker Compose project and named volumes.
3. Starts PostgreSQL/TimescaleDB, Keycloak, Kafka and Mosquitto.
4. Initializes Kafka topics and Mosquitto users/ACLs.
5. Starts Portfolio, Telemetry, Intelligence, Dispatch, Settlement and Gateway
   as child JVM processes.
6. Waits for each `/actuator/health` endpoint.
7. Runs the ordered platform scenarios.
8. Stops child processes and removes the Compose project and volumes.

The suite never depends on the developer's normal Compose project or fixed
host ports. A failed run preserves service logs under:

```text
tests/e2e/target/runtime-<pid>/logs/
```

## Covered journeys

The suite currently proves:

- Keycloak password grant for seeded admin, customer and operator users.
- Organization membership, site preferences and device provisioning.
- A provisioned MQTT device publishing telemetry through Kafka into the durable
  authorized device twin.
- VPP membership, forecast baseline, flexibility snapshot, optimization preview
  and idempotent manual dispatch creation.
- Provisioning and dispatch replay returning the original resource.
- Reusing an idempotency key with a different payload returning `409 Conflict`.
- Cross-tenant site isolation, role boundaries and forged JWT rejection.
- Scheduled dispatch and prepared command state surviving Dispatch restarts.
- Re-preparing commands after restart returning the same command IDs.

The dispatch is deliberately scheduled in the future. Preparing it changes the
workflow to `PREPARING`, but Telemetry does not publish the MQTT command before
its `validFrom`. Command ACK, timeout and rebalance transitions are covered by
their focused service integration tests.

## Troubleshooting

### Missing local Maven artifact

If Maven reports a missing `io.voltweave:*:0.1.0-SNAPSHOT` artifact, run:

```powershell
mvn -Pe2e verify
```

Do not start with `-pl tests/e2e` on a clean checkout because `-pl` alone does
not build sibling reactor modules.

### A service exits during startup

Open its log in the newest runtime directory:

```powershell
$runtime = Get-ChildItem tests/e2e/target -Directory -Filter 'runtime-*' |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
Get-Content "$($runtime.FullName)\logs\dispatch.log" -Tail 100
```

Replace `dispatch.log` with `portfolio.log`, `telemetry.log`,
`intelligence.log`, `settlement.log` or `gateway.log` as needed.

### Docker resources remain after an interrupted JVM

Normally teardown removes all resources. If the test JVM itself is killed,
list projects first and remove only the exact E2E project shown:

```powershell
docker compose ls
docker compose --project-name voltweave-e2e-<pid> `
  --env-file tests/e2e/target/runtime-<pid>/compose.env `
  -f infrastructure/compose/compose.yml down --volumes --remove-orphans
```

Never use a broad volume-prune command for this cleanup.

### Port or startup timeout

Retry once after confirming Docker Desktop is healthy. Random ports avoid the
usual `8080 already in use` issue, while startup failures are reported with the
last service log lines. If failures repeat, inspect the preserved logs instead
of increasing timeouts first.
