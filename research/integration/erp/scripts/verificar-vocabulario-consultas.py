#!/usr/bin/env python3
"""Comprueba que P1–P5 y la consulta conjunta usen sólo términos de la red declarados en ontologies/.

Lista, por consulta, cada término con prefijo bajo http://w3id.org/protys/ontology/ y el archivo
de ontologies/ que lo declara. Sale con código 1 si alguno no está declarado o si sólo lo declara
la extensión del caso de pintura (paint-case-extension.owl).

Uso: verificar-vocabulario-consultas.py
"""
import glob
import re
import sys
from pathlib import Path

import rdflib

RAIZ = Path(__file__).resolve().parents[4]
QUERIES = RAIZ / "research" / "integration" / "erp" / "queries"

declarado = {}
for f in sorted(glob.glob(str(RAIZ / "ontologies" / "*.owl"))):
    g = rdflib.Graph()
    g.parse(f, format="xml")
    for s in set(g.subjects()):
        declarado.setdefault(str(s), set()).add(Path(f).name)

falla = False
for f in sorted(QUERIES.glob("P*.rq")) + [QUERIES / "CRUZADA.rq"]:
    texto = f.read_text(encoding="utf-8")
    prefijos = dict(re.findall(r"PREFIX\s+(\w*):\s*<([^>]+)>", texto))
    cuerpo = re.sub(r"#[^\n]*", "", re.sub(r"PREFIX[^\n]*", "", texto))
    print(f"== {f.name}")
    for p, local in sorted(set(re.findall(r"\b(\w+):([A-Za-z_]\w*)", cuerpo))):
        iri = prefijos.get(p, "") + local
        if not iri.startswith("http://w3id.org/protys/ontology/"):
            continue
        archivos = declarado.get(iri, set())
        mal = not archivos or archivos == {"paint-case-extension.owl"}
        falla |= mal
        print(f"  {'FALLA' if mal else 'OK   '} {p}:{local:24} {', '.join(sorted(archivos)) or 'no declarado'}")
print("resultado:", "FALLADO" if falla else "APROBADO (sólo vocabulario de los módulos de ontologies/)")
sys.exit(1 if falla else 0)
