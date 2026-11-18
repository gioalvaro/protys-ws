#!/usr/bin/env bash
# Sólo lectura de la conexión del conector ERP, contra un PostgreSQL desechable (nunca la base de un ERP).
# Levanta postgres:15 (mismo digest que la plataforma) en una red Docker propia, con un usuario que SÍ puede
# escribir sobre la tabla de prueba, y corre ERPReadOnlyConnectionIntegrationTest en Java 17.
# Al terminar borra el contenedor y la red. Requiere docker.
# Uso: research/integration/revision-2026-11-18/solo-lectura.sh [archivo-de-salida]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
PG_IMAGE=postgres:15@sha256:0dda651c259bfe50e2bcc28ca23d1fcca772fa90b0210803aa7b97379ccf4e85
JDK_IMAGE=eclipse-temurin:17-jdk@sha256:5465815640e10433422217335e15a523a3e57021a1f2ae0fc3849380d237979f
NET=protys-solo-lectura-$$
DB=protys-solo-lectura-db-$$
OUT="${1:-/dev/stdout}"

limpiar() { docker rm -f "$DB" > /dev/null 2>&1 || true; docker network rm "$NET" > /dev/null 2>&1 || true; }
trap limpiar EXIT

docker network create "$NET" > /dev/null
# Credenciales de demostración de una base que se borra al terminar.
docker run -d --name "$DB" --network "$NET" -e POSTGRES_USER=escritor -e POSTGRES_PASSWORD=escritor_demo \
  -e POSTGRES_DB=desechable "$PG_IMAGE" > /dev/null
for _ in $(seq 60); do docker exec "$DB" pg_isready -U escritor -d desechable > /dev/null 2>&1 && break; sleep 1; done
sleep 2

{
  echo "# solo-lectura.sh — $(date -u +%FT%TZ)"
  echo "# commit: $(git -C "$REPO" rev-parse --short HEAD)$(git -C "$REPO" diff --quiet HEAD -- backend || echo '+cambios-sin-commit')"
  echo "# PostgreSQL desechable: $PG_IMAGE; Java: $JDK_IMAGE"
  echo "# usuario 'escritor' = dueño de la base 'desechable' (puede escribir en la tabla prueba)"
  docker run --rm --network "$NET" -v "$REPO:$REPO" -v "$HOME/.m2:$HOME/.m2" -e HOME="$HOME" \
    -e PROTYS_TEST_PG_URL="jdbc:postgresql://$DB:5432/desechable" \
    -e PROTYS_TEST_PG_USER=escritor -e PROTYS_TEST_PG_PASSWORD=escritor_demo \
    -w "$REPO/backend" "$JDK_IMAGE" \
    ./mvnw -q -Dmaven.repo.local="$HOME/.m2/repository" test -Dtest=ERPReadOnlyConnectionIntegrationTest 2>&1 \
    | grep -E "PGJDBC|Tests run|FAIL|ERROR" || true
  echo "# surefire:"
  sed -n 4p "$REPO/backend/target/surefire-reports/org.protys.ws.service.ERPReadOnlyConnectionIntegrationTest.txt"
} | sed "s#$REPO#<repo>#g" | tee "$OUT"
