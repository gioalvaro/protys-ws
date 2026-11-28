"""Respuesta de /classes en v1.1.0 (evidencia E-13, salidas/api-classes.json) frente a la de esta versión.

Comprueba que el árbol de clases sea el mismo (las mismas clases en las mismas posiciones, 128 clases, 65 raíces y la
misma profundidad, contada por nodos y por aristas), lista los campos que cambian y, para cada clase cuyo propertyCount
cambia, qué propiedades entran o salen. La causa se calcula con rdflib sobre los archivos de la configuración
"integrated" de research/catalog.json: en v1.1.0 propertyCount contaba los sujetos con rdfs:domain igual a la clase;
ahora cuenta las propiedades de objeto y de datos cuyo dominio declarado incluye la clase (también por una unión).
uso: python3 comparar-clases.py <api-classes.json de v1.1.0> <api-classes de esta versión> <salida.json>
"""
import json, sys
from rdflib import Graph, OWL, RDF, RDFS, URIRef


def nodos(raices, d=1, camino=()):
    for n in raices:
        yield n, d, camino
        yield from nodos(n.get('subclasses') or [], d + 1, camino + (n['uri'],))


def forma(raices):
    return [(n['uri'], d, camino) for n, d, camino in nodos(raices)]


def resumen(raices):
    lista = list(nodos(raices))
    prof = max((d for _, d, _ in lista), default=0)
    return {'raices': len(raices), 'clases_distintas': len({n['uri'] for n, _, _ in lista}), 'nodos_del_arbol': len(lista),
            'profundidad_maxima_por_nodos': prof, 'profundidad_maxima_por_aristas': prof - 1 if prof else 0}


def corto(u):
    return str(u).split('#')[-1]


def main(viejo_path, nuevo_path, salida):
    viejo = json.load(open(viejo_path, encoding='utf-8'))['respuesta']
    nuevo = json.load(open(nuevo_path, encoding='utf-8'))['respuesta']
    r_viejo, r_nuevo = resumen(viejo), resumen(nuevo)
    misma_forma = forma(viejo) == forma(nuevo)
    campos_viejo = sorted({k for n, _, _ in nodos(viejo) for k in n})
    campos_nuevo = sorted({k for n, _, _ in nodos(nuevo) for k in n})
    otros_cambios = sorted({k for (a, _, _), (b, _, _) in zip(nodos(viejo), nodos(nuevo))
                            for k in set(a) | set(b) if k not in ('propertyCount', 'objectProperties', 'dataProperties', 'subclasses')
                            and a.get(k) != b.get(k)}) if misma_forma else None
    # objectProperties y dataProperties ya estaban en v1.1.0, siempre en null; ahora son listas.
    llenados = {k: {'null_en_v110': sum(1 for n, _, _ in nodos(viejo) if n.get(k) is None),
                    'lista_en_v111': sum(1 for n, _, _ in nodos(nuevo) if isinstance(n.get(k), list)),
                    'no_vacia_en_v111': sum(1 for n, _, _ in nodos(nuevo) if n.get(k))}
                for k in ('objectProperties', 'dataProperties')}

    cat = json.load(open('research/catalog.json', encoding='utf-8'))
    c = next(c for c in cat['configurations'] if c['id'] == 'integrated')
    g = Graph()
    for p in dict.fromkeys(c['tbox_paths'] + c['abox_paths'] + c['rules_paths']):
        g.parse(p, format='turtle' if p.endswith('.ttl') else 'xml')
    declaradas = {s for t in (OWL.ObjectProperty, OWL.DatatypeProperty) for s in g.subjects(RDF.type, t)}

    cambios, vistos = [], set()
    for (a, _, _), (b, _, _) in zip(nodos(viejo), nodos(nuevo)):
        if a['uri'] in vistos or a.get('propertyCount') == b.get('propertyCount'):
            continue
        vistos.add(a['uri'])
        antes = {s for s in g.subjects(RDFS.domain, URIRef(a['uri']))}
        ahora = {URIRef(p['uri']) for p in (b.get('objectProperties') or []) + (b.get('dataProperties') or [])}
        entran = sorted(ahora - antes)
        salen = sorted(antes - ahora)
        cambios.append({
            'clase': a['uri'], 'propertyCount_v110': a.get('propertyCount'), 'propertyCount_v111': b.get('propertyCount'),
            'calculado_como_en_v110_con_rdflib': len(antes),
            'entran': [{'propiedad': str(p), 'causa': 'la clase forma parte de una unión declarada como dominio'} for p in entran],
            'salen': [{'propiedad': str(p), 'causa': 'tiene rdfs:domain igual a la clase pero no está declarada como propiedad de objeto ni de datos'
                       if p not in declaradas else 'otra'} for p in salen]})

    datos = {'v110': r_viejo, 'v111': r_nuevo, 'mismo_arbol': misma_forma, 'campos_v110': campos_viejo,
             'campos_v111': campos_nuevo, 'campos_nuevos': sorted(set(campos_nuevo) - set(campos_viejo)),
             'otros_campos_que_cambian': otros_cambios, 'campos_que_pasan_de_null_a_lista': llenados, 'clases_con_propertyCount_distinto': cambios}
    json.dump(datos, open(salida, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

    print(f'v1.1.0 (E-13): {r_viejo}')
    print(f'esta versión:  {r_nuevo}')
    print(f'Mismo árbol (mismas clases en las mismas posiciones y el mismo orden): {"sí" if misma_forma else "NO"}')
    print(f'Campos nuevos en cada clase: {", ".join(datos["campos_nuevos"]) or "ninguno"}; '
          f'otros campos que cambian además de propertyCount, objectProperties y dataProperties (sin contar los hijos): {", ".join(otros_cambios) if otros_cambios else "ninguno"}')
    for k, v in llenados.items():
        print(f'{k}: null en {v["null_en_v110"]} de {r_viejo["nodos_del_arbol"]} nodos en v1.1.0; lista en {v["lista_en_v111"]} '
              f'en esta versión, no vacía en {v["no_vacia_en_v111"]}')
    print(f'Clases con propertyCount distinto: {len(cambios)}')
    for ch in cambios:
        print(f'  {corto(ch["clase"])}: {ch["propertyCount_v110"]} -> {ch["propertyCount_v111"]}'
              + ''.join(f'; entra {corto(e["propiedad"])} ({e["causa"]})' for e in ch['entran'])
              + ''.join(f'; sale {corto(s["propiedad"])} ({s["causa"]})' for s in ch['salen']))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
