#!/usr/bin/env bash
# Fase 3b: incorpora el piloto ISO 10303 (AP242) con el asistente, por su API, sobre la plataforma
# recién levantada en configuración integrada, y lo compara con la incorporación por programa.
#
# Lo que entra por el asistente es lo mismo que agrega la configuración h3_extended de research/catalog.json:
#   - ontologies/iso10303-ap242-fragment.owl       paso 1 (estándar)
#   - research/data/automotive.ttl                 paso 1 (datos de instancia de la sesión)
#   - ontologies/iso10303-alignment-rules.owl      paso 3 (reglas de la sesión R27–R29)
#   - las 22 reglas ya registradas                 paso 3 (selección)
# Comprobaciones: consistencia antes y después, las 21 consultas del catálogo antes y después, y los
# 9 controles H3_* (research/data/fixtures/assertions/) contra lo esperado en
# research/evaluation/current/assertion-groups/{g30,g31}/, que es lo que resume research/release/functional.json.
#
# Uso:   API=http://127.0.0.1:18080/api FUSEKI=http://127.0.0.1:13030 research/integration/asistente/incorporar.sh
# Salida: research/integration/asistente/salidas/fase3b/api/ (una respuesta por llamada) y RESUMEN.txt
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$REPO/research/integration/asistente/salidas/fase3b/api"
API="${API:-http://127.0.0.1:18080/api}"
FUSEKI="${FUSEKI:-http://127.0.0.1:13030}"
FRAGMENTO="$REPO/ontologies/iso10303-ap242-fragment.owl"
DATOS="$REPO/research/data/automotive.ttl"
REGLAS="$REPO/ontologies/iso10303-alignment-rules.owl"
NOMBRE="ISO 10303-242 (fragmento piloto)"
DIFF="$REPO/research/integration/erp/scripts/diferencias-catalogo.py"
CONTROLES="$REPO/research/data/fixtures/assertions"
ESPERADO="$REPO/research/evaluation/current/assertion-groups"

rm -rf "$OUT"; mkdir -p "$OUT"
exec > >(tee "$OUT/incorporar.log") 2>&1
declare -a RESUMEN=()
paso() { RESUMEN+=("$(printf '%-9s %-52s %s' "$2" "$1" "$3")"); echo ">>> [$2] $1 — $3"; }
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
    --data-urlencode 'query=SELECT ?g (COUNT(*) AS ?n) WHERE { GRAPH ?g { ?s ?p ?o } } GROUP BY ?g ORDER BY ?g' > "$OUT/$1-grafos.csv"
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
controles() { mkdir -p "$1"; for f in "$CONTROLES"/H3_*.sparql; do consulta "$f" "$1/$(basename "$f" .sparql).json"; done; }

echo "== Fase 3b, incorporación completa por la API — $(date -u +%FT%TZ); commit $(git -C "$REPO" rev-parse --short HEAD)"
for f in "$FRAGMENTO" "$DATOS" "$REGLAS"; do echo "== ${f#$REPO/} sha256=$(shasum -a 256 "$f" | cut -d' ' -f1)"; done

# ---------------------------------------------------------------- 0. línea de base (integrada)
estado 00-antes
llamada 00-razonamiento-antes.json -X POST "$API/alignment/reasoning/execute" > /dev/null
catalogo "$OUT/00-catalogo-antes"
controles "$OUT/00-controles-antes"
activas0=$(json "$OUT/00-antes-reglas.json" "sum(1 for r in d if r.get('active'))")
paso "0 línea de base" APROBADO "módulos=$(json "$OUT/00-antes-modulos.json" "len(d)") reglas=$(json "$OUT/00-antes-reglas.json" "len(d)") activas=$activas0 razonamiento=$(json "$OUT/00-razonamiento-antes.json" "d.get('status')") owl=$(json "$OUT/00-razonamiento-antes.json" "d.get('owlValidationStatus')")"

# ---------------------------------------------------------------- 1. estándar y datos de la sesión
code=$(llamada 01-paso1-subir.json -X POST "$API/wizard/step1/upload" -F "file=@$FRAGMENTO" --form-string "standardName=$NOMBRE")
SID=$(json "$OUT/01-paso1-subir.json" "d.get('tempModuleId') or ''" 2>/dev/null)
[ "$code" = 201 ] && [ -n "$SID" ] \
  && paso "1 subir fragmento AP242" APROBADO "http=$code sesión=$SID triples=$(json "$OUT/01-paso1-subir.json" "d.get('tripleCount')")" \
  || { paso "1 subir fragmento AP242" FALLADO "http=$code"; printf '%s\n' "${RESUMEN[@]}" | tee "$OUT/RESUMEN.txt"; exit 1; }
code=$(llamada 01b-paso1-datos.json -X POST "$API/wizard/step1/data/$SID" -F "file=@$DATOS")
[ "$code" = 201 ] && paso "1b subir datos del piloto (automotive.ttl)" APROBADO "http=$code triples=$(json "$OUT/01b-paso1-datos.json" "d.get('dataTripleCount')")" \
                  || paso "1b subir datos del piloto (automotive.ttl)" FALLADO "http=$code $(json "$OUT/01b-paso1-datos.json" "d.get('step1_message')" 2>/dev/null)"

