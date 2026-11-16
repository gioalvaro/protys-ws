#!/usr/bin/env bash
# Prueba de punta a punta de la Fase 1: PROTYS-WS lee ADempiere y Odoo, materializa con R2RML,
# razona sobre la red más los dos grafos y responde P1–P5 y una consulta conjunta.
#
# Corre de cero en un proyecto de Compose propio (por defecto "protys-e2e") con puertos
# alternativos, así no toca otra instancia de la plataforma. Al empezar borra los contenedores
# y volúmenes de ESE proyecto. Requiere docker (con compose), curl y python3.
#
# Uso:   research/integration/erp/run.sh            (deja todo levantado al terminar)
#        KEEP_UP=0 research/integration/erp/run.sh  (baja el proyecto al terminar)
# Salida: research/integration/erp/salidas/e2e/ y un resumen APROBADO/FALLADO por paso.
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$REPO/research/integration/erp/salidas/e2e"
QUERIES="$REPO/research/integration/erp/queries"
SCRIPTS="$REPO/research/integration/erp/scripts"

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-protys-e2e}"
export PROTYS_FUSEKI_PORT="${PROTYS_FUSEKI_PORT:-13030}"
export PROTYS_POSTGRES_PORT="${PROTYS_POSTGRES_PORT:-15432}"
export PROTYS_BACKEND_PORT="${PROTYS_BACKEND_PORT:-18080}"
export PROTYS_FRONTEND_PORT="${PROTYS_FRONTEND_PORT:-18081}"
export ERP_ODOO_DB_PORT="${ERP_ODOO_DB_PORT:-15434}"
export ERP_ADEMPIERE_DB_PORT="${ERP_ADEMPIERE_DB_PORT:-15433}"
RO_USER="${PROTYS_RO_USER:-protys_ro}"
RO_PASS="${PROTYS_RO_PASSWORD:-protys_ro_demo}"
API="http://127.0.0.1:${PROTYS_BACKEND_PORT}/api"
COMPOSE=(docker compose -f "$REPO/docker/docker-compose.yml" -f "$REPO/docker/integration/docker-compose.erp.yml")
MAPPINGS_IN_BACKEND=/opt/protys/integration/mappings

rm -rf "$OUT"; mkdir -p "$OUT"
LOG="$OUT/run.log"
exec > >(tee "$LOG") 2>&1

