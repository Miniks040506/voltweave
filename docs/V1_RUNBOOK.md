# VoltWeave V1 runbook

## Start and acceptance check

Use a private `.env` copied from `.env.example`, then run:

```powershell
.\infrastructure\compose\release.ps1
```

Successful acceptance prints five PASS lines: Web HTTP, Gateway health, anonymous
rejection, customer token and authenticated Gateway route. `docker compose --wait`
also requires every long-running container to be healthy.

Use `-NoBuild` for a restart when source and images have not changed. Use
`-ProjectName` only for an isolated validation environment.

## Inspect the platform

```powershell
$compose = @(
  "--env-file", "infrastructure/compose/.env",
  "-f", "infrastructure/compose/compose.yml",
  "--profile", "app"
)
docker compose @compose ps
docker compose @compose logs --tail 200 api-gateway
docker compose @compose logs --since 10m dispatch-service
```

Every HTTP response carries `X-Correlation-Id`. Search that UUID in ECS JSON logs
across Gateway and the owning service before searching by timestamp.

## Expected health model

Startup order is enforced by health conditions:

```text
PostgreSQL / Kafka / MQTT / Keycloak
               ↓
          initializers
               ↓
            Portfolio
          ↙             ↘
 Telemetry/Intelligence  Dispatch → Settlement
          \             /
             Gateway
                ↓
               Web
```

One-shot `kafka-init` and `mosquitto-init` should exit with code 0. They are not
failed services. Backend containers use a Hikari pool of 1–5 connections to avoid
startup spikes against the local PostgreSQL limit.

## Common failures

### Port already allocated

Stop the process using the port or change the corresponding `*_HOST_PORT` in
`.env`. Keycloak/Web browser redirects assume the configured localhost ports, so
restart/rebuild Web after changing them.

### Missing Maven sibling artifact

Run Maven from the repository root with `-am`, or use the release script. Do not
run an individual service goal before its sibling libraries are available.

### Keycloak `invalid_client` after pulling realm changes

Keycloak imports a realm only into an empty database. Existing local volumes keep
the previous realm. Back up any required state, then perform the deliberate reset
described below. Never reset a shared environment as a troubleshooting shortcut.

### Service cannot resolve JWT keys

Containers validate the public issuer `http://localhost:8180/realms/voltweave`
but load keys through the internal Docker address `keycloak:8080`. Check Keycloak
health and the `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` environment
in the affected container.

### PostgreSQL connection slots exhausted

Check actual usage before changing limits:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml exec -T postgres `
  psql -U voltweave_admin -d postgres -c `
  "SELECT usename,state,count(*) FROM pg_stat_activity GROUP BY 1,2;"
```

V1 caps each service pool at five. Investigate leaked/duplicate processes before
raising `POSTGRES_MAX_CONNECTIONS`.

### Kafka consumer appears stalled

Check broker health, expected topics and consumer logs. Business rejection must
remain in normal domain flow; DLQ is only for poison events after bounded retry.
Replay safety comes from inbox `eventId` uniqueness, not from deleting offsets.

### Device does not publish telemetry

Confirm the simulator uses the newest provision response, the MQTT client ID is
unique and `scenario.local.json` was not reused after device revocation or broker
reset. The file contains a secret and is intentionally ignored by Git.

## Data lifecycle

Named volumes persist PostgreSQL, Kafka, MQTT, Prometheus and Grafana data. A normal
`down` preserves them. Before upgrades, back up at least PostgreSQL:

```powershell
docker compose --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml exec -T postgres `
  pg_dumpall -U voltweave_admin > voltweave-backup.sql
```

Restore procedures must be rehearsed on an isolated Compose project before use on
important data. Kafka/MQTT volumes are local sandbox durability, not a production
backup strategy.

## Deliberate destructive reset

The following command permanently removes the project's named volumes. Verify the
project name and backup first:

```powershell
docker compose --project-name voltweave `
  --env-file infrastructure/compose/.env `
  -f infrastructure/compose/compose.yml `
  --profile app --profile observability down --volumes --remove-orphans
```

Run `release.ps1` afterward to recreate databases, realm, topics and MQTT roles.

## V1 production boundary

Compose is the supported V1 sandbox/reviewer release. Before Internet deployment,
replace local credentials, use TLS, remove direct debug port exposure, use managed
secret storage/backups and complete infrastructure-specific capacity testing.
Kubernetes, Loki/Tempo, multi-region and real energy-market integrations are
deferred until there is a deployment environment that requires them.
