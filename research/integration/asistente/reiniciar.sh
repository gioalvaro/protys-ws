#!/usr/bin/env bash
# Reinicia la plataforma completa (sin borrar volúmenes) y comprueba que protys-init conserva
# lo registrado por la API (asistente, ERP). Uso: [COMPOSE_PROJECT_NAME=protys] reiniciar.sh [salida.txt]
set -uo pipefail
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
export PROTYS_POSTGRES_PORT=${PROTYS_POSTGRES_PORT:-15432} PROTYS_BACKEND_PORT=${PROTYS_BACKEND_PORT:-18080}
export PROTYS_FRONTEND_PORT=${PROTYS_FRONTEND_PORT:-18081} PROTYS_FUSEKI_PORT=${PROTYS_FUSEKI_PORT:-13030}
C=(docker compose -p "${COMPOSE_PROJECT_NAME:-protys}" -f "$REPO/docker/docker-compose.yml")
API=http://127.0.0.1:$PROTYS_BACKEND_PORT
{
echo "# Reinicio completo de la plataforma ($(git -C "$REPO" rev-parse --short HEAD), $(date -u +%FT%TZ))"
echo "\$ ${C[*]} stop protys-frontend protys-backend protys-fuseki protys-postgres"
"${C[@]}" stop protys-frontend protys-backend protys-fuseki protys-postgres 2>&1 | tail -1
echo "\$ ${C[*]} up -d --build --wait protys-frontend"
"${C[@]}" up -d --build --wait protys-frontend > /dev/null 2>&1; echo "exit=$?"
"${C[@]}" ps -a --format '{{.Service}} {{.Status}}' | grep '^protys'
echo "## protys-init (última línea, resumida)"
"${C[@]}" logs protys-init --no-log-prefix | tail -1 | python3 -c 'import json,sys; d=json.loads(sys.stdin.read()); print(json.dumps({k: d.get(k) for k in ("status","rule_activation_preserved","added_modules","added_rules","platform_graphs")}, indent=1, ensure_ascii=False))'
echo "## módulos registrados"
curl -s "$API/api/ontology/modules" | python3 -c 'import json,sys; [print(m["name"], m["namedGraph"]) for m in json.load(sys.stdin)]'
echo "## reglas activas"
curl -s "$API/api/alignment/rules/active" | python3 -c 'import json,sys; r=json.load(sys.stdin); print(len(r), " ".join(sorted(x["ruleId"] for x in r)))'
echo "## interfaz: HTTP $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PROTYS_FRONTEND_PORT/")"
} 2>&1 | tee "${1:-/dev/null}"
