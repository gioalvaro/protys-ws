"""Respuesta esperada de P6 y P7 calculada leyendo el archivo de instancias directamente, sin SPARQL ni la red.

Recorre el grafo del archivo con rdflib (triples afirmados): plantas = sujetos con rdf:type paint:Plant; parte de =
cierre transitivo de enterprise:isPartOf ∪ paint:partOfOrganization; personas = rdf:type iso15531:HumanResource o
resource:Person; asignación = employs, employedBy o belongsTo a algo que es parte de la planta. Etiqueta = la menor
rdfs:label en orden de cadena. Escribe las filas como resultado SPARQL JSON, ordenadas.
uso: python3 esperadas.py <archivo-de-instancias.ttl> <carpeta-salida>
"""
import json, sys
from pathlib import Path
import rdflib
from rdflib.namespace import RDF, RDFS

N = 'http://w3id.org/protys/ontology/'
PLANT, ISPART, PARTORG = rdflib.URIRef(N + 'paint-ext#Plant'), rdflib.URIRef(N + 'enterprise#isPartOf'), rdflib.URIRef(N + 'paint-ext#partOfOrganization')
EMPLOYS, EMPLOYEDBY, BELONGS = (rdflib.URIRef(N + 'paint-ext#' + x) for x in ('employs', 'employedBy', 'belongsTo'))
PERSONAS = {rdflib.URIRef(N + 'iso15531#HumanResource'), rdflib.URIRef(N + 'resource#Person')}


def partes_de(g, planta):
    """Todo x tal que x (isPartOf|partOfOrganization)+ planta."""
    hallados, pendientes = set(), [planta]
    while pendientes:
        objetivo = pendientes.pop()
        for p in (ISPART, PARTORG):
            for x in g.subjects(p, objetivo):
                if x not in hallados:
                    hallados.add(x)
                    pendientes.append(x)
    return hallados


def etiqueta(g, x):
    labels = sorted(str(l) for l in g.objects(x, RDFS.label))
    return labels[0] if labels else None


def fila(vars_, valores):
    return {v: ({'type': 'uri', 'value': str(x)} if isinstance(x, rdflib.URIRef) else {'type': 'literal', 'value': x}) for v, x in zip(vars_, valores) if x is not None}


def main(archivo, salida):
    g = rdflib.Graph().parse(archivo, format='turtle')
    salida = Path(salida)
    salida.mkdir(parents=True, exist_ok=True)
    p6, p7 = [], []
    for planta in sorted(g.subjects(RDF.type, PLANT)):
        partes = partes_de(g, planta)
        for inst in sorted(partes):
            p6.append(fila(['planta', 'instalacion', 'etiqueta'], [planta, inst, etiqueta(g, inst)]))
        personas = {x for x in g.subjects(RDF.type, None) if set(g.objects(x, RDF.type)) & PERSONAS}
        asignadas = {x for x in personas if (planta, EMPLOYS, x) in g or (x, EMPLOYEDBY, planta) in g
                     or any(o in partes for o in g.objects(x, BELONGS))}
        for persona in sorted(asignadas):
            p7.append(fila(['planta', 'persona', 'etiqueta'], [planta, persona, etiqueta(g, persona)]))
    for nombre, vars_, filas in (('P6', ['planta', 'instalacion', 'etiqueta'], p6), ('P7', ['planta', 'persona', 'etiqueta'], p7)):
        (salida / f'{nombre}.json').write_text(json.dumps({'head': {'vars': vars_}, 'results': {'bindings': filas}}, indent=2, ensure_ascii=False) + '\n')
        print(nombre, len(filas))
        for f in filas:
            print('  ', {k: v['value'].replace(N, '') for k, v in f.items()})


if __name__ == '__main__':
    main(*sys.argv[1:3])
