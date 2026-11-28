#!/bin/sh
# Capturas del explorador con el selector «Classes | Properties» (serie P, v1.1.1). No regenera las series A, B y C ni
# borra sus capturas. Uso, desde la raíz del repositorio:
#   PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/capturas-interfaz/correr-explorador.sh
# Proyecto de Compose «protys-v110»; las imágenes se reconstruyen desde el árbol de trabajo y al terminar se baja.
set -eu
D=research/integration/capturas-interfaz
mkdir -p "$D/capturas" "$D/salidas"
rm -f "$D"/capturas/A2b-*.png "$D"/capturas/A3b-*.png "$D"/capturas/A9*.png "$D"/salidas/*-P.txt "$D"/salidas/serie-P.json
export OUTDIR="$(pwd)/$D/capturas"
{
  echo "fecha_utc=$(date -u +%FT%TZ)"; echo "commit=$(git rev-parse HEAD)"
  echo "cambios_sin_commit=$(git status --porcelain --untracked-files=no | wc -l | tr -d ' ')"
  echo "node=$(node --version)"
  echo "playwright=$(node -e "console.log(require('$PLAYWRIGHT_MODULE/package.json').version)")"
} > "$D/salidas/00-versiones-P.txt"
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
export PROTYS_WEB=http://127.0.0.1:38081
C="docker compose -f docker/docker-compose.yml"
$C down -v > /dev/null 2>&1 || true
$C up -d --build > "$D/salidas/07-compose-up-P.txt" 2>&1
for c in protys-backend protys-frontend; do docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1" >> "$D/salidas/00-versiones-P.txt"; done
(cd "$D/scripts" && node capturas.js P > ../salidas/08-serie-P.txt)
$C down -v > /dev/null 2>&1
cat "$D/salidas/08-serie-P.txt"
