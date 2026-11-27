"""Diferencias exactas entre ontologies/ de v1.0.0 y de v1.1.0, triple por triple.

Se comparan los grafos (no el texto): se canonicalizan los nodos en blanco (rdflib.compare.to_canonical_graph) y se
listan los triples que están sólo en un lado. Cada triple se clasifica:
- importación: owl:imports;
- anotación: el predicado es una propiedad de anotación (rdfs:label, rdfs:comment, ... o declarada
  owl:AnnotationProperty) o el triple pertenece a la etiqueta/descripción de una regla; no tiene efecto lógico;
- lógico: cualquier otro (axiomas de clase, propiedad o individuo).
También lista los archivos que están sólo en una de las dos versiones, con su cantidad de triples.
uso: python3 diferencias.py <salida.json>
"""
import json, sys
import rdflib
from rdflib.compare import to_canonical_graph, graph_diff
from rdflib.namespace import RDF, RDFS, OWL

from versiones import ANTES, DESPUES, RAIZ, leer, listar
ANOTACION = {RDFS.label, RDFS.comment, RDFS.seeAlso, RDFS.isDefinedBy, OWL.versionInfo,
             rdflib.URIRef('http://purl.org/dc/terms/title'), rdflib.URIRef('http://purl.org/dc/elements/1.1/title'),
             rdflib.URIRef('http://www.w3.org/2004/02/skos/core#definition')}


def nt(t):
    return ' '.join(x.n3() for x in t)


def grafo(version, ruta):
    return rdflib.Graph().parse(data=leer(version, ruta), format='turtle' if ruta.endswith('.ttl') else 'xml')


def main(salida):
    antes_l, despues_l = set(listar(ANTES)), set(listar(DESPUES))
    comunes = sorted(antes_l & despues_l)
    cambiados = [r for r in comunes if leer(ANTES, r) != leer(DESPUES, r)]
    resultado = {'antes': ANTES, 'despues': DESPUES, 'archivos_en_ontologies': {ANTES: len(antes_l), DESPUES: len(despues_l)},
                 'solo_en_' + ANTES: [{'ruta': r, 'triples': len(grafo(ANTES, r))} for r in sorted(antes_l - despues_l) if r.endswith(('.owl', '.ttl'))],
                 'solo_en_' + DESPUES: [{'ruta': r, 'triples': len(grafo(DESPUES, r))} for r in sorted(despues_l - antes_l) if r.endswith(('.owl', '.ttl'))],
                 'archivos_distintos': cambiados, 'archivos': []}
    for ruta in cambiados:
        antes, despues = grafo(ANTES, ruta), grafo(DESPUES, ruta)
        anotaciones = ANOTACION | set(antes.subjects(RDF.type, OWL.AnnotationProperty)) | set(despues.subjects(RDF.type, OWL.AnnotationProperty))
        _, quitados, agregados = graph_diff(to_canonical_graph(antes), to_canonical_graph(despues))

        def clase(t):
            if t[1] == OWL.imports:
                return 'importación'
            if t[1] in anotaciones:
                return 'anotación'
            return 'lógico'
        filas = [{'cambio': c, 'tipo': clase(t), 'sujeto': str(t[0]), 'predicado': str(t[1]), 'objeto': str(t[2]), 'triple': nt(t)}
                 for c, g in (('quitado', quitados), ('agregado', agregados)) for t in sorted(g)]
        resultado['archivos'].append({'ruta': ruta, 'triples_antes': len(antes), 'triples_despues': len(despues),
                                      'quitados': len(quitados), 'agregados': len(agregados),
                                      'por_tipo': {k: sum(1 for f in filas if f['tipo'] == k) for k in ('lógico', 'importación', 'anotación')},
                                      'cambios': filas})
    json.dump(resultado, open(salida, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
    print(f"{len(cambiados)} archivos comunes de ontologies/ difieren entre {ANTES} y {DESPUES}")
    for k in ('solo_en_' + ANTES, 'solo_en_' + DESPUES):
        print(k, resultado[k])
    for a in resultado['archivos']:
        print(f"== {a['ruta']}: {a['triples_antes']} -> {a['triples_despues']} triples; {a['por_tipo']}")
        for f in a['cambios']:
            print(f"   {f['cambio']:8} [{f['tipo']}] {f['triple'][:260]}")


if __name__ == '__main__':
    main(sys.argv[1])
