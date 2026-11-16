#!/usr/bin/env bash
# Fase 3: recorre el asistente de incorporación por su API con el fragmento de ISO 10303 (AP242)
# sobre la plataforma ya levantada en configuración integrada, hasta donde el asistente alcanza.
#
# No completa la incorporación salteando pasos: si el asistente no permite registrar las reglas de
# vínculo del piloto (R27–R29), lo registra, cancela la sesión y comprueba que la red quedó igual
# (módulos, grafos, consistencia y las 21 consultas del catálogo).
#
# Uso:   API=http://127.0.0.1:18080/api FUSEKI=http://127.0.0.1:13030 research/integration/asistente/secuencia.sh
# Salida: research/integration/asistente/salidas/api/ (una respuesta por llamada, numerada) y RESUMEN.txt
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$REPO/research/integration/asistente/salidas/api"
API="${API:-http://127.0.0.1:18080/api}"
FUSEKI="${FUSEKI:-http://127.0.0.1:13030}"
FRAGMENTO="$REPO/ontologies/iso10303-ap242-fragment.owl"
NOMBRE="ISO 10303-242 (fragmento piloto)"
DIFF="$REPO/research/integration/erp/scripts/diferencias-catalogo.py"

rm -rf "$OUT"; mkdir -p "$OUT"
exec > >(tee "$OUT/secuencia.log") 2>&1
declare -a RESUMEN=()
paso() { RESUMEN+=("$(printf '%-9s %-50s %s' "$2" "$1" "$3")"); echo ">>> [$2] $1 — $3"; }
json() { python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print($2)" "$1"; }
llamada() { # llamada <archivo> <curl args...>: guarda cuerpo y código HTTP
  local f="$1"; shift
  local code; code=$(curl -sS -o "$OUT/$f" -w '%{http_code}' "$@")
  echo "$code" > "$OUT/$f.http"; echo "$code"
}
estado() { # estado <prefijo>: módulos, reglas y grafos de Fuseki
  curl -sS "$API/ontology/modules" -o "$OUT/$1-modulos.json"
  curl -sS "$API/alignment/rules" -o "$OUT/$1-reglas.json"
  curl -sS "$FUSEKI/protys_canonical/query" -H 'Accept: text/csv' \
    --data-urlencode 'query=SELECT ?g (COUNT(*) AS ?n) WHERE { GRAPH ?g { ?s ?p ?o } } GROUP BY ?g ORDER BY ?g' \
    | grep -v 'inference-results' > "$OUT/$1-grafos.csv"
}
consulta() {
  python3 - "$1" "$2" "$API" <<'PY'
import json, sys, urllib.request
q = open(sys.argv[1], encoding="utf-8").read()
req = urllib.request.Request(sys.argv[3] + "/sparql/execute", data=json.dumps({"query": q}).encode(),
                             headers={"Content-Type": "application/json"})
open(sys.argv[2], "wb").write(urllib.request.urlopen(req, timeout=120).read())
PY
}
catalogo() { mkdir -p "$1"; for f in "$REPO"/research/model/queries/Q*.sparql; do consulta "$f" "$1/$(basename "$f" .sparql).json"; done; }

echo "== Fase 3, asistente por la API — $(date -u +%FT%TZ); commit $(git -C "$REPO" rev-parse --short HEAD)"
echo "== fragmento: ontologies/iso10303-ap242-fragment.owl sha256=$(shasum -a 256 "$FRAGMENTO" | cut -d' ' -f1)"

# ---------------------------------------------------------------- 0. línea de base (integrada)
estado 00-antes
llamada 00-razonamiento-antes.json -X POST "$API/alignment/reasoning/execute" > /dev/null
catalogo "$OUT/00-catalogo-antes"
paso "0 línea de base" APROBADO "módulos=$(json "$OUT/00-antes-modulos.json" "len(d)") reglas=$(json "$OUT/00-antes-reglas.json" "len(d)") razonamiento=$(json "$OUT/00-razonamiento-antes.json" "d.get('status')") owl=$(json "$OUT/00-razonamiento-antes.json" "d.get('owlValidationStatus')")"

# ---------------------------------------------------------------- 1. subir el fragmento
code=$(llamada 01-paso1-subir.json -X POST "$API/wizard/step1/upload" -F "file=@$FRAGMENTO" -F "standardName=$NOMBRE")
SID=$(json "$OUT/01-paso1-subir.json" "d.get('tempModuleId') or ''" 2>/dev/null)
[ "$code" = 201 ] && [ -n "$SID" ] \
  && paso "1 subir fragmento AP242" APROBADO "http=$code sesión=$SID triples=$(json "$OUT/01-paso1-subir.json" "d.get('tripleCount')")" \
  || paso "1 subir fragmento AP242" FALLADO "http=$code"

# ---------------------------------------------------------------- 2. validar consistencia
code=$(llamada 02-paso2-validar.json -X POST "$API/wizard/step2/validate/$SID")
ok=$(json "$OUT/02-paso2-validar.json" "d.get('step2_consistent')" 2>/dev/null)
[ "$ok" = True ] && paso "2 validar consistencia (red + fragmento)" APROBADO "http=$code $(json "$OUT/02-paso2-validar.json" "d.get('step2_message')")" \
                 || paso "2 validar consistencia (red + fragmento)" FALLADO "http=$code consistente=$ok"

# ---------------------------------------------------------------- 3. reglas de vínculo R27–R29
python3 - "$OUT/00-antes-reglas.json" "$REPO/ontologies/iso10303-alignment-rules.owl" > "$OUT/03-reglas-piloto-disponibles.txt" <<'PY'
import json, sys, re
registradas = {r["ruleId"] for r in json.load(open(sys.argv[1]))}
piloto = sorted(set(re.findall(r'rdf:about="[^"]*#(R2[7-9])"', open(sys.argv[2], encoding="utf-8").read())))
print("reglas del piloto en iso10303-alignment-rules.owl:", piloto)
print("reglas registradas en la plataforma:", len(registradas))
for r in piloto:
    print(f"  {r}: {'registrada' if r in registradas else 'NO registrada: el paso 3 no la puede seleccionar'}")
PY
faltan=$(grep -c "NO registrada" "$OUT/03-reglas-piloto-disponibles.txt")
cat "$OUT/03-reglas-piloto-disponibles.txt"
# El paso 3 sólo acepta IDs de reglas registradas: se prueba pedir R27 por su identificador de regla.
code=$(llamada 03-paso3-pedir-R27.json -X POST "$API/wizard/step3/alignments/$SID" -H 'Content-Type: application/json' -d '[{"ruleId":"R27"}]')
msg=$(json "$OUT/03-paso3-pedir-R27.json" "d.get('step3_message')" 2>/dev/null)
if [ "$faltan" -gt 0 ]; then
  paso "3 seleccionar R27–R29 en el asistente" "NO-ALCANZA" "$faltan de 3 reglas no registradas; pedir R27: http=$code «${msg}»"
else
  paso "3 seleccionar R27–R29 en el asistente" APROBADO "las 3 reglas están registradas"
fi

# ---------------------------------------------------------------- 4. no se saltea: cancelar
code=$(llamada 04-cancelar.json -X DELETE "$API/wizard/$SID")
paso "4 cancelar la sesión (no se completa sin las reglas)" "$([ "$code" = 200 ] && echo APROBADO || echo FALLADO)" "http=$code"

# ---------------------------------------------------------------- 5. la red quedó igual
estado 05-despues
llamada 05-razonamiento-despues.json -X POST "$API/alignment/reasoning/execute" > /dev/null
catalogo "$OUT/05-catalogo-despues"
iguales=1
for f in modulos grafos; do
  python3 - "$OUT/00-antes-$f".* "$OUT/05-despues-$f".* <<'PY' || iguales=0
import json, sys
def norm(p):
    t = open(p).read()
    if p.endswith(".json"):
        return sorted((m["id"], m["name"], m.get("namedGraph")) for m in json.loads(t))
    return sorted(t.splitlines())
a, b = norm(sys.argv[1]), norm(sys.argv[2])
print("igual" if a == b else f"DISTINTO:\n antes={a}\n después={b}")
sys.exit(0 if a == b else 1)
PY
done
python3 "$DIFF" "$OUT/00-catalogo-antes" "$OUT/05-catalogo-despues" "$OUT/05-catalogo-diferencias.md" \
  "Consultas del catálogo antes y después de recorrer y cancelar el asistente" \
  "Generado por \`diferencias-catalogo.py\` a partir de \`00-catalogo-antes/\` y \`05-catalogo-despues/\`. La columna «con ERP» se lee «después»: no hay grafos ERP en esta plataforma." \
  > "$OUT/05-catalogo-diferencias.txt" 2>&1
cambios=$(awk -F'|' '/^\| Q/ { if ($6+0 != 0 || $7+0 != 0 || $4 != $5) n++ } END { print n+0 }' "$OUT/05-catalogo-diferencias.md")
c_antes=$(json "$OUT/00-razonamiento-antes.json" "d.get('owlValidationStatus')"); c_desp=$(json "$OUT/05-razonamiento-despues.json" "d.get('owlValidationStatus')")
if [ $iguales = 1 ] && [ "$cambios" = 0 ] && [ "$c_antes" = "$c_desp" ]; then
  paso "5 red sin cambios tras cancelar" APROBADO "módulos y grafos iguales; owl $c_antes → $c_desp; 21 consultas idénticas"
else
  paso "5 red sin cambios tras cancelar" FALLADO "iguales=$iguales consultas_con_cambios=$cambios owl $c_antes → $c_desp"
fi

{ echo "Resumen — Fase 3, asistente por la API ($(date -u +%FT%TZ))"; printf '%s\n' "${RESUMEN[@]}"; } | tee "$OUT/RESUMEN.txt"
