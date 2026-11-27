"""Controles del caso al agregar el módulo de instancias (equivalentes a los del piloto H3: identidad de los
individuos del caso y sin individuos nuevos fuera de los del archivo).

Lee por el protocolo de grafos de Fuseki (sólo GET) el grafo del caso y el de inferencias, y compara dos estados:
sin y con el módulo, los dos razonados.
- K1 el grafo del caso (Canonical_Catalog_Integrated) es el mismo (huella canónica);
- K2 los triples inferidos cuyo sujeto es un individuo o término del caso son los mismos;
- K3 no hay owl:sameAs inferido ni afirmado que involucre a un individuo del caso;
- K4 los individuos con nombre de la red (afirmados e inferidos) son los del caso más los del archivo, sin otros.
uso: python3 controles.py guardar <prefijo-salida>        # guarda los grafos del estado actual
     python3 controles.py comparar <prefijo-sin> <prefijo-con> <archivo-instancias> <salida.json>
"""
import json, sys, urllib.parse, urllib.request
import rdflib
from rdflib.compare import to_canonical_graph
from rdflib.namespace import RDF, OWL
from comun import FUSEKI, GRAFO_CANONICO

INFERENCIAS = 'http://w3id.org/protys/ontology/inference-results'


def leer(grafo):
    req = urllib.request.Request(f'{FUSEKI}/get?graph={urllib.parse.quote(grafo, safe="")}', headers={'Accept': 'application/n-triples'})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return rdflib.Graph().parse(data=r.read(), format='nt')
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return rdflib.Graph()
        raise


def guardar_estado(prefijo):
    for nombre, grafo in (('caso', GRAFO_CANONICO), ('inferencias', INFERENCIAS)):
        g = to_canonical_graph(leer(grafo))
        open(f'{prefijo}-{nombre}.nt', 'w', encoding='utf-8').write('\n'.join(sorted(g.serialize(format='nt').splitlines())) + '\n')


def comparar(sin, con, instancias, salida):
    # Las comparaciones K1 y K2 se hacen sobre las líneas N-Triples canónicas (texto): al releer un archivo, rdflib
    # renombra los nodos en blanco, y un conjunto de triples con nodos en blanco no se puede comparar entre lecturas.
    lineas = lambda f: {l for l in open(f, encoding='utf-8').read().splitlines() if l.strip()}
    cargar = lambda f: rdflib.Graph().parse(f, format='nt')
    caso_sin, caso_con = cargar(f'{sin}-caso.nt'), cargar(f'{con}-caso.nt')
    inf_sin, inf_con = cargar(f'{sin}-inferencias.nt'), cargar(f'{con}-inferencias.nt')
    archivo = rdflib.Graph().parse(instancias, format='turtle')
    todo = caso_con + inf_con + archivo
    esquema = {s for s in todo.subjects(RDF.type, None)
               if set(todo.objects(s, RDF.type)) & {OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty, OWL.AnnotationProperty, OWL.Ontology, RDF.Property}}

    def individuos(g):
        # Individuo con nombre: sujeto IRI con algún rdf:type, que no es un término del esquema.
        return {s for s in g.subjects(RDF.type, None) if isinstance(s, rdflib.URIRef) and s not in esquema}
    del_caso = {s for s in caso_sin.subjects() if isinstance(s, rdflib.URIRef)}
    sujetos_caso = {s.n3() for s in del_caso}
    sobre_caso = lambda ls: {l for l in ls if l.split(' ', 1)[0] in sujetos_caso}
    k2_sin, k2_con = sobre_caso(lineas(f'{sin}-inferencias.nt')), sobre_caso(lineas(f'{con}-inferencias.nt'))
    same = [t for g in (caso_con, inf_con) for t in g.triples((None, OWL.sameAs, None)) if t[0] in del_caso or t[2] in del_caso]
    ind_caso, ind_archivo, ind_red = individuos(caso_sin), individuos(archivo), individuos(todo)
    ajenos = sorted(str(x) for x in ind_red - ind_caso - ind_archivo)
    r = {'K1_grafo_del_caso_igual': lineas(f'{sin}-caso.nt') == lineas(f'{con}-caso.nt'),
         'K2_inferidos_sobre_el_caso': {'sin_modulo': len(k2_sin), 'con_modulo': len(k2_con), 'iguales': k2_sin == k2_con,
                                        'agregados': sorted(k2_con - k2_sin)[:50], 'quitados': sorted(k2_sin - k2_con)[:50]},
         'K3_sameAs_con_individuos_del_caso': [' '.join(x.n3() for x in t) for t in same],
         'K4_individuos': {'del_caso': len(ind_caso), 'del_archivo': len(ind_archivo), 'en_la_red_con_el_modulo': len(ind_red),
                           'ajenos_a_caso_y_archivo': ajenos},
         'inferidos_totales': {'sin_modulo': len(inf_sin), 'con_modulo': len(inf_con)}}
    r['todos_pasan'] = r['K1_grafo_del_caso_igual'] and r['K2_inferidos_sobre_el_caso']['iguales'] and not same and not ajenos
    json.dump(r, open(salida, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
    print(json.dumps({k: (v if not isinstance(v, dict) else {a: b for a, b in v.items() if not isinstance(b, list) or len(b) < 6}) for k, v in r.items()}, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    if sys.argv[1] == 'guardar':
        guardar_estado(sys.argv[2])
    else:
        comparar(*sys.argv[2:6])
