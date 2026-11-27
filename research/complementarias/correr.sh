#!/bin/sh
# Pruebas complementarias sobre el ejecutor académico (research/runtime/validator), sin Docker ni la plataforma.
# Uso, desde la raíz del repositorio, con JAVA_HOME en un JDK 17 y los ejecutores preparados
# (./research/reproduce.sh --prepare-only):
#   sh research/complementarias/correr.sh [disyunciones] [consultas-qc] [ablacion] [curva]
# Sin argumentos corre las cuatro. La curva mide tiempos: conviene correrla sola, con la máquina sin otra carga.
set -eu
: "${JAVA_HOME:?JAVA_HOME debe apuntar a un JDK 17}"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17\.' || { echo "se requiere Java 17" >&2; exit 1; }
[ "$#" -eq 0 ] && set -- disyunciones consultas-qc ablacion curva
C=research/complementarias
TMP=$(mktemp -d "${TMPDIR:-/tmp}/complementarias.XXXXXX")
for t in "$@"; do
  case "$t" in
    disyunciones)
      rm -rf "$C/disyunciones/salidas"; mkdir -p "$C/disyunciones/salidas/pares" "$C/disyunciones/salidas/modulo"
      (cd "$C/disyunciones" && python3 disyunciones.py salidas/pares "$TMP/pares" > salidas/pares/disyunciones.log 2>&1)
      (cd "$C/disyunciones" && python3 modulo.py salidas/modulo "$TMP/modulo" > salidas/modulo/modulo.log 2>&1) ;;
    consultas-qc)
      rm -rf "$C/consultas-qc/salidas"; mkdir -p "$C/consultas-qc/salidas"
      (cd "$C/consultas-qc" && python3 definiciones.py > salidas/definiciones.log 2>&1 \
        && python3 tabla.py salidas "$TMP/qc" > salidas/tabla.log 2>&1) ;;
    ablacion)
      rm -rf "$C/ablacion-reglas/salidas"; mkdir -p "$C/ablacion-reglas/salidas"
      (cd "$C/ablacion-reglas" && python3 ablacion.py salidas "$TMP/ablacion" > salidas/ablacion.log 2>&1) ;;
    curva)
      rm -rf "$C/curva-costo/salidas" "$C/curva-costo/cargas"; mkdir -p "$C/curva-costo/salidas" "$C/curva-costo/cargas"
      (cd "$C/curva-costo" && python3 cargas.py > salidas/cargas.log 2>&1 \
        && python3 curva.py salidas "$TMP/curva" 30 > salidas/curva.log 2>&1) ;;
    *) echo "tarea desconocida: $t" >&2; exit 1 ;;
  esac
  echo "$t terminada $(date -u +%FT%TZ)"
done
rm -rf "$TMP"
