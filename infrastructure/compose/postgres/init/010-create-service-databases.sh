#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  PORTFOLIO_DB_PASSWORD
  TELEMETRY_DB_PASSWORD
  INTELLIGENCE_DB_PASSWORD
  DISPATCH_DB_PASSWORD
  SETTLEMENT_DB_PASSWORD
  KEYCLOAK_DB_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing required environment variable: ${variable_name}" >&2
    exit 1
  fi
done

create_service_database() {
  local database_name="$1"
  local role_name="$2"
  local password="$3"

  psql --username "$POSTGRES_USER" --dbname postgres \
    --set=database_name="$database_name" \
    --set=role_name="$role_name" \
    --set=role_password="$password" <<'SQL'
SELECT format(
  'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
  :'role_name',
  :'role_password'
)
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role_name') \gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I TEMPLATE template0',
  :'database_name',
  :'role_name'
)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database_name') \gexec

SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'database_name') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'role_name') \gexec
SQL
}

# The Timescale image enables the extension in system templates by default.
# Service databases use template0, and only telemetry_db receives the extension.
psql --username "$POSTGRES_USER" --dbname postgres \
  --command 'DROP EXTENSION IF EXISTS timescaledb CASCADE'
psql --username "$POSTGRES_USER" --dbname template1 \
  --command 'DROP EXTENSION IF EXISTS timescaledb CASCADE'

create_service_database portfolio_db portfolio_app "$PORTFOLIO_DB_PASSWORD"
create_service_database telemetry_db telemetry_app "$TELEMETRY_DB_PASSWORD"
create_service_database intelligence_db intelligence_app "$INTELLIGENCE_DB_PASSWORD"
create_service_database dispatch_db dispatch_app "$DISPATCH_DB_PASSWORD"
create_service_database settlement_db settlement_app "$SETTLEMENT_DB_PASSWORD"
create_service_database keycloak_db keycloak_app "$KEYCLOAK_DB_PASSWORD"

psql --username "$POSTGRES_USER" --dbname telemetry_db \
  --command 'CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE'
