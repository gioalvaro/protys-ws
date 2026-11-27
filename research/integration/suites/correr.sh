#!/bin/sh
# Suites de pruebas y comprobaciones estáticas. Uso, desde la raíz del repositorio, con JAVA_HOME en un JDK 17 y
# Node 24 con npm 11 en el PATH:  sh research/integration/suites/correr.sh
# La prueba de sólo lectura del conector (ERPReadOnlyConnectionIntegrationTest) se saltea en la suite de backend
# porque necesita un PostgreSQL; la corre research/integration/erp-solo-lectura/solo-lectura.sh.
set -eu
S=research/integration/suites
R="$(pwd)"
fecha() { date -u +%FT%TZ; }
{ echo "# cd backend && ./mvnw test  (JDK $("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\(.*\)".*/\1/p'), $(fecha))"
  (cd backend && ./mvnw test 2>&1) | grep -E '^\[(INFO|WARNING|ERROR)\] (Tests run|BUILD|Total time)' | sed 's/^\[[A-Z]*\] //'
} > "$S/backend-pruebas.txt"
(cd frontend && npm ci --no-audit --no-fund > /dev/null 2>&1 && CI=true npm test -- --watchAll=false 2>&1) > "$S/interfaz-pruebas.txt"
(cd frontend && npm run build 2>&1) > "$S/interfaz-build.txt"
python3 -m unittest discover -s scripts/tests -v > "$S/guiones-pruebas.txt" 2>&1
python3 -m unittest discover -s research/evaluation/tests -v > "$S/evaluacion-pruebas.txt" 2>&1
python3 research/model/comprobar_extractos.py "$S/extractos-contenidos.json" > "$S/extractos-contenidos.txt"
T=$(mktemp -d "${TMPDIR:-/tmp}/sintaxis.XXXXXX")
python3 research/evaluation/comprobar_sintaxis.py --salida "$T" > "$S/sintaxis-ejemplos.txt"
sed "s#$R/#<repo>/#g" "$T/runtime/syntax-check.json" > "$S/sintaxis-ejemplos.json"
rm -rf "$T"
for f in "$S"/*.txt "$S"/*.json; do sed -i.bak "s#$R/#<repo>/#g" "$f" && rm -f "$f.bak"; done
tail -n 3 "$S/backend-pruebas.txt"; grep -E '^Tests:' "$S/interfaz-pruebas.txt"; tail -n 1 "$S/sintaxis-ejemplos.txt"
