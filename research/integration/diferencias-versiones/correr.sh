#!/bin/sh
# Diferencias entre ontologies/ de la versión v1.0.0 (etiqueta) y de v1.1.0 (árbol de trabajo): triples, inferencias,
# las 21 consultas y el censo de clases y propiedades. No usa la plataforma.
# Uso, desde la raíz del repositorio, con JAVA_HOME en un JDK 17 y los ejecutores preparados:
#   sh research/integration/diferencias-versiones/correr.sh
set -eu
D=research/integration/diferencias-versiones
S="$D/salidas"; rm -rf "$S"; mkdir -p "$S"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/diferencias-versiones.XXXXXX")
{
  echo "fecha_utc=$(date -u +%FT%TZ)"; echo "etiqueta_v1.0.0=$(git rev-parse 'v1.0.0^{commit}')"
  echo "python=$(python3 --version 2>&1)"; echo "rdflib=$(python3 -c 'import rdflib;print(rdflib.__version__)')"
  echo "java=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
} > "$S/00-versiones.txt"
python3 "$D/scripts/diferencias.py" "$S/diferencias.json" > "$S/diferencias.txt"
python3 "$D/scripts/corridas.py" "$S/corridas" "$TMP" > "$S/corridas.txt"
python3 "$D/scripts/comparar.py" "$S/corridas" > "$S/comparacion.txt"
python3 "$D/scripts/censo.py" "$S/censo.json" > "$S/censo.txt"
rm -rf "$TMP"
