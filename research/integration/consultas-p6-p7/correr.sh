#!/bin/sh
# P6 y P7 con el caso de pintura más ontologies/paint-case-study-instances.ttl, por la plataforma.
# Uso, desde la raíz del repositorio:
#   PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/consultas-p6-p7/correr.sh
# Usa el proyecto de Compose «protys-v110» (puertos 38080, 38081, 33030 y 35432), lo recrea desde cero y al terminar
# lo baja con sus volúmenes. No toca otros proyectos.
set -eu
D=research/integration/consultas-p6-p7
S="$D/salidas"; C="$D/capturas"
INSTANCIAS=ontologies/paint-case-study-instances.ttl
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
export PROTYS_API=http://127.0.0.1:38080/api PROTYS_FUSEKI=http://127.0.0.1:33030/protys_canonical PROTYS_WEB=http://127.0.0.1:38081
rm -rf "$S" "$C" "$D/esperadas"; mkdir -p "$S" "$C"
export OUTDIR="$(pwd)/$C"
COMPOSE="docker compose -f docker/docker-compose.yml"

desde_cero() {
  $COMPOSE down -v > /dev/null 2>&1 || true
  $COMPOSE up -d --build > "$S/$1-compose-up.txt" 2>&1
  test "$(docker inspect -f '{{.State.ExitCode}}' protys-v110-protys-init-1)" = 0
}
subir() {
  curl -sS -o "$S/$1.json" -w '%{http_code}\n' -F "name=paint-case-study-instances" -F "file=@$INSTANCIAS;type=text/turtle" \
    "$PROTYS_API/ontology/modules/upload" > "$S/$1.codigo"
}
{
  echo "fecha_utc=$(date -u +%FT%TZ)"
  echo "instancias_sha256=$(shasum -a 256 $INSTANCIAS | cut -c1-64)"
  echo "instancias_triples=$(python3 -c "import rdflib;print(len(rdflib.Graph().parse('$INSTANCIAS',format='turtle')))")"
  echo "docker=$(docker version --format '{{.Server.Version}}')"; echo "compose=$(docker compose version --short)"
  echo "python=$(python3 --version 2>&1)"; echo "rdflib=$(python3 -c 'import rdflib;print(rdflib.__version__)')"
  echo "node=$(node --version)"; echo "playwright=$(node -e "console.log(require('$PLAYWRIGHT_MODULE/package.json').version)")"
} > "$S/00-versiones.txt"

(cd "$D/scripts" && python3 esperadas.py "../../../../$INSTANCIAS" ../esperadas > ../salidas/01-esperadas.txt)

# Estado 1: sólo el caso, razonado.
desde_cero 02
for c in protys-fuseki protys-postgres protys-backend protys-frontend; do
  docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1" >> "$S/00-versiones.txt"
done
echo "java_backend=$(docker exec protys-v110-protys-backend-1 java -version 2>&1 | head -1)" >> "$S/00-versiones.txt"
(cd "$D/scripts" && python3 razonar.py ../salidas/02-razonamiento-solo-caso.json > ../salidas/02-razonamiento-solo-caso.txt \
  && python3 estado.py ../salidas/02-estado-solo-caso.json > ../salidas/02-estado-solo-caso.txt \
  && python3 controles.py guardar ../salidas/02-grafos-solo-caso)

# Estado 2: caso + instancias. P6/P7 sin razonar, razonamiento, P6/P7 razonadas, catálogo, controles y capturas.
desde_cero 03
subir 03-carga-instancias
(cd "$D/scripts" && python3 p6p7.py "caso + instancias, sin razonamiento" ../salidas/03-p6p7-sin-razonar.json > ../salidas/03-p6p7-sin-razonar.txt \
  && python3 razonar.py ../salidas/04-razonamiento-con-instancias.json > ../salidas/04-razonamiento-con-instancias.txt \
  && python3 p6p7.py "caso + instancias, con razonamiento" ../salidas/05-p6p7-razonadas.json > ../salidas/05-p6p7-razonadas.txt \
  && python3 estado.py ../salidas/06-estado-con-instancias.json > ../salidas/06-estado-con-instancias.txt \
  && { python3 comparar.py ../salidas/02-estado-solo-caso.json ../salidas/06-estado-con-instancias.json > ../salidas/06-catalogo-con-y-sin-instancias.txt || true; } \
  && python3 controles.py guardar ../salidas/06-grafos-con-instancias \
  && python3 controles.py comparar ../salidas/02-grafos-solo-caso ../salidas/06-grafos-con-instancias "../../../../$INSTANCIAS" ../salidas/07-controles.json > ../salidas/07-controles.txt \
  && node capturas.js > ../salidas/08-capturas.txt)

$COMPOSE logs --no-color protys-backend 2>&1 | sed -E 's/(Using generated security password: )[0-9a-f-]+/\1<redactada>/' > "$S/09-backend.log"
$COMPOSE down -v > "$S/10-compose-down.txt" 2>&1
