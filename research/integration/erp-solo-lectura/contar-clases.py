"""Clases OWL nombradas: los ocho módulos de ontologies/ frente al catálogo integrado (research/catalog.json).
Uso: python3 research/integration/erp-solo-lectura/contar-clases.py  (desde la raíz del repositorio)"""
import json
from rdflib import Graph, RDF, OWL, URIRef

MODULOS = [f"ontologies/{m}.owl" for m in ("core-concepts", "product-module", "process-module", "resource-module",
                                             "enterprise-module", "iso15531-module", "iso14040-module", "alignment-rules")]


def nombradas(path):
    g = Graph()
    g.parse(path, format="turtle" if path.endswith(".ttl") else "xml")
    return {s for s in g.subjects(RDF.type, OWL.Class) if isinstance(s, URIRef)}


integrada = next(c for c in json.load(open("research/catalog.json"))["configurations"] if c["id"] == "integrated")
base = set()
for p in MODULOS:
    clases = nombradas(p)
    print(f"{p}: {len(clases)}")
    base |= clases
print(f"ocho módulos de ontologies/: {len(base)} clases nombradas distintas")
total = set(base)
for p in integrada["tbox_paths"] + integrada["abox_paths"] + integrada["rules_paths"]:
    if p in MODULOS:
        continue
    nuevas = nombradas(p) - total
    total |= nuevas
    print(f"+ {p}: {len(nuevas)}")
print(f"catálogo integrado: {len(total)} clases nombradas distintas")
