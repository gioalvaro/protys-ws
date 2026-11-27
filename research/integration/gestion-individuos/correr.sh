#!/bin/sh
# Gestión de individuos por la API y por la interfaz web, regresión con razonamiento y carga de Turtle mal
# formado. Uso, desde la raíz del repositorio:
#   PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/gestion-individuos/correr.sh
# Usa el proyecto de Compose «protys-v110» (puertos 38080, 38081, 33030 y 35432), lo recrea desde cero y al terminar
# lo baja con sus volúmenes. No toca otros proyectos.
set -eu
D=research/integration/gestion-individuos
S="$D/salidas"; C="$D/capturas"
rm -rf "$S" "$C"; mkdir -p "$S" "$C"
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
export PROTYS_API=http://127.0.0.1:38080/api PROTYS_FUSEKI=http://127.0.0.1:33030/protys_canonical PROTYS_WEB=http://127.0.0.1:38081
export OUTDIR="$(pwd)/$C"
COMPOSE="docker compose -f docker/docker-compose.yml"
$COMPOSE down -v > /dev/null 2>&1 || true
$COMPOSE up -d --build > "$S/01-compose-up.txt" 2>&1
{
  echo "fecha_utc=$(date -u +%FT%TZ)"
  echo "docker=$(docker version --format '{{.Server.Version}}')"; echo "compose=$(docker compose version --short)"
  echo "python=$(python3 --version 2>&1)"; echo "rdflib=$(python3 -c 'import rdflib;print(rdflib.__version__)')"
  echo "node=$(node --version)"; echo "playwright=$(node -e "console.log(require('$PLAYWRIGHT_MODULE/package.json').version)")"
  echo "init_exit=$(docker inspect -f '{{.State.ExitCode}}' protys-v110-protys-init-1)"
  for c in protys-fuseki protys-postgres protys-backend protys-frontend; do
    docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1"
  done
  echo "java_backend=$(docker exec protys-v110-protys-backend-1 java -version 2>&1 | head -1)"
} > "$S/00-versiones.txt"
cd "$D/scripts"
python3 estado.py ../salidas/02-estado-inicial-sin-razonar.json > ../salidas/02-estado-inicial-sin-razonar.txt
python3 razonar.py ../salidas/03-razonamiento-inicial.json > ../salidas/03-razonamiento-inicial.txt
python3 estado.py ../salidas/04-estado-inicial-razonado.json > ../salidas/04-estado-inicial-razonado.txt
python3 individuos_api.py ../salidas/05-individuos-api.json > ../salidas/05-individuos-api.txt
node individuos_web.js > ../salidas/06-individuos-web.txt
python3 estado.py ../salidas/07-estado-tras-ediciones.json > ../salidas/07-estado-tras-ediciones.txt
python3 comparar.py ../salidas/04-estado-inicial-razonado.json ../salidas/07-estado-tras-ediciones.json > ../salidas/07-comparacion-tras-ediciones.txt || true
python3 razonar.py ../salidas/08-razonamiento-tras-ediciones.json > ../salidas/08-razonamiento-tras-ediciones.txt
python3 estado.py ../salidas/09-estado-razonado-tras-ediciones.json > ../salidas/09-estado-razonado-tras-ediciones.txt
python3 comparar.py ../salidas/04-estado-inicial-razonado.json ../salidas/09-estado-razonado-tras-ediciones.json > ../salidas/09-comparacion-razonada.txt || true
python3 modulos.py ../salidas/10-turtle-invalido.json > ../salidas/10-turtle-invalido.txt
cd - > /dev/null
$COMPOSE logs --no-color protys-backend 2>&1 | sed -E 's/(Using generated security password: )[0-9a-f-]+/\1<redactada>/' > "$S/11-backend.log"
$COMPOSE down -v > "$S/12-compose-down.txt" 2>&1
