# VoltWeave testing guide

This guide covers local release acceptance, automated tests, the complete product
journey and failure diagnostics. Run commands from the repository root unless a
different directory is shown.

## 1. Prerequisites

Verify the required tools:

```powershell
docker version
docker compose version
java -version
.\mvnw.cmd -version
node --version
npm --version
```

Expected local tool versions:

- Java 21
- Maven Wrapper included in the repository
- Node.js 24
- Docker Compose v2

Docker must have enough memory to run PostgreSQL, Kafka, Keycloak, Mosquitto, six
Spring Boot applications and the web application. Eight GB available to Docker is
a practical minimum for the complete stack.

## 2. Clean release acceptance

Create a private environment file:

```powershell
Copy-Item infrastructure/compose/.env.example infrastructure/compose/.env
```

Replace every value ending in `change-me`, then start the platform:

```powershell
.\infrastructure\compose\release.ps1
```

The script must report five passing assertions:

```text
PASS web HTTP status
PASS gateway health
PASS anonymous API rejection
PASS customer token
PASS authenticated Gateway route
```

Inspect container state independently:

```powershell
$compose = @(
  "--env-file", "infrastructure/compose/.env",
  "-f", "infrastructure/compose/compose.yml",
  "--profile", "app"
)
docker compose @compose ps
```

All long-running containers should be `healthy`. `kafka-init` and
`mosquitto-init` are one-shot containers and should exit with code 0.

## 3. Backend verification

Run the complete default Maven reactor:

```powershell
.\mvnw.cmd --batch-mode verify
```

This builds, tests and packages:

- event contracts;
- API Gateway;
- Portfolio;
- Telemetry;
- Intelligence;
- Dispatch;
- Settlement;
- simulator;
- the E2E test module without activating its opt-in profile.

The final reactor summary must show `SUCCESS` for every module.

### Run one service

Use `-pl` to select the module and `-am` to build its required sibling modules:

```powershell
.\mvnw.cmd -pl services/portfolio-service -am test
.\mvnw.cmd -pl services/telemetry-service -am test
.\mvnw.cmd -pl services/intelligence-service -am test
.\mvnw.cmd -pl services/dispatch-service -am test
.\mvnw.cmd -pl services/settlement-service -am test
```

`-am` is important. Running a service goal alone can fail when an internal artifact
such as `event-contracts` has not been installed in the local Maven repository.

### Start one service for debugging

Start its dependencies first, then run from the root with `-am`:

```powershell
.\mvnw.cmd -pl services/portfolio-service -am spring-boot:run
```

For normal product testing, prefer Compose because it supplies the complete and
consistent environment.

## 4. Frontend verification

```powershell
Push-Location apps/web
npm ci
npm audit --omit=dev
npm run lint
npm run build
Pop-Location
```

Expected results:

- dependency installation follows `package-lock.json`;
- the production dependency audit reports no known vulnerability;
- ESLint exits successfully;
- TypeScript checking and the Next.js production build complete successfully.

For interactive frontend development while backend Compose services run:

```powershell
Push-Location apps/web
npm run dev
Pop-Location
```

## 5. Cross-service E2E tests

```powershell
.\mvnw.cmd "-Pe2e" verify
```

The E2E profile creates an isolated environment with random ports and its own
Compose project. Requests use real Keycloak tokens and enter through Gateway.

The suite verifies:

1. organization, membership, site and device creation;
2. one-time MQTT device provisioning and idempotent replay;
3. MQTT telemetry through Kafka into TimescaleDB and the durable twin;
4. forecast, flexibility and optimization creation;
5. manual dispatch preparation and persisted commands;
6. authorization failures across roles and tenants;
7. forged-token rejection;
8. replay protection for provisioning and dispatch;
9. restart-safe dispatch state;
10. settlement and duplicate-reward protection.

The isolated test project is removed after the run. It does not use the normal
`voltweave` Compose volumes.

## 6. Performance baseline

Run the E2E workflow and k6 baseline together:

```powershell
.\mvnw.cmd "-Pe2e,performance" verify
```

