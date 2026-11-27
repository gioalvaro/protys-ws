#!/bin/sh
# Capturas de la interfaz: serie A (red integrada), serie B (asistente con el piloto ISO 10303) y serie C (conectores
# ERP). Corre también research/integration/erp/correr.sh, porque la serie C necesita los ERP de prueba.
# Uso, desde la raíz del repositorio:
#   PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/capturas-interfaz/correr.sh
# Proyectos de Compose: «protys-v110» (series A y B) y «protys-v110-erp» (serie C). Los dos se bajan al terminar.
set -eu
D=research/integration/capturas-interfaz
rm -rf "$D/capturas" "$D/salidas"; mkdir -p "$D/capturas" "$D/salidas"
export OUTDIR="$(pwd)/$D/capturas"
{
  echo "fecha_utc=$(date -u +%FT%TZ)"; echo "node=$(node --version)"
  echo "playwright=$(node -e "console.log(require('$PLAYWRIGHT_MODULE/package.json').version)")"
} > "$D/salidas/00-versiones.txt"

# Series A y B: plataforma recién levantada, con la red integrada.
(
  export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
  export PROTYS_WEB=http://127.0.0.1:38081
  C="docker compose -f docker/docker-compose.yml"
  $C down -v > /dev/null 2>&1 || true
  $C up -d --build > "$D/salidas/01-compose-up.txt" 2>&1
  for c in protys-backend protys-frontend; do docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1" >> "$D/salidas/00-versiones.txt"; done
  (cd "$D/scripts" && node capturas.js A > ../salidas/02-serie-A.txt && node capturas.js B > ../salidas/03-serie-B.txt)
  $C down -v > /dev/null 2>&1
)

# Serie C: plataforma con los dos ERP de prueba y los conectores materializados.
KEEP_UP=1 sh research/integration/erp/correr.sh > "$D/salidas/04-erp.txt" 2>&1
(
  export COMPOSE_PROJECT_NAME=protys-v110-erp PROTYS_BACKEND_PORT=48080 PROTYS_FRONTEND_PORT=48081 PROTYS_FUSEKI_PORT=43030 PROTYS_POSTGRES_PORT=45432
  export ERP_ODOO_DB_PORT=45434 ERP_ADEMPIERE_DB_PORT=45433 PROTYS_WEB=http://127.0.0.1:48081
  C="docker compose -f docker/docker-compose.yml -f docker/integration/docker-compose.erp.yml"
  $C up -d --build --no-deps protys-frontend > "$D/salidas/05-compose-frontend.txt" 2>&1
  (cd "$D/scripts" && node capturas.js C > ../salidas/06-serie-C.txt)
  $C down -v > /dev/null 2>&1
)
