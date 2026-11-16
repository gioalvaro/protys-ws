#!/bin/sh
# Restaura la semilla de ADempiere y crea el rol de sólo lectura que usa PROTYS-WS.
# Corre una única vez, cuando el volumen de datos está vacío (docker-entrypoint-initdb.d).
set -eu

# El volcado asigna dueño 'adempiere' a sus objetos: el rol tiene que existir antes.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE adempiere LOGIN PASSWORD '${ADEMPIERE_DB_PASSWORD}';
SQL

psql -v ON_ERROR_STOP=1 --quiet --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -f /seed/Adempiere_pg.dmp > /dev/null

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE ${PROTYS_RO_USER} LOGIN PASSWORD '${PROTYS_RO_PASSWORD}';
ALTER ROLE ${PROTYS_RO_USER} SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${PROTYS_RO_USER};
GRANT USAGE ON SCHEMA adempiere TO ${PROTYS_RO_USER};
GRANT SELECT ON ALL TABLES IN SCHEMA adempiere TO ${PROTYS_RO_USER};
SQL
