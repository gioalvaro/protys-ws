#!/usr/bin/env python3
"""Verifica que cada clase y propiedad usada en las correspondencias ERP exista en ontologies/.

Lee los términos de la tabla de correspondencias (columna «Término PROTYS(KB)» de
correspondencias.md), expande los prefijos de la red y comprueba que cada IRI esté
declarado en algún .owl de ontologies/ con el tipo esperado (owl:Class,
owl:ObjectProperty, owl:DatatypeProperty). También informa dominio y rango declarados.
Sale con código 1 si falta algún término.

Uso: verificar-vocabulario.py [ruta/correspondencias.md]
"""
import glob
import re
import sys
from pathlib import Path

import rdflib
from rdflib.namespace import OWL, RDF, RDFS

RAIZ = Path(__file__).resolve().parents[4]
BASE = "http://w3id.org/protys/ontology/"
PREFIJOS = {
    "c": BASE + "core#",
    "r": BASE + "resource#",
    "prod": BASE + "product#",
    "proc": BASE + "process#",
    "i": BASE + "iso15531#",
    "e": BASE + "iso14040#",
    "a": BASE + "alignment#",
    "rdfs": str(RDFS),
}
TIPOS = {OWL.Class: "clase", OWL.ObjectProperty: "prop. objeto", OWL.DatatypeProperty: "prop. datos"}
# rdfs:label no se declara en la red: es vocabulario RDF Schema estándar.
ESTANDAR = {str(RDFS.label)}


def abreviar(iri):
    for p, ns in PREFIJOS.items():
        if str(iri).startswith(ns):
            return f"{p}:{str(iri)[len(ns):]}"
    return "[anónimo]" if isinstance(iri, rdflib.BNode) else str(iri)


def terminos(md_path):
    """Extrae los términos con prefijo (p:Nombre) de las celdas marcadas entre backticks."""
    texto = Path(md_path).read_text(encoding="utf-8")
    patron = re.compile(r"`((?:%s):[A-Za-z_][A-Za-z0-9_]*)`" % "|".join(PREFIJOS))
    return sorted(set(patron.findall(texto)))


def main():
    md = sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "correspondencias.md"
    g = rdflib.Graph()
    archivos = sorted(glob.glob(str(RAIZ / "ontologies" / "*.owl")))
    for f in archivos:
        g.parse(f, format="xml")
    print(f"ontologías cargadas: {len(archivos)} archivos, {len(g)} triples")

    faltan = 0
    for t in terminos(md):
        p, local = t.split(":", 1)
        iri = rdflib.URIRef(PREFIJOS[p] + local)
        if str(iri) in ESTANDAR:
            print(f"OK      {t:32} estándar RDFS")
            continue
        tipos = [TIPOS[x] for x in g.objects(iri, RDF.type) if x in TIPOS]
        if not tipos:
            print(f"FALTA   {t:32} no declarado en ontologies/")
            faltan += 1
            continue
        dom = ",".join(abreviar(x) for x in g.objects(iri, RDFS.domain)) or "-"
        ran = ",".join(abreviar(x) for x in g.objects(iri, RDFS.range)) or "-"
        print(f"OK      {t:32} {'/'.join(tipos):14} dominio={dom} rango={ran}")
    print(f"resultado: {'APROBADO' if faltan == 0 else 'FALLADO'} ({faltan} términos faltantes)")
    sys.exit(1 if faltan else 0)


if __name__ == "__main__":
    main()