# ---------------------------------------------------------------- 2. validar consistencia
code=$(llamada 02-paso2-validar.json -X POST "$API/wizard/step2/validate/$SID")
ok=$(json "$OUT/02-paso2-validar.json" "d.get('step2_consistent')" 2>/dev/null)
[ "$ok" = True ] && paso "2 validar (red + fragmento + datos)" APROBADO "http=$code $(json "$OUT/02-paso2-validar.json" "d.get('step2_message')")" \
                 || paso "2 validar (red + fragmento + datos)" FALLADO "http=$code consistente=$ok"

# ---------------------------------------------------------------- 3. reglas: R27–R29 de la sesión + las 22 registradas
code=$(llamada 03a-paso3-reglas-sesion.json -X POST "$API/wizard/step3/rules/$SID" -F "file=@$REGLAS")
[ "$code" = 201 ] && paso "3a cargar R27–R29 como reglas de la sesión" APROBADO "http=$code reglas=$(json "$OUT/03a-paso3-reglas-sesion.json" "','.join(d.get('sessionRuleIds') or [])")" \
                  || paso "3a cargar R27–R29 como reglas de la sesión" FALLADO "http=$code $(json "$OUT/03a-paso3-reglas-sesion.json" "d.get('step3_message')" 2>/dev/null)"
json "$OUT/00-antes-reglas.json" "json.dumps([{'id': r['id']} for r in d])" > "$OUT/03b-seleccion.json"
code=$(llamada 03b-paso3-seleccion.json -X POST "$API/wizard/step3/alignments/$SID" -H 'Content-Type: application/json' --data-binary "@$OUT/03b-seleccion.json")
[ "$code" = 200 ] && paso "3b seleccionar las 22 reglas registradas" APROBADO "http=$code seleccionadas=$(json "$OUT/03b-paso3-seleccion.json" "d.get('step3_rulesCount')")" \
                  || paso "3b seleccionar las 22 reglas registradas" FALLADO "http=$code $(json "$OUT/03b-paso3-seleccion.json" "d.get('step3_message')" 2>/dev/null)"

# ---------------------------------------------------------------- 4. verificar inferencias
code=$(llamada 04-paso4-verificar.json -X POST "$API/wizard/step4/verify/$SID")
ok=$(json "$OUT/04-paso4-verificar.json" "d.get('step4_verified')" 2>/dev/null)
[ "$ok" = True ] && paso "4 verificar (22 + 3 reglas)" APROBADO "http=$code ejecutadas=$(json "$OUT/04-paso4-verificar.json" "len(d.get('executedRules') or [])") $(json "$OUT/04-paso4-verificar.json" "d.get('step4_message')")" \
                 || paso "4 verificar (22 + 3 reglas)" FALLADO "http=$code $(json "$OUT/04-paso4-verificar.json" "d.get('step4_message')" 2>/dev/null)"

# ---------------------------------------------------------------- 5. completar
code=$(llamada 05-completar.json -X POST "$API/wizard/complete/$SID")
[ "$code" = 201 ] && paso "5 completar la incorporación" APROBADO "http=$code módulo=$(json "$OUT/05-completar.json" "d.get('finalModuleId')") activadas=$(json "$OUT/05-completar.json" "len(d.get('activatedRules') or [])")" \
                  || paso "5 completar la incorporación" FALLADO "http=$code $(json "$OUT/05-completar.json" "d.get('message')" 2>/dev/null)"

# ---------------------------------------------------------------- 6. razonamiento global y comprobaciones
estado 06-despues
llamada 06-razonamiento-despues.json -X POST "$API/alignment/reasoning/execute" > /dev/null
catalogo "$OUT/06-catalogo-despues"
controles "$OUT/06-controles-despues"
python3 - "$OUT/06-despues-reglas.json" "$OUT/06-despues-modulos.json" > "$OUT/06-reglas-y-modulos.txt" <<'PY'
import json, sys
reglas = json.load(open(sys.argv[1])); modulos = json.load(open(sys.argv[2]))
activas = sorted(r["ruleId"] for r in reglas if r.get("active"))
print("reglas registradas:", len(reglas), "activas:", len(activas))
print("R27–R29 activas:", [r for r in ("R27", "R28", "R29") if r in activas])
print("módulos:"); [print(" ", m["name"], m.get("namedGraph"), m.get("tripleCount")) for m in modulos]
PY
cat "$OUT/06-reglas-y-modulos.txt"
activas=$(json "$OUT/06-despues-reglas.json" "sum(1 for r in d if r.get('active'))")
piloto=$(json "$OUT/06-despues-reglas.json" "sum(1 for r in d if r.get('active') and r['ruleId'] in ('R27','R28','R29'))")
[ "$activas" = 25 ] && [ "$piloto" = 3 ] && paso "6a reglas activas en la plataforma" APROBADO "activas=$activas (22 + R27–R29), como h3_extended" \
                                         || paso "6a reglas activas en la plataforma" FALLADO "activas=$activas piloto=$piloto"