declare -a RESUMEN=()
FALLOS=0
paso() { # paso <nombre> <APROBADO|FALLADO> <detalle>
  RESUMEN+=("$(printf '%-9s %-40s %s' "$2" "$1" "$3")")
  [ "$2" = "APROBADO" ] || FALLOS=$((FALLOS + 1))
  echo ">>> [$2] $1 — $3"
}
json() { python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print($2)" "$1"; }

echo "== PROTYS-WS, Fase 1, prueba de punta a punta — $(date -u +%FT%TZ)"
echo "== proyecto Compose: $COMPOSE_PROJECT_NAME, backend en 127.0.0.1:$PROTYS_BACKEND_PORT"

# ---------------------------------------------------------------- 1. levantar de cero
"${COMPOSE[@]}" down -v --remove-orphans > "$OUT/01-down.txt" 2>&1
if "${COMPOSE[@]}" up -d --build --wait adempiere-db odoo-db protys-fuseki protys-postgres protys-backend \
     > "$OUT/01-up.txt" 2>&1 \
   && "${COMPOSE[@]}" run --rm odoo-init > "$OUT/01-odoo-init.txt" 2>&1 \
   && "${COMPOSE[@]}" run --rm protys-init > "$OUT/01-protys-init.txt" 2>&1; then
  paso "1 levantar plataforma y ERP" APROBADO "ADempiere, Odoo (mrp), Fuseki, PostgreSQL, backend y red integrada cargada"
else
  paso "1 levantar plataforma y ERP" FALLADO "ver salidas/e2e/01-*.txt"
  printf '%s\n' "${RESUMEN[@]}"; exit 1
fi

# ---------------------------------------------------------------- 2. versiones
{
  echo "fecha_utc=$(date -u +%FT%TZ)"
  echo "commit=$(git -C "$REPO" rev-parse HEAD)$(git -C "$REPO" diff --quiet || echo '+cambios-sin-commit')"
  echo "docker=$(docker version --format '{{.Server.Version}}')"
  echo "compose=$(docker compose version --short)"
  for s in adempiere-db odoo-db protys-fuseki protys-postgres protys-backend; do
    c=$("${COMPOSE[@]}" ps -q "$s")
    echo "imagen[$s]=$(docker inspect --format '{{.Config.Image}}' "$c") id=$(docker inspect --format '{{.Image}}' "$c")"
  done
  echo "imagenes_declaradas=$("${COMPOSE[@]}" config --images | sort -u | tr '\n' ' ')"
  echo "odoo=$("${COMPOSE[@]}" run --rm --entrypoint odoo odoo-init --version 2>/dev/null | tail -1)"
  echo "adempiere_semilla=adempiere_postgresql_seed.tar.gz 3.9.4 sha256=ed4b2b3d33bbf831cb2443195cea0ede2f836e7212a123042e0b794ca87bbd1c"
  echo "java_backend=$(docker exec "$("${COMPOSE[@]}" ps -q protys-backend)" java -version 2>&1 | head -1)"
  echo "postgres_adempiere=$(docker exec "$("${COMPOSE[@]}" ps -q adempiere-db)" postgres --version)"
  echo "postgres_odoo=$(docker exec "$("${COMPOSE[@]}" ps -q odoo-db)" postgres --version)"
} > "$OUT/02-versiones.txt" 2>&1
paso "2 registrar versiones" APROBADO "salidas/e2e/02-versiones.txt"

# ---------------------------------------------------------------- 3. huella antes
COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" "$SCRIPTS/huella-esquema.sh" adempiere > "$OUT/03-huella-antes.txt"
COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" "$SCRIPTS/huella-esquema.sh" odoo >> "$OUT/03-huella-antes.txt"
paso "3 huella de esquemas (antes)" APROBADO "$(tr '\n' ' ' < "$OUT/03-huella-antes.txt" | cut -c1-60)…"

# ---------------------------------------------------------------- 4–6. conectores
for erp in adempiere odoo; do
  case $erp in
    adempiere) TYPE=ADEMPIERE; URL="jdbc:postgresql://adempiere-db:5432/adempiere" ;;
    odoo)      TYPE=ODOO;      URL="jdbc:postgresql://odoo-db:5432/odoo" ;;
  esac
  G="http://w3id.org/protys/erp/$erp"
  curl -sS -X POST "$API/erp/connectors" -H 'Content-Type: application/json' -o "$OUT/04-registro-$erp.json" \
    -d "{\"name\":\"$erp-demo\",\"type\":\"$TYPE\",\"jdbcUrl\":\"$URL\",\"username\":\"$RO_USER\",\"password\":\"$RO_PASS\",
         \"databaseName\":\"$erp\",\"namedGraph\":\"$G\",
         \"r2rmlMappingPath\":\"$MAPPINGS_IN_BACKEND/$erp.r2rml.ttl\"}"
  CID=$(json "$OUT/04-registro-$erp.json" "d['id']" 2>/dev/null)
  curl -sS -X POST "$API/erp/connectors/$CID/test" -o "$OUT/04-test-$erp.json"
  if [ "$(json "$OUT/04-test-$erp.json" "d['connected']" 2>/dev/null)" = "True" ]; then
    paso "4 registrar y probar conexión ($erp)" APROBADO "id=$CID usuario=$RO_USER"
  else
    paso "4 registrar y probar conexión ($erp)" FALLADO "ver 04-*-$erp.json"
  fi

  code=$(curl -sS -X POST "$API/erp/connectors/$CID/introspect" -o "$OUT/05-introspect-$erp.json" -w '%{http_code}')
  n=$(json "$OUT/05-introspect-$erp.json" "len(d)" 2>/dev/null || echo 0)
  [ "$code" = 200 ] && [ "$n" -gt 0 ] \
    && paso "5 inspeccionar esquema ($erp)" APROBADO "$n tablas" \
    || paso "5 inspeccionar esquema ($erp)" FALLADO "http=$code"

  code=$(curl -sS -X POST "$API/erp/connectors/$CID/materialize" -o "$OUT/06-materialize-$erp.json" -w '%{http_code}')
  st=$(json "$OUT/06-materialize-$erp.json" "d.get('status')" 2>/dev/null)
  tr=$(json "$OUT/06-materialize-$erp.json" "d.get('tripleCount')" 2>/dev/null)
  mp=$(json "$OUT/06-materialize-$erp.json" "d.get('mappingsProcessed')" 2>/dev/null)
  [ "$code" = 201 ] && [ "$st" = SUCCESS ] \
    && paso "6 materializar con R2RML ($erp)" APROBADO "$tr triples de $mp triples maps en $G" \
    || paso "6 materializar con R2RML ($erp)" FALLADO "http=$code status=$st"
  curl -sS "$API/erp/connectors/$CID/history" -o "$OUT/06-history-$erp.json"
  curl -sS "http://127.0.0.1:${PROTYS_FUSEKI_PORT}/protys_canonical/data?graph=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$G")" \
    -H 'Accept: text/turtle' -o "$OUT/06-grafo-$erp.ttl"
