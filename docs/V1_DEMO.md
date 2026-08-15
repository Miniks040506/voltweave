# VoltWeave V1 demo

This walkthrough presents the complete V1 story in about ten minutes. It uses
simulated devices and deterministic optimization, so no physical energy hardware
or external market account is required.

## Prepare the environment

From the repository root:

```powershell
Copy-Item infrastructure/compose/.env.example infrastructure/compose/.env
.\infrastructure\compose\release.ps1
.\infrastructure\compose\demo.ps1
.\mvnw.cmd -pl simulator/simulation-service -am -DskipTests package
java -jar simulator/simulation-service/target/simulation-service-0.1.0-SNAPSHOT-exec.jar `
  simulator/simulation-service/scenario.local.json
```

Use the local passwords from `infrastructure/compose/.env`; do not display that
file during a recorded demo. Keep the simulator running in its own terminal.

## Story and expected evidence

### 1. Customer: connect an energy asset

1. Open `http://localhost:3000` and sign in as `customer`.
2. Open the portfolio and show the seeded site and battery.
3. Open live telemetry and wait for values to change.
4. Show the site's automation preference and reward summary.

Explain that `demo.ps1` provisioned the device once. The simulator authenticates
with its one-time MQTT credential and publishes readings; Telemetry validates and
deduplicates them before updating the durable device twin.

Expected evidence:

- the battery is online;
- sequence numbers and timestamps advance;
- state of charge and power values are visible;
- the customer has opted into VPP participation.

### 2. Operator: turn flexibility into a dispatch

1. Sign out and sign in as `operator`.
2. Open the VPP overview and select the seeded VPP.
3. Generate or inspect a forecast baseline and flexibility snapshot.
4. Preview the deterministic optimization result.
5. Confirm a manual dispatch and inspect its device allocation.
6. Keep the simulator running while command and performance status update.
7. Complete the dispatch when its delivery window is satisfied.

Explain the safety boundary: optimization proposes work, but a person confirms the
manual dispatch. The immutable baseline version follows the dispatch into
settlement. Kafka carries lifecycle events, while each consumer's inbox prevents
duplicate side effects.

Expected evidence:

- available capacity is derived from the battery state and site preference;
- the optimization result is repeatable for the same inputs;
- the dispatch has an allocation, command attempts and lifecycle status;
- measured performance is linked to the dispatch by logical IDs, not cross-DB FKs.

### 3. Operator: settle delivered energy

1. Open the completed dispatch's settlement.
2. Show baseline, actual delivery and calculated line items.
3. Export the settlement CSV.
4. Show the customer's append-only reward ledger entry.

Explain that settlement copies the required baseline inputs rather than querying
another service's database. Reward entries use source-event idempotency so an
event replay cannot pay twice.

Expected evidence:

- one settlement exists for the dispatch;
- money uses decimal amounts and an explicit currency;
- the CSV agrees with the displayed line items;
- repeated processing does not create a duplicate base reward.

### 4. Administrator: prove isolation and operability

1. Sign in as `admin` and show organization/audit views.
2. Open `http://localhost:3001` if the observability profile is running.
3. Show the `VoltWeave V1 Acceptance` Grafana dashboard.

Explain that every user-facing resource check resolves ownership through
Portfolio. Services validate JWTs independently, internal calls use service
credentials and correlation IDs join logs across a request.

Expected evidence:

- customer and operator data remain tenant-scoped;
- audit records capture sensitive lifecycle changes;
- service health and the golden-path metrics are visible.

## Useful presenter commands

```powershell
$compose = @(
  "--env-file", "infrastructure/compose/.env",
  "-f", "infrastructure/compose/compose.yml",
  "--profile", "app"
)
docker compose @compose ps
docker compose @compose logs --since 5m telemetry-service
docker compose @compose logs --since 5m dispatch-service
```

If a screen is slow to update, first verify that the simulator still runs and all
containers are healthy. Do not reset volumes during a presentation.

## Honest V1 boundaries

- Devices are deterministic software simulations, not certified hardware.
- Forecasting and optimization use explainable heuristics, not market-grade ML or
  LP/MILP solvers.
- Rewards are an internal ledger and CSV export, not real payments.
- Docker Compose is the supported V1 runtime; Kubernetes and multi-region
  deployment are deferred.

These boundaries keep the project reproducible while still demonstrating the
microservice concerns that matter: ownership, authorization, asynchronous events,
idempotency, recovery, observability and an end-to-end product journey.
