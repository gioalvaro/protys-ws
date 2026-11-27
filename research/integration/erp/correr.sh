#!/bin/sh
# Conectores ERP en sólo lectura con ADempiere y Odoo: prueba de punta a punta (run.sh), relevamiento de esquemas por
# SQL y verificación del vocabulario de los mapeos y de las consultas.
# Uso, desde la raíz del repositorio:  sh research/integration/erp/correr.sh
# KEEP_UP=1 deja levantado el proyecto «protys-v110-erp» al terminar; por omisión lo baja con sus volúmenes.
set -eu
D=research/integration/erp
S="$D/salidas"; rm -rf "$S"; mkdir -p "$S"
export COMPOSE_PROJECT_NAME=protys-v110-erp
KEEP_UP=1 bash "$D/run.sh" > "$S/run.txt" 2>&1 || true
bash "$D/scripts/relevar-esquemas.sh" > "$S/esquemas-sql.txt" 2>&1
python3 "$D/scripts/verificar-vocabulario.py" > "$S/verificar-vocabulario.txt" 2>&1
python3 "$D/scripts/verificar-vocabulario-consultas.py" > "$S/verificar-vocabulario-consultas.txt" 2>&1
if [ "${KEEP_UP:-0}" != 1 ]; then
  docker compose -f docker/docker-compose.yml -f docker/integration/docker-compose.erp.yml down -v > /dev/null 2>&1
fi
tail -1 "$S/e2e/RESUMEN.txt"