done

# ---------------------------------------------------------------- 7. razonamiento
# Línea de base: la red sola (sin cuerpo, comportamiento anterior al parámetro dataGraphs).
curl -sS -X POST "$API/alignment/reasoning/execute" -o "$OUT/07a-razonamiento-sin-erp.json"
base_in=$(json "$OUT/07a-razonamiento-sin-erp.json" "d.get('inputTripleCount')" 2>/dev/null || echo 0)
code=$(curl -sS -X POST "$API/alignment/reasoning/execute" -H 'Content-Type: application/json' \
  -d "{\"dataGraphs\":[\"http://w3id.org/protys/erp/adempiere\",\"http://w3id.org/protys/erp/odoo\"]}" -o "$OUT/07-razonamiento.json" -w '%{http_code}')
st=$(json "$OUT/07-razonamiento.json" "d.get('status')" 2>/dev/null)
vs=$(json "$OUT/07-razonamiento.json" "d.get('validationStatus')" 2>/dev/null)
ow=$(json "$OUT/07-razonamiento.json" "d.get('owlValidationStatus')" 2>/dev/null)
inf=$(json "$OUT/07-razonamiento.json" "d.get('inferredTripleCount')" 2>/dev/null)
inp=$(json "$OUT/07-razonamiento.json" "d.get('inputTripleCount')" 2>/dev/null)
if [ "$code" = 201 ] && [ "$st" = SUCCESS ] && [ "$ow" = CONSISTENT ]; then
  paso "7 razonar red + 2 grafos ERP" APROBADO "owl=$ow validación=$vs entrada=$inp inferidos=$inf"
else
  paso "7 razonar red + 2 grafos ERP" FALLADO "http=$code status=$st owl=$ow validación=$vs"
fi
# Los grafos ERP entran completos: la entrada crece exactamente en los triples materializados.
erp_tr=$(( $(json "$OUT/06-materialize-adempiere.json" "d.get('tripleCount') or 0") + $(json "$OUT/06-materialize-odoo.json" "d.get('tripleCount') or 0") ))
if [ "$(( inp - base_in ))" = "$erp_tr" ]; then
  paso "7 grafos ERP incluidos en el razonamiento" APROBADO "entrada $base_in → $inp (+$erp_tr = triples materializados)"
else
  paso "7 grafos ERP incluidos en el razonamiento" FALLADO "entrada $base_in → $inp, se esperaba +$erp_tr"
fi

