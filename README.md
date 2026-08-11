# VoltWeave

VoltWeave is a virtual power plant sandbox. It will simulate household energy
assets, forecast flexibility, dispatch devices and settle rewards without
requiring physical power hardware.

The repository currently contains the first runnable vertical foundations:

- `services/portfolio-service`: Spring Boot service for future tenant, site,
  device and virtual power plant ownership.
- `apps/web`: Next.js web application.
- `docs/VOLTWEAVE_V1_PLAN.md`: V1 architecture and delivery plan.

Only `portfolio-service` exists today. Other services will be introduced when
their first real behavior is implemented, rather than as empty scaffolding.

## Requirements

- Java 21
- Node.js 24
- npm 11+
- Docker Desktop or Docker Engine with Compose

Maven does not need to be installed globally; use the included wrapper.

## Run the platform sandbox

PostgreSQL/TimescaleDB and Keycloak run locally in Docker:

```powershell
Copy-Item infrastructure/compose/.env.example infrastructure/compose/.env
infrastructure/compose/verify.ps1
```

The verification script starts both containers, waits for their health checks,
then verifies database ownership, restricted roles, TimescaleDB isolation,
OIDC discovery, seeded roles/users and both OAuth clients. Local `.env` is
ignored by Git; replace its example passwords before sharing the machine.

Useful endpoints and ports:

| Component | Address | Purpose |
|---|---|---|
| Web | `http://localhost:3000` | Browser application |
| API Gateway | `http://localhost:8080` | Reserved for the future public entry point |
| Portfolio | `http://localhost:8081` | Direct service port during development |
| Keycloak | `http://localhost:8180` | Login and identity administration |
| PostgreSQL | `127.0.0.1:6543` | Local database access |

Portfolio uses `8081` because `8080` is intentionally reserved for the API
Gateway. Once the gateway exists, the frontend will call `8080`; direct service
ports remain useful for local debugging and are not exposed publicly.

Stop the sandbox without deleting its data:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml down
```

Keycloak imports a realm only when it does not already exist. To deliberately
rebuild all local databases and re-import the realm, add `--volumes` to the
`down` command; that permanently deletes the sandbox data.

## Run the backend

On Windows PowerShell:

```powershell
.\mvnw.cmd -pl services/portfolio-service spring-boot:run
```

The service starts on port `8081`. Check it at
`http://localhost:8081/actuator/health`.

## Run the frontend

```powershell
cd apps/web
npm ci
npm run dev
```

Open `http://localhost:3000`.

## Verify the repository

Backend:

```powershell
.\mvnw.cmd --batch-mode verify
```

Frontend:

```powershell
cd apps/web
npm ci
npm run lint
npm run build
npm audit
```

The same backend and frontend checks run in GitHub Actions for every pull
request and every push to `main`.

## Specifications

- [V1 delivery plan](docs/VOLTWEAVE_V1_PLAN.md)
- [Original SRS](docs/GridMind_SRS.md)
- [Full target-system SRS](docs/GridMind_FULL_Production_SRS.md)
