#!/usr/bin/env bash
# Huella del esquema de un ERP, para comparar antes y después de la prueba.
# Imprime: cantidad de tablas, cantidad de columnas y SHA-256 del listado ordenado de
# information_schema.columns (esquema, tabla, columna, tipo, nulabilidad, valor por defecto)
# más los permisos de tabla del rol protys_ro. Usa el superusuario de la base para ver todo.
# Uso: huella-esquema.sh adempiere|odoo
set -euo pipefail

PROJECT="${COMPOSE_PROJECT_NAME:-protys}"
case "${1:-}" in
  adempiere) C="${PROJECT}-adempiere-db-1"; U="${ERP_ADMIN_USER:-postgres}"; D=adempiere; S=adempiere ;;
  odoo)      C="${PROJECT}-odoo-db-1";      U="${ODOO_DB_USER:-odoo}";      D=odoo;      S=public ;;
  *) echo "uso: $0 adempiere|odoo" >&2; exit 2 ;;
esac

psqlq() { docker exec "$C" psql -U "$U" -d "$D" -tA -c "$1"; }

tablas=$(psqlq "SELECT count(*) FROM information_schema.tables WHERE table_schema='$S'")
columnas=$(psqlq "SELECT count(*) FROM information_schema.columns WHERE table_schema='$S'")
hash_cols=$(psqlq "SELECT table_schema||'|'||table_name||'|'||column_name||'|'||data_type||'|'||is_nullable||'|'||coalesce(column_default,'')
                   FROM information_schema.columns WHERE table_schema='$S'
                   ORDER BY table_name, column_name" | shasum -a 256 | cut -d' ' -f1)
hash_acl=$(psqlq "SELECT table_name||'|'||privilege_type FROM information_schema.role_table_grants
                  WHERE grantee='${PROTYS_RO_USER:-protys_ro}' ORDER BY 1" | shasum -a 256 | cut -d' ' -f1)
echo "erp=$1 esquema=$S tablas=$tablas columnas=$columnas sha256_columnas=$hash_cols sha256_permisos_ro=$hash_acl"
