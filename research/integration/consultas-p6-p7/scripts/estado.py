"""Foto del estado de la plataforma: conteos, grafos, plantillas y respuestas de las 21 consultas del catálogo.

uso: python3 estado.py <archivo-salida.json>
"""
import hashlib, json, sys, urllib.request
import rdflib
from rdflib.compare import to_canonical_graph
from comun import API, FUSEKI, GRAFO_CANONICO, RAIZ, enc, http, sparql, guardar


def huella_grafo(grafo):
    # Lectura del grafo por el protocolo de grafos de Fuseki (sólo GET) y huella independiente de los nodos en blanco.
    req = urllib.request.Request(f'{FUSEKI}/get?graph={enc(grafo)}', headers={'Accept': 'application/n-triples'})
    with urllib.request.urlopen(req, timeout=120) as r:
        g = rdflib.Graph().parse(data=r.read(), format='nt')
    nt = sorted(to_canonical_graph(g).serialize(format='nt').splitlines())
    return len(g), hashlib.sha256('\n'.join(nt).encode()).hexdigest()


def main(salida):
    estado = {}
    _, t = http('GET', '/dashboard'); d = json.loads(t)
    estado['tablero'] = {k: d.get(k) for k in ('totalModules', 'totalClasses', 'totalIndividuals', 'totalTriples', 'activeAlignmentRules', 'connectedERPs')}
    _, t = http('GET', '/ontology/modules')
    estado['modulos'] = sorted([(m['name'], m['tripleCount']) for m in json.loads(t)])
    _, t = http('GET', '/alignment/rules/active'); estado['reglas_activas'] = len(json.loads(t))
    _, t = http('GET', '/sparql/templates'); estado['plantillas'] = len(json.loads(t))
    _, t = http('GET', '/sparql/templates/competency'); comp = json.loads(t)
    estado['plantillas_competencia'] = sorted(q['name'].split(' ')[0] for q in comp)
    estado['grafos'] = {f['g']: int(f['n']) for f in sparql('SELECT ?g (COUNT(*) AS ?n) WHERE { GRAPH ?g { ?s ?p ?o } } GROUP BY ?g')}
    n, h = huella_grafo(GRAFO_CANONICO)
    estado['grafo_canonico'] = {'triples': n, 'sha256_canonico': h}
    estado['individuos_instances'] = int(sparql('SELECT (COUNT(DISTINCT ?s) AS ?n) WHERE { GRAPH ?g { ?s ?p ?o FILTER(STRSTARTS(STR(?s), "http://w3id.org/protys/instances/")) } }')[0]['n'])
    catalogo = json.load(open(f'{RAIZ}/research/catalog.json', encoding='utf-8'))
    estado['catalogo'] = {}
    for q in catalogo['queries']:
        filas = sparql(open(f"{RAIZ}/{q['path']}", encoding='utf-8').read())
        canon = sorted(json.dumps(f, sort_keys=True, ensure_ascii=False) for f in filas)
        estado['catalogo'][q['id']] = {'filas': len(filas), 'sha256': hashlib.sha256('\n'.join(canon).encode()).hexdigest(), 'respuesta': [json.loads(c) for c in canon]}
    guardar(salida, estado)
    print(json.dumps({k: v for k, v in estado.items() if k != 'catalogo'}, ensure_ascii=False))
    print('catálogo:', {k: v['filas'] for k, v in estado['catalogo'].items()})


if __name__ == '__main__':
    main(sys.argv[1])
