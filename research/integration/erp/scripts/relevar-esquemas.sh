#!/usr/bin/env bash
# Relevamiento por SQL directo de las tablas de manufactura de cada ERP.
# Para cada tabla: cantidad de filas y columnas (nombre, tipo) según information_schema.
# Sólo lee. Usa el superusuario de cada base, como huella-esquema.sh: desde la revisión del 2026-11-18
# el rol protys_ro sólo puede leer las tablas de los mapeos, y este relevamiento mira algunas más
# (s_resourcetype, pp_order_bomline). El relevamiento de esquemas.md se hizo antes con protys_ro.
# Uso: relevar-esquemas.sh > salida.txt   (con la composición ERP levantada)
set -euo pipefail

PROJECT="${COMPOSE_PROJECT_NAME:-protys-e2e}"

ADEMPIERE_TABLAS="m_product pp_product_planning pp_product_bom pp_product_bomline ad_workflow ad_wf_node ad_wf_nodenext s_resource s_resourcetype pp_order pp_order_node pp_order_bomline"
ODOO_TABLAS="product_template product_product mrp_bom mrp_bom_line mrp_routing_workcenter mrp_workcenter resource_resource mrp_production mrp_workorder"

relevar() {
  local contenedor="$1" base="$2" esquema="$3" usuario="$4"; shift 4
  for tabla in "$@"; do
    local filas
    filas=$(docker exec "$contenedor" psql -U "$usuario" -d "$base" -tAc \
      "SELECT count(*) FROM ${esquema}.${tabla}")
    echo "### ${esquema}.${tabla}  filas=${filas}"
    docker exec "$contenedor" psql -U "$usuario" -d "$base" -tA -F ' ' -c \
      "SELECT column_name, data_type FROM information_schema.columns
       WHERE table_schema='${esquema}' AND table_name='${tabla}' ORDER BY ordinal_position"
    echo
  done
}

echo "## ADempiere (base adempiere, esquema adempiere) — $(date -u +%FT%TZ)"
relevar "${PROJECT}-adempiere-db-1" adempiere adempiere "${ERP_ADMIN_USER:-postgres}" $ADEMPIERE_TABLAS
echo "## Odoo (base odoo, esquema public)"
relevar "${PROJECT}-odoo-db-1" odoo public "${ODOO_DB_USER:-odoo}" $ODOO_TABLAS