# ---------------------------------------------------------------- 8. consultas
consulta() { # consulta <archivo.rq> <salida.json>
  python3 - "$1" "$2" "$API" <<'PY'
import json, sys, urllib.request
q = open(sys.argv[1], encoding="utf-8").read()
req = urllib.request.Request(sys.argv[3] + "/sparql/execute", data=json.dumps({"query": q}).encode(),
                             headers={"Content-Type": "application/json"})
body = urllib.request.urlopen(req, timeout=120).read()
open(sys.argv[2], "wb").write(body)
PY
}
fuentes() { # cuántas filas por grafo ERP
  python3 - "$1" <<'PY'
import json, sys, collections
d = json.load(open(sys.argv[1]))
rows = ((d.get("sparqlJson") or {}).get("results") or {}).get("bindings") or []
c = collections.Counter(r.get("g", {}).get("value", "").rsplit("/", 1)[-1] for r in rows)
print(len(rows), " ".join(f"{k}={v}" for k, v in sorted(c.items())))
PY
}
# P1 también tal cual la consulta canónica Q04, sin cambios.
consulta "$REPO/research/model/queries/Q04.sparql" "$OUT/08-Q04-canonica.json"
q04_erp=$(python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print(sum(1 for r in d['sparqlJson']['results']['bindings'] if '/protys/erp/' in r.get('process',{}).get('value','')))" "$OUT/08-Q04-canonica.json")
[ "$q04_erp" -gt 0 ] && paso "8 Q04 canónica sin cambios (P1)" APROBADO "$q04_erp filas con procesos ERP" \
                     || paso "8 Q04 canónica sin cambios (P1)" FALLADO "sin filas ERP"
for q in P1 P2 P3 P4 P5 CRUZADA; do
  consulta "$QUERIES/$q.rq" "$OUT/08-$q.json"
  read -r total detalle <<< "$(fuentes "$OUT/08-$q.json")"
  if [ "${total:-0}" -gt 0 ]; then
    if [ "$q" = CRUZADA ] && ! { [[ "$detalle" == *adempiere=* ]] && [[ "$detalle" == *odoo=* ]]; }; then
      paso "8 consulta $q" FALLADO "no combina los dos ERP: $detalle"
    else
      paso "8 consulta $q" APROBADO "$total filas ($detalle)"
    fi
  else
    paso "8 consulta $q" FALLADO "respuesta vacía"
  fi
done
consulta "$QUERIES/INFERENCIA.rq" "$OUT/08-INFERENCIA.json"
ninf=$(json "$OUT/08-INFERENCIA.json" "len(d['sparqlJson']['results']['bindings'])" 2>/dev/null || echo 0)
paso "8 tipos inferidos sobre individuos ERP" APROBADO "$ninf tipos deducidos no afirmados (informativo)"

# ---------------------------------------------------------------- 9. sólo lectura
ro_ok=1
for pair in "adempiere-db adempiere adempiere.m_product" "odoo-db odoo public.mrp_bom"; do
  set -- $pair
  c=$("${COMPOSE[@]}" ps -q "$1")
  docker exec -e PGPASSWORD="$RO_PASS" "$c" psql -h localhost -U "$RO_USER" -d "$2" -v ON_ERROR_STOP=1 \
    -c "BEGIN READ WRITE; CREATE TABLE protys_intrusion(i int); ROLLBACK;" > "$OUT/09-solo-lectura-$2.txt" 2>&1 && ro_ok=0
  docker exec -e PGPASSWORD="$RO_PASS" "$c" psql -h localhost -U "$RO_USER" -d "$2" -v ON_ERROR_STOP=1 \
    -c "DELETE FROM $3 WHERE false;" >> "$OUT/09-solo-lectura-$2.txt" 2>&1 && ro_ok=0
done
[ $ro_ok = 1 ] && paso "9 rol de conexión sin escritura" APROBADO "CREATE y DELETE rechazados en los dos ERP" \
               || paso "9 rol de conexión sin escritura" FALLADO "ver 09-solo-lectura-*.txt"

# ---------------------------------------------------------------- 10. huella después
COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" "$SCRIPTS/huella-esquema.sh" adempiere > "$OUT/10-huella-despues.txt"
COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" "$SCRIPTS/huella-esquema.sh" odoo >> "$OUT/10-huella-despues.txt"
if diff "$OUT/03-huella-antes.txt" "$OUT/10-huella-despues.txt" > "$OUT/10-diff.txt"; then
  paso "10 esquemas ERP sin cambios" APROBADO "huellas idénticas antes y después"
else
  paso "10 esquemas ERP sin cambios" FALLADO "ver 10-diff.txt"
fi

# ---------------------------------------------------------------- resumen
{
  echo "Resumen — PROTYS-WS Fase 1 ($(date -u +%FT%TZ), proyecto $COMPOSE_PROJECT_NAME)"
  printf '%s\n' "${RESUMEN[@]}"
  echo "resultado: $([ $FALLOS = 0 ] && echo APROBADO || echo "FALLADO ($FALLOS pasos)")"
} | tee "$OUT/RESUMEN.txt"

[ "${KEEP_UP:-1}" = 0 ] && "${COMPOSE[@]}" down -v > /dev/null 2>&1
exit $([ $FALLOS = 0 ] && echo 0 || echo 1)