The default local profile uses a deliberately small load so it remains reproducible
on development hardware. It obtains a Keycloak token and calls authorized read
routes through Gateway. Threshold failures cause Maven verification to fail.

Treat local latency as a regression baseline, not a production capacity claim.
Increase load only after recording hardware, Docker resources and test parameters.

## 7. Manual product test

### Seed the scenario

With the Compose application running:

```powershell
.\infrastructure\compose\demo.ps1
.\mvnw.cmd -pl simulator/simulation-service -am -DskipTests package
java -jar simulator/simulation-service/target/simulation-service-0.1.0-SNAPSHOT-exec.jar `
  simulator/simulation-service/scenario.local.json
```

Keep the simulator terminal open.

### Customer checks

1. Open `http://localhost:3000` and sign in as `customer`.
2. Confirm the seeded site and battery are visible.
3. Confirm the battery becomes online.
4. Observe sequence number and telemetry timestamp advancing.
5. Confirm VPP opt-in and minimum reserve preferences.
6. Inspect the reward summary after a completed settlement.

### Operator checks

1. Sign in as `operator`.
2. Select the seeded VPP and inspect available resources.
3. Create a forecast baseline.
4. Calculate a flexibility snapshot.
5. Preview an optimization and inspect its allocations.
6. Confirm a manual dispatch.
7. Observe command attempts, acknowledgement and measured delivery.
8. Complete the dispatch and inspect its settlement.
9. Export the settlement CSV.

### Administrator checks

1. Sign in as `admin`.
2. Inspect organizations and membership relationships.
3. Inspect audit records for privileged lifecycle changes.
4. Confirm customer and operator resources remain tenant-scoped.

The detailed presentation order and expected evidence are documented in
[V1_DEMO.md](V1_DEMO.md).

## 8. Observability checks

Start the optional profile:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml `
  --profile app --profile observability up -d --wait
```

Verify:

- Prometheus is reachable at `http://localhost:9090`;
- Grafana is reachable at `http://localhost:3001`;
- the `VoltWeave V1 Acceptance` dashboard loads;
- service health, HTTP throughput, error rate and latency contain data;
- `X-Correlation-Id` appears on Gateway responses and in backend JSON logs.

View recent logs:

```powershell
$compose = @(
  "--env-file", "infrastructure/compose/.env",
  "-f", "infrastructure/compose/compose.yml",
  "--profile", "app"
)
docker compose @compose logs --since 10m api-gateway
docker compose @compose logs --since 10m telemetry-service
docker compose @compose logs --since 10m dispatch-service
```

## 9. Replay and restart behavior

Replay, tenant isolation and restart recovery are automated in the E2E suite. For
manual observation:

1. prepare a future dispatch;
2. restart only `dispatch-service`;
3. wait for its health check;
4. reopen the dispatch;
5. confirm the same dispatch and command identifiers remain;
6. confirm the command is not delivered before `validFrom`.

Do not edit service databases to manufacture a state. Testing through public APIs
preserves authentication, validation, persistence and event boundaries.

## 10. Common failures

### Maven cannot find `event-contracts`

Run Maven from the repository root and include `-am`. The sibling module is part of
the reactor, not a dependency downloaded from Maven Central.

### Keycloak returns `invalid_client`

An old Keycloak volume may predate the current realm configuration. Keycloak only
imports the realm into an empty database. Back up required data and follow the
deliberate reset procedure in [V1_RUNBOOK.md](V1_RUNBOOK.md).

### A service remains unhealthy

```powershell
docker compose @compose ps
docker compose @compose logs --tail 200 <service-name>
```

Check the first application exception rather than later connection retries.

### Simulator publishes no telemetry

- Confirm the simulator process is still running.
- Use the newest generated `scenario.local.json`.
- Confirm port 1883 is not occupied by another broker.
- Regenerate the scenario after device revocation or broker reset.

### PostgreSQL rejects connections

The local profile caps every application pool at five connections. Check for old
duplicate containers or locally running service processes before raising limits.

## 11. Stop the environment

Preserve data:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml --profile app down
```

Do not add `--volumes` unless deletion is intentional and backups have been
verified. The destructive reset procedure is documented separately in the runbook.
