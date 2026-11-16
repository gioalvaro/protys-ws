#!/usr/bin/env bash
# Relevamiento por SQL directo de las tablas de manufactura de cada ERP.
# Para cada tabla: cantidad de filas y columnas (nombre, tipo) según information_schema.
# Se conecta con el rol de sólo lectura protys_ro, el mismo que usa PROTYS-WS.
# Uso: relevar-esquemas.sh > salida.txt   (con la composición ERP levantada)
set -euo pipefail

RO_USER="${PROTYS_RO_USER:-protys_ro}"
RO_PASS="${PROTYS_RO_PASSWORD:-protys_ro_demo}"
PROJECT="${COMPOSE_PROJECT_NAME:-protys}"

ADEMPIERE_TABLAS="m_product pp_product_planning pp_product_bom pp_product_bomline ad_workflow ad_wf_node ad_wf_nodenext s_resource s_resourcetype pp_order pp_order_node pp_order_bomline"
ODOO_TABLAS="product_template product_product mrp_bom mrp_bom_line mrp_routing_workcenter mrp_workcenter resource_resource mrp_production mrp_workorder"

relevar() {
  local contenedor="$1" base="$2" esquema="$3"; shift 3
  for tabla in "$@"; do
    local filas
    filas=$(docker exec -e PGPASSWORD="$RO_PASS" "$contenedor" psql -h localhost -U "$RO_USER" -d "$base" -tAc \
      "SELECT count(*) FROM ${esquema}.${tabla}")
    echo "### ${esquema}.${tabla}  filas=${filas}"
    docker exec -e PGPASSWORD="$RO_PASS" "$contenedor" psql -h localhost -U "$RO_USER" -d "$base" -tA -F ' ' -c \
      "SELECT column_name, data_type FROM information_schema.columns
       WHERE table_schema='${esquema}' AND table_name='${tabla}' ORDER BY ordinal_position"
    echo
  done
}

echo "## ADempiere (base adempiere, esquema adempiere) — $(date -u +%FT%TZ)"
relevar "${PROJECT}-adempiere-db-1" adempiere adempiere $ADEMPIERE_TABLAS
echo "## Odoo (base odoo, esquema public)"
relevar "${PROJECT}-odoo-db-1" odoo public $ODOO_TABLAS