c_antes=$(json "$OUT/00-razonamiento-antes.json" "d.get('owlValidationStatus')"); c_desp=$(json "$OUT/06-razonamiento-despues.json" "d.get('owlValidationStatus')")
[ "$(json "$OUT/06-razonamiento-despues.json" "d.get('status')")" = SUCCESS ] && [ "$c_desp" = CONSISTENT ] \
  && paso "6b razonamiento global consistente" APROBADO "owl $c_antes → $c_desp; entrada $(json "$OUT/00-razonamiento-antes.json" "d.get('inputTripleCount')") → $(json "$OUT/06-razonamiento-despues.json" "d.get('inputTripleCount')"); inferidos $(json "$OUT/00-razonamiento-antes.json" "d.get('inferredTripleCount')") → $(json "$OUT/06-razonamiento-despues.json" "d.get('inferredTripleCount')")" \
  || paso "6b razonamiento global consistente" FALLADO "status=$(json "$OUT/06-razonamiento-despues.json" "d.get('status')") owl $c_antes → $c_desp $(json "$OUT/06-razonamiento-despues.json" "d.get('errorMessage')")"

python3 "$DIFF" "$OUT/00-catalogo-antes" "$OUT/06-catalogo-despues" "$OUT/06-catalogo-diferencias.md" \
  "Consultas del catálogo antes y después de incorporar el piloto ISO 10303 con el asistente" \
  "Generado por \`diferencias-catalogo.py\` a partir de \`00-catalogo-antes/\` y \`06-catalogo-despues/\`. Las columnas «sin ERP» y «con ERP» se leen «antes» y «después» de incorporar; la de «individuos ERP» no aplica (no hay grafos ERP en esta plataforma)." \
  > "$OUT/06-catalogo-diferencias.txt" 2>&1
cambios=$(awk -F'|' '/^\| Q/ { if ($6+0 != 0 || $7+0 != 0 || $4 != $5) n++ } END { print n+0 }' "$OUT/06-catalogo-diferencias.md")
[ "$cambios" = 0 ] && paso "6c las 21 consultas del catálogo" APROBADO "idénticas antes y después" \
                   || paso "6c las 21 consultas del catálogo" DISTINTO "$cambios consultas cambian (ver 06-catalogo-diferencias.md)"

python3 - "$OUT" "$ESPERADO" > "$OUT/06-controles-H3.md" <<'PY'
import json, sys
from pathlib import Path
out, esperado = Path(sys.argv[1]), Path(sys.argv[2])
filas, iguales, comparables = [], 0, 0
for grupo in ("g30", "g31"):
    for f in sorted((esperado / grupo).glob("H3_*.json")):
        exp = json.loads(f.read_text())["boolean"]
        def valor(d):
            p = out / d / f.name
            try: return json.loads(p.read_text())["sparqlJson"]["boolean"]
            except Exception as e: return f"error: {e}"
        antes, despues = valor("00-controles-antes"), valor("06-controles-despues")
        if grupo == "g30":
            comparables += 1; ok = despues == exp; iguales += ok
            nota = "igual a lo esperado" if ok else "DISTINTO de lo esperado"
            filas.append(f"| {f.stem} | g30 (h3_extended) | {str(exp).lower()} | {str(antes).lower()} | **{str(despues).lower()}** | {nota} |")
        else:
            nota = ("se compara con «antes» (sin el fragmento), pero sin los datos del piloto da falso de forma trivial: no es equivalente"
                    if antes == exp else "DISTINTO de lo esperado")
            filas.append(f"| {f.stem} | g31 (integrada + datos, sin extensión) | {str(exp).lower()} | **{str(antes).lower()}** | {str(despues).lower()} | {nota} |")
print("# Controles H3 del piloto: plataforma contra la evaluación por programa\n")
print("Esperado: `research/evaluation/current/assertion-groups/{g30,g31}/H3_*.json` (resumido en `research/release/functional.json`).")
print("Antes y después: las mismas consultas ASK de `research/data/fixtures/assertions/` por `POST /api/sparql/execute`.\n")
print("| Control | Configuración evaluada | Esperado | Plataforma antes | Plataforma después | Resultado |")
print("|---|---|---|---|---|---|")
print("\n".join(filas))
print(f"\nControles g30 comparables: {iguales} de {comparables} iguales a lo esperado.")
PY
cat "$OUT/06-controles-H3.md"
h3=$(grep -c "| igual a lo esperado |" "$OUT/06-controles-H3.md")
[ "$h3" = 8 ] && paso "6d controles H3 (g30) contra lo esperado" APROBADO "8 de 8 iguales" \
              || paso "6d controles H3 (g30) contra lo esperado" DISTINTO "$h3 de 8 iguales (ver 06-controles-H3.md)"

{ echo "Resumen — Fase 3b, incorporación completa por la API ($(date -u +%FT%TZ))"; printf '%s\n' "${RESUMEN[@]}"; } | tee "$OUT/RESUMEN.txt"
