# VoltWeave

VoltWeave is a virtual power plant sandbox. It will simulate household energy
assets, forecast flexibility, dispatch devices and settle rewards without
requiring physical power hardware.

The repository currently contains the first runnable vertical foundations:

- `services/portfolio-service`: Spring Boot service for future tenant, site,
  device and virtual power plant ownership.
- `apps/web`: Next.js web application.
- `VOLTWEAVE_V1_PLAN.md`: V1 architecture and delivery plan.

Only `portfolio-service` exists today. Other services will be introduced when
their first real behavior is implemented, rather than as empty scaffolding.

## Requirements

- Java 21
- Node.js 24
- npm 11+

Maven does not need to be installed globally; use the included wrapper.

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

- [V1 delivery plan](VOLTWEAVE_V1_PLAN.md)
- [Original SRS](GridMind_SRS.md)
- [Full target-system SRS](GridMind_FULL_Production_SRS.md)
