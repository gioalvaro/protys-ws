#!/usr/bin/env python3
"""Verifica la cadena R07 → R24 sobre una corrida real del razonador de la plataforma.

Uso: verificar-brecha.py <carpeta_de_la_corrida>

La carpeta es la copia que el backend deja con protys.reasoning.evidence-dir:
  request.json                 reglas habilitadas que el backend pidió
  out/raw-input.owl            entrada que armó el backend (red + reglas + grafos ERP)
  out/prepared-input.ttl       la misma entrada después de las construcciones previas (R07, CNUM)
  out/swrl/deductions.owl      conclusiones de las reglas SWRL

Comprueba, y sale con código 1 si algo no se cumple:
  1. La regla R24 está habilitada y su definición SWRL en la entrada es
     c:MissingEnvironmentalData(?p) → c:CoverageGap(?p).
  2. c:MissingEnvironmentalData no está afirmada en la entrada cruda: la agrega la preparación.
  3. En la entrada preparada, todos los procesos ERP (i:ManufacturingProcess bajo
     http://w3id.org/protys/erp/) tienen c:MissingEnvironmentalData.
  4. Las deducciones SWRL clasifican c:CoverageGap exactamente a los procesos que tienen
     c:MissingEnvironmentalData en la entrada preparada (ERP y del escenario, si los hubiera).
  5. Prueba negativa: hay al menos un proceso del escenario de pintura con representación de
     inventario e indicador (lo que R07 exige), y ninguno de ellos queda como
     c:MissingEnvironmentalData ni como c:CoverageGap.
"""
import json
import sys
from pathlib import Path

import rdflib
from rdflib.namespace import RDF

C = rdflib.Namespace("http://w3id.org/protys/ontology/core#")
I = rdflib.Namespace("http://w3id.org/protys/ontology/iso15531#")
SWRL = rdflib.Namespace("http://www.w3.org/2003/11/swrl#")
ERP = "http://w3id.org/protys/erp/"

CON_INVENTARIO = """
PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
SELECT DISTINCT ?process ?lci ?indicator ?value WHERE {
  ?process a i:ManufacturingProcess ; t:hasInventoryRepresentation ?lci .
  ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:indicatorValue ?value .
} ORDER BY ?process ?indicator"""

REGLA = """
PREFIX swrl: <http://www.w3.org/2003/11/swrl#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
SELECT ?imp ?cuerpo ?cabeza WHERE {
  ?imp a swrl:Imp ; swrl:body ?b ; swrl:head ?h .
  ?b rdf:first/swrl:classPredicate ?cuerpo ; rdf:rest rdf:nil .
  ?h rdf:first/swrl:classPredicate ?cabeza ; rdf:rest rdf:nil .
  FILTER(?cabeza = <http://w3id.org/protys/ontology/core#CoverageGap>)
}"""


def cargar(path, fmt):
    g = rdflib.Graph()
    g.parse(str(path), format=fmt)
    return g


def corto(iri):
    s = str(iri)
    return "erp:" + s[len(ERP):] if s.startswith(ERP) else s.rsplit("/", 1)[-1]


def main():
    run = Path(sys.argv[1])
    ok = True

    def chequeo(nombre, cumple, detalle=""):
        nonlocal ok
        ok &= bool(cumple)
        print(f"[{'OK' if cumple else 'FALLA'}] {nombre}" + (f" — {detalle}" if detalle else ""))

    request = json.loads((run / "request.json").read_text(encoding="utf-8"))
    reglas = str(request.get("enabled_rules", "")).split(",")
    cruda = cargar(run / "out" / "raw-input.owl", "xml")
    preparada = cargar(run / "out" / "prepared-input.ttl", "turtle")
    deducciones = cargar(run / "out" / "swrl" / "deductions.owl", "xml")
    print(f"entrada cruda: {len(cruda)} triples; preparada: {len(preparada)} (+{len(preparada) - len(cruda)}); "
          f"deducciones SWRL: {len(deducciones)}")
    print(f"construcciones previas pedidas: {request.get('pre_inference_constructs')}")

    # 1. R24 habilitada y con la forma esperada.
    filas = list(cruda.query(REGLA))
    forma = [(corto(r.cuerpo), corto(r.cabeza)) for r in filas]
    chequeo("1 R24 habilitada", "R24" in reglas, f"{len(reglas)} reglas habilitadas")
    chequeo("1 regla SWRL MissingEnvironmentalData(?p) → CoverageGap(?p) en la entrada",
            any(r.cuerpo == C.MissingEnvironmentalData for r in filas), f"reglas con cabeza CoverageGap: {forma}")

    # 2. La clase la agrega la preparación, no está afirmada.
    afirmada = set(cruda.subjects(RDF.type, C.MissingEnvironmentalData))
    chequeo("2 MissingEnvironmentalData no afirmada en la entrada cruda", not afirmada,
            f"{len(afirmada)} individuos afirmados")

    # 3. Procesos ERP con MissingEnvironmentalData en la entrada preparada.
    procesos_erp = sorted(p for p in preparada.subjects(RDF.type, I.ManufacturingProcess) if str(p).startswith(ERP))
    med = set(preparada.subjects(RDF.type, C.MissingEnvironmentalData))
    med_erp = sorted(p for p in med if str(p).startswith(ERP))
    print("\nProcesos ERP (i:ManufacturingProcess) en la entrada preparada:")
    for p in procesos_erp:
        nombre = next(preparada.objects(p, rdflib.RDFS.label), "")
        print(f"  {corto(p):40} {nombre!s:45} MissingEnvironmentalData={'sí' if p in med else 'no'}")
    chequeo("3 todos los procesos ERP tienen MissingEnvironmentalData en la entrada preparada",
            procesos_erp and set(procesos_erp) <= med, f"{len(med_erp)} de {len(procesos_erp)}")

    # 4. CoverageGap = MissingEnvironmentalData.
    brecha = set(deducciones.subjects(RDF.type, C.CoverageGap))
    print(f"\nc:MissingEnvironmentalData en la entrada preparada: {len(med)} ({len(med_erp)} ERP)")
    print(f"c:CoverageGap en las deducciones SWRL: {len(brecha)}")
    for p in sorted(med | brecha):
        print(f"  {corto(p):40} Missing={'sí' if p in med else 'no'} CoverageGap={'sí' if p in brecha else 'no'}")
    chequeo("4 CoverageGap deducida exactamente para los procesos con MissingEnvironmentalData",
            brecha == med and med, f"{len(brecha)} = {len(med)}")

    # 5. Prueba negativa.
    con_inv = list(preparada.query(CON_INVENTARIO))
    procesos_con_inv = sorted({r.process for r in con_inv})
    print("\nProcesos con representación de inventario e indicador (escenario):")
    for r in con_inv:
        print(f"  {corto(r.process):40} inventario={corto(r.lci)} indicador={corto(r.indicator)} valor={r.value}")
    falsos = [p for p in procesos_con_inv if p in med or p in brecha]
    chequeo("5 prueba negativa: procesos con inventario no quedan como brecha",
            procesos_con_inv and not falsos,
            f"{len(procesos_con_inv)} procesos con inventario; marcados por error: {[corto(p) for p in falsos]}")

    print(f"\nresultado: {'CADENA CONFIRMADA' if ok else 'CADENA NO CONFIRMADA'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
