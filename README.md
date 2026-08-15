# VoltWeave

VoltWeave is a runnable virtual power plant sandbox. It models customer energy
assets, ingests simulated telemetry, forecasts flexibility, schedules dispatches,
tracks delivery and settles rewards without physical power hardware.

## V1 architecture

| Component | Responsibility |
|---|---|
| Web | Customer, operator and administrator journeys |
| API Gateway | Public routing, JWT validation and correlation IDs |
| Portfolio | Organizations, sites, devices, VPPs and access ownership |
| Telemetry | MQTT ingestion, validation, TimescaleDB history and durable twins |
| Intelligence | Forecast baselines, flexibility and deterministic optimization |
| Dispatch | Manual/automatic dispatch, commands, ACKs and recovery |
| Settlement | Immutable settlement inputs, rewards, ledger and CSV reports |
| Simulator | Deterministic meter, solar, battery and EV device behavior |

Services communicate synchronously through authenticated HTTP where an immediate
answer is required and asynchronously through Kafka for lifecycle events. Each
service owns its PostgreSQL database and Flyway migrations.

## Requirements

- Docker Desktop or Docker Engine with Compose
- PowerShell 7+
- Java 21 only when running tests or the simulator outside Docker
- Node.js 24 only when developing the web application outside Docker

## Start the complete platform

From a clean checkout:

```powershell
Copy-Item infrastructure/compose/.env.example infrastructure/compose/.env
.\infrastructure\compose\release.ps1
```

Change every `local-*-change-me` value in `.env` before sharing the environment.
The script builds seven application images, starts the dependency graph, waits for
health checks, verifies authentication boundaries and calls an authorized route
through Gateway.

Open `http://localhost:3000`. Demo users are `customer`, `operator` and `admin`;
their local passwords are defined in `.env`.

## Create runnable demo data

```powershell
.\infrastructure\compose\demo.ps1
.\mvnw.cmd -pl simulator/simulation-service -am -DskipTests package
java -jar simulator/simulation-service/target/simulation-service-0.1.0-SNAPSHOT-exec.jar `
  simulator/simulation-service/scenario.local.json
```

The seed script creates new customer/operator organizations, memberships, an
opted-in battery site, a provisioned battery and a VPP membership. It writes the
one-time MQTT credential to ignored `scenario.local.json`; do not commit or share
that file. Keep the simulator process running while demonstrating live telemetry
and dispatch commands.

## Local endpoints

| Component | URL |
|---|---|
| Web | `http://localhost:3000` |
| API Gateway | `http://localhost:8080` |
| Portfolio debug/metrics | `http://localhost:8081` |
| Telemetry debug/metrics | `http://localhost:8082` |
| Intelligence debug/metrics | `http://localhost:8083` |
| Dispatch debug/metrics | `http://localhost:8084` |
| Settlement debug/metrics | `http://localhost:8085` |
| Keycloak | `http://localhost:8180` |
| PostgreSQL | `127.0.0.1:6543` |
| Kafka | `127.0.0.1:9092` |
| MQTT | `127.0.0.1:1883` |

Direct service ports bind to `127.0.0.1` for local debugging. Browser/API clients
should use Gateway on port 8080.

## Observability

Start the optional Prometheus and Grafana profile alongside the application:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml `
  --profile app --profile observability up -d --wait
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- Dashboard: `VoltWeave V1 Acceptance`

## Stop and restart

Stop containers while keeping database, Kafka and MQTT data:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml --profile app down
```

Run `release.ps1 -NoBuild` to restart existing images. Do not add `--volumes`
unless you intentionally want to permanently delete the sandbox state.

## Verification

```powershell
.\mvnw.cmd --batch-mode verify

cd apps/web
npm ci
npm run lint
npm run build
npm audit --omit=dev
```

Full cross-service E2E and the k6 latency baseline are opt-in:

```powershell
.\mvnw.cmd "-Pe2e" verify
.\mvnw.cmd "-Pe2e,performance" verify
```

## Documentation

- [V1 runbook](docs/V1_RUNBOOK.md)
- [V1 demo](docs/V1_DEMO.md)
- [V1 delivery plan](docs/VOLTWEAVE_V1_PLAN.md)
- [Original SRS](docs/GridMind_SRS.md)
- [Full target-system SRS](docs/GridMind_FULL_Production_SRS.md)

Kubernetes, multi-region deployment, real market/payment integrations and physical
device certification are intentionally outside V1.
