#!/bin/sh
# Jerarquía de clases del catálogo integrado por la API del explorador. Uso, desde la raíz del repositorio:
#   sh research/integration/explorador/correr.sh
# Proyecto de Compose «protys-v110» (puertos 38080, 38081, 33030 y 35432); se recrea y al terminar se baja.
set -eu
D=research/integration/explorador
rm -rf "$D/salidas"; mkdir -p "$D/salidas"
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
C="docker compose -f docker/docker-compose.yml"
$C down -v > /dev/null 2>&1 || true
$C up -d --build > "$D/salidas/01-compose-up.txt" 2>&1
PROTYS_API=http://127.0.0.1:38080/api python3 "$D/jerarquia.py" "$D/salidas/api-classes.json" > "$D/salidas/jerarquia.txt"
$C down -v > /dev/null 2>&1
cat "$D/salidas/jerarquia.txt"
