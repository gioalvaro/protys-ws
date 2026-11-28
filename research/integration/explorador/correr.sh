#!/bin/sh
# Explorador del catálogo integrado por la API: jerarquía de clases y árboles de propiedades (v1.1.1), conteo
# independiente con rdflib y comparación de /classes con la evidencia E-13 de v1.1.0. Uso, desde la raíz del repositorio:
#   sh research/integration/explorador/correr.sh
# Proyecto de Compose «protys-v110» (puertos 38080, 38081, 33030 y 35432); las imágenes se reconstruyen sin caché desde
# el árbol de trabajo y al terminar se baja. Las salidas de v1.1.0 (api-classes.json, jerarquia.txt, 01-compose-up.txt)
# no se tocan: esta corrida escribe archivos nuevos.
set -eu
D=research/integration/explorador
S="$D/salidas"
mkdir -p "$S"
rm -f "$S"/v1.1.1-* "$S"/api-classes-v1.1.1.json "$S"/jerarquia-v1.1.1.txt "$S"/api-properties.json "$S"/propiedades.txt \
      "$S"/conteo-independiente.* "$S"/clases-v1.1.0-vs-v1.1.1.*
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
C="docker compose -f docker/docker-compose.yml"
{
  echo "fecha_utc=$(date -u +%FT%TZ)"
  echo "commit=$(git rev-parse HEAD)"
  echo "cambios_sin_commit=$(git status --porcelain --untracked-files=no | wc -l | tr -d ' ')"
  echo "python=$(python3 --version 2>&1)"
  echo "rdflib=$(python3 -c 'import rdflib; print(rdflib.__version__)')"
} > "$S/v1.1.1-00-versiones.txt"
$C down -v > /dev/null 2>&1 || true
$C build --no-cache > "$S/v1.1.1-01-compose-up.txt" 2>&1
$C up -d >> "$S/v1.1.1-01-compose-up.txt" 2>&1
for c in protys-backend protys-frontend; do docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1" >> "$S/v1.1.1-00-versiones.txt"; done
export PROTYS_API=http://127.0.0.1:38080/api
python3 "$D/jerarquia.py" "$S/api-classes-v1.1.1.json" > "$S/jerarquia-v1.1.1.txt"
python3 "$D/propiedades.py" "$S/api-properties.json" > "$S/propiedades.txt"
$C down -v > /dev/null 2>&1
python3 "$D/contar-propiedades.py" "$S/api-properties.json" "$S/conteo-independiente.json" > "$S/conteo-independiente.txt"
python3 "$D/comparar-clases.py" "$S/api-classes.json" "$S/api-classes-v1.1.1.json" "$S/clases-v1.1.0-vs-v1.1.1.json" > "$S/clases-v1.1.0-vs-v1.1.1.txt"
cat "$S/jerarquia-v1.1.1.txt" "$S/propiedades.txt" "$S/conteo-independiente.txt" "$S/clases-v1.1.0-vs-v1.1.1.txt"
