#!/bin/sh
# Instala el módulo de manufactura (mrp) con datos de demostración y crea el rol
# de sólo lectura de PROTYS-WS. Idempotente: si la base ya tiene mrp, no reinstala.
# Esto es provisión del entorno de prueba: ocurre antes de la huella "antes" de run.sh.
set -eu

export PGHOST="$ODOO_DB_HOST" PGUSER="$ODOO_DB_USER" PGPASSWORD="$ODOO_DB_PASSWORD"

if psql -d "$ODOO_DB_NAME" -tAc "SELECT 1 FROM ir_module_module WHERE name='mrp' AND state='installed'" 2>/dev/null | grep -q 1; then
  echo "mrp ya instalado en $ODOO_DB_NAME"
else
  # Odoo 17 carga datos de demostración salvo que se pida --without-demo.
  odoo --db_host="$ODOO_DB_HOST" --db_user="$ODOO_DB_USER" --db_password="$ODOO_DB_PASSWORD" \
       -d "$ODOO_DB_NAME" -i mrp --stop-after-init --no-http --load-language=en_US
fi

psql -v ON_ERROR_STOP=1 -d "$ODOO_DB_NAME" <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${PROTYS_RO_USER}') THEN
    CREATE ROLE ${PROTYS_RO_USER} LOGIN PASSWORD '${PROTYS_RO_PASSWORD}';
  END IF;
END \$\$;
ALTER ROLE ${PROTYS_RO_USER} SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE ${ODOO_DB_NAME} TO ${PROTYS_RO_USER};
GRANT USAGE ON SCHEMA public TO ${PROTYS_RO_USER};
-- Odoo deja CREATE sobre public para PUBLIC; sin esto protys_ro podría crear tablas
-- forzando BEGIN READ WRITE. Odoo no lo necesita: su rol es dueño de la base.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
-- SELECT sólo sobre las tablas que leen los mapeos (docker/integration/mappings/odoo.r2rml.ttl).
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM ${PROTYS_RO_USER};
GRANT SELECT ON mrp_bom, mrp_bom_line, mrp_production, mrp_routing_workcenter, mrp_workcenter,
  mrp_workorder, product_product, product_template, res_company, resource_resource TO ${PROTYS_RO_USER};
SQL
echo "Odoo listo: base $ODOO_DB_NAME con mrp y rol ${PROTYS_RO_USER}"
