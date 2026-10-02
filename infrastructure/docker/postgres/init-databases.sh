#!/usr/bin/env bash
# ======================================================================
# FINOVA — creates one database per microservice.
#
# Bounded-context-per-database is what makes these services independently
# deployable: nothing shares a schema, so a service can be restarted,
# migrated or scaled without touching anyone else's data.
#
# Runs once, on first initialisation of the PostgreSQL data volume.
# ======================================================================
set -euo pipefail

create_database() {
  local database="$1"
  echo "[finova-init] creating database '${database}'"
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" <<-EOSQL
    SELECT 'CREATE DATABASE "${database}"'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${database}')\gexec
    GRANT ALL PRIVILEGES ON DATABASE "${database}" TO "${POSTGRES_USER}";
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  for database in ${POSTGRES_MULTIPLE_DATABASES//,/ }; do
    create_database "${database}"
  done
fi

echo "[finova-init] database provisioning complete."
psql --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" --tuples-only \
  --command "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;"