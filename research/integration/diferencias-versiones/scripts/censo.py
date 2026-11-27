"""Clases y propiedades declaradas por módulo, en v1.0.0 y en v1.1.0.

Mismo método que research/model/census.json: declaraciones OWL explícitas (owl:Class, owl:ObjectProperty,
owl:DatatypeProperty) con IRI, deduplicadas por IRI completo. Los «base eight modules» son los ocho componentes con
ese alcance en census.json; la tesis informa 87 clases y 119 propiedades (81 de objeto + 38 de datos).
uso: python3 c5_censo.py <salida.json>
"""
import json, subprocess, sys
from pathlib import Path
import rdflib
from rdflib import URIRef
from rdflib.namespace import RDF, OWL

sys.path.insert(0, str(Path(__file__).resolve().parent))
from versiones import ANTES, DESPUES, RAIZ, leer  # noqa: E402
TIPOS = {'Class': OWL.Class, 'ObjectProperty': OWL.ObjectProperty, 'DatatypeProperty': OWL.DatatypeProperty}


def declarados(ref, ruta):
    datos = leer(ref, ruta)
    g = rdflib.Graph().parse(data=datos, format='turtle' if ruta.endswith('.ttl') else 'xml')
    return {k: {s for s in g.subjects(RDF.type, t) if isinstance(s, URIRef)} for k, t in TIPOS.items()}


def main(salida):
    censo = json.load(open(RAIZ / 'research/model/census.json', encoding='utf-8'))
    componentes = [(c['path'], c['scope']) for c in censo['components']]
    resultado = {'metodo': 'declaraciones OWL explícitas con IRI, deduplicadas por IRI completo (como census.json)', 'versiones': {}}
    for nombre, ref in ((ANTES, ANTES), (DESPUES, DESPUES)):
        por_modulo, union_base, union_total = [], {k: set() for k in TIPOS}, {k: set() for k in TIPOS}
        for ruta, alcance in componentes:
            d = declarados(ref, ruta)
            por_modulo.append({'modulo': ruta, 'alcance': alcance, **{k: len(v) for k, v in d.items()}})
            for k in TIPOS:
                union_total[k] |= d[k]
                if alcance == 'base eight modules':
                    union_base[k] |= d[k]
        base = {k: len(v) for k, v in union_base.items()}
        resultado['versiones'][nombre] = {'por_modulo': por_modulo, 'base_ocho_unicos': base,
                                          'base_ocho_propiedades': base['ObjectProperty'] + base['DatatypeProperty'],
                                          'union_completa': {k: len(v) for k, v in union_total.items()}}
    resultado['census_json'] = {'base_eight_unique_counts': censo['base_eight_unique_counts'], 'complete_explicit_union_counts': censo['complete_explicit_union_counts']}
    a, b = resultado['versiones'][ANTES], resultado['versiones'][DESPUES]
    resultado['por_modulo_iguales'] = a['por_modulo'] == b['por_modulo']
    json.dump(resultado, open(salida, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
    print(f"{'módulo':48} {'clases':>14} {'p. objeto':>14} {'p. datos':>14}")
    for x, y in zip(a['por_modulo'], b['por_modulo']):
        print(f"{x['modulo']:48} {x['Class']:>6} -> {y['Class']:<5} {x['ObjectProperty']:>6} -> {y['ObjectProperty']:<5} {x['DatatypeProperty']:>6} -> {y['DatatypeProperty']:<5}")
    print('base ocho (únicos):', ANTES, a['base_ocho_unicos'], a['base_ocho_propiedades'], 'propiedades |', DESPUES, b['base_ocho_unicos'], b['base_ocho_propiedades'], 'propiedades')
    print('census.json:', resultado['census_json'])


if __name__ == '__main__':
    main(sys.argv[1])
