#!/bin/sh
# Incorporación del piloto ISO 10303 (fragmento AP242, datos y reglas R27–R29) con el asistente, por su API, y
# reinicio de la plataforma para comprobar que lo incorporado se conserva. Las capturas del mismo recorrido por la
# interfaz están en research/integration/capturas-interfaz/ (serie B).
# Requiere la fase funcional de la evaluación (research/evaluation/current/assertion-groups), con la que se comparan
# los controles H3_*. Uso, desde la raíz del repositorio:  sh research/integration/asistente/correr.sh
# Proyecto de Compose «protys-v110» (puertos 38080, 38081, 33030 y 35432); se recrea y al terminar se baja.
set -eu
D=research/integration/asistente
S="$D/salidas"; rm -rf "$S"; mkdir -p "$S"
export COMPOSE_PROJECT_NAME=protys-v110 PROTYS_BACKEND_PORT=38080 PROTYS_FRONTEND_PORT=38081 PROTYS_FUSEKI_PORT=33030 PROTYS_POSTGRES_PORT=35432
C="docker compose -f docker/docker-compose.yml"
$C down -v > /dev/null 2>&1 || true
$C up -d --build > "$S/01-compose-up.txt" 2>&1
for c in protys-backend protys-fuseki; do docker inspect -f "imagen[$c]={{.Config.Image}} id={{.Image}}" "protys-v110-$c-1" >> "$S/00-versiones.txt"; done
OUT="$(pwd)/$S/api" API=http://127.0.0.1:38080/api FUSEKI=http://127.0.0.1:33030 bash "$D/incorporar.sh" > "$S/02-incorporar.txt" 2>&1 || true
bash "$D/reiniciar.sh" "$S/03-reinicio.txt" > /dev/null 2>&1 || true
$C down -v > /dev/null 2>&1
tail -15 "$S/api/RESUMEN.txt"
