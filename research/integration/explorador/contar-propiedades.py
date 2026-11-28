"""Conteo independiente de propiedades con rdflib, sin pasar por la plataforma, y comparación con el servicio.

Lee la lista de archivos de la configuración "integrated" de research/catalog.json (la misma que carga
docker/load_canonical.py en el módulo Canonical_Catalog_Integrated) y cuenta, sólo con lo declarado (sin inferencia):
  A. los ocho módulos base de ontologies/, uno por uno, frente a la Tabla 3.11 de la tesis;
  B. el conjunto "integrated", archivo por archivo, con lo que agrega cada uno;
  C. lo que agregan los archivos de la configuración "h3_extended" que no están en "integrated" (piloto ISO 10303 y
     datos de prueba), para explicar el total de la tesis con el piloto;
  D. propiedades con una característica declarada (owl:FunctionalProperty, etc.) que no están declaradas como
     owl:ObjectProperty ni owl:DatatypeProperty, y predicados usados sin declarar;
  E. raíces y profundidad de los dos árboles con la misma regla que el servicio, y la comparación con la respuesta
     del servicio (api-properties.json), propiedad por propiedad.
uso: python3 contar-propiedades.py <api-properties.json> <salida.json>   (desde la raíz del repositorio)
"""
import json, sys
from rdflib import Graph, Namespace, OWL, RDF, RDFS, URIRef

CATALOGO = 'research/catalog.json'
# Los ocho módulos base de la Tabla 3.11, en su orden.
BASE = [('Núcleo', 'ontologies/core-concepts.owl'), ('Producto', 'ontologies/product-module.owl'),
        ('Proceso', 'ontologies/process-module.owl'), ('Recursos', 'ontologies/resource-module.owl'),
        ('Empresa', 'ontologies/enterprise-module.owl'), ('ISO15531', 'ontologies/iso15531-module.owl'),
        ('ISO14040', 'ontologies/iso14040-module.owl'), ('Alineamiento', 'ontologies/alignment-rules.owl')]
# Tabla 3.11 de la tesis (TESIS_FINAL_CORREGIDA_2026-11-27), transcripta: clases locales / nuevas, propiedades de
# objeto, propiedades de datos. Sólo se usa para comparar; nada se ajusta a estos valores.
TABLA_3_11 = {'Núcleo': (4, 4, 8, 4), 'Producto': (11, 11, 4, 0), 'Proceso': (8, 8, 3, 0), 'Recursos': (15, 15, 11, 0),
              'Empresa': (5, 5, 2, 0), 'ISO15531': (18, 18, 21, 16), 'ISO14040': (15, 15, 11, 12),
              'Alineamiento': (11, 11, 21, 6)}
TABLA_3_11_TOTAL_BASE = (87, 81, 38)
TESIS_TOTAL_CON_PILOTO = (134, 119, 109)  # Sección 3.6.1: clases, propiedades de objeto, de datos
CARACTERISTICAS = [OWL.FunctionalProperty, OWL.InverseFunctionalProperty, OWL.TransitiveProperty, OWL.SymmetricProperty,
                   OWL.AsymmetricProperty, OWL.ReflexiveProperty, OWL.IrreflexiveProperty]
VOCABULARIOS = ('http://www.w3.org/1999/02/22-rdf-syntax-ns#', 'http://www.w3.org/2000/01/rdf-schema#',
                'http://www.w3.org/2002/07/owl#', 'http://www.w3.org/2001/XMLSchema#', 'http://www.w3.org/2003/11/swrl#',
                'http://www.w3.org/2003/11/swrlb#')


def leer(path):
    g = Graph()
    g.parse(path, format='turtle' if path.endswith('.ttl') else 'xml')
    return g


def declaradas(g, tipo):
    return {s for s in g.subjects(RDF.type, tipo) if isinstance(s, URIRef)}


def resumen(g):
    clases = declaradas(g, OWL.Class) - {OWL.Thing, OWL.Nothing}
    return {'clases': clases, 'objeto': declaradas(g, OWL.ObjectProperty), 'datos': declaradas(g, OWL.DatatypeProperty),
            'anotacion': declaradas(g, OWL.AnnotationProperty)}


def configuracion(cat, ident):
    c = next(c for c in cat['configurations'] if c['id'] == ident)
    return list(dict.fromkeys(c['tbox_paths'] + c['abox_paths'] + c['rules_paths']))


def arbol(g, props):
    """Raíces y profundidad con la regla del servicio: raíz = sin superpropiedad nombrada dentro del grupo."""
    hijos = {p: sorted(h for h in g.subjects(RDFS.subPropertyOf, p) if h in props and h != p) for p in props}
    raices = sorted(p for p in props if not any(q in props and q != p for q in g.objects(p, RDFS.subPropertyOf)))
    prof, alcanzadas = 0, set()

    def bajar(p, d, camino):
        nonlocal prof
        alcanzadas.add(p); prof = max(prof, d)
        for h in hijos[p]:
            if h not in camino:
                bajar(h, d + 1, camino | {h})
    for r in raices:
        bajar(r, 1, {r})
    return {'raices': len(raices), 'nombres_raices': [str(r).split('#')[-1] for r in raices],
            'profundidad_maxima_por_nodos': prof, 'profundidad_maxima_por_aristas': prof - 1 if prof else 0,
            'sin_raiz_alcanzable': len(props - alcanzadas)}


def uris_api(raices):
    vistas = set()
    pila = list(raices)
    while pila:
        n = pila.pop(); vistas.add(n['uri']); pila.extend(n.get('subproperties') or [])
    return vistas


def corto(u):
    return str(u).split('#')[-1] if '#' in str(u) else str(u).rsplit('/', 1)[-1]


def main(api_path, salida):
    cat = json.load(open(CATALOGO, encoding='utf-8'))
    integrada = configuracion(cat, 'integrated')
    extendida = configuracion(cat, 'h3_extended')
    lineas, datos = [], {'catalogo': CATALOGO, 'archivos_integrated': integrada}
    out = lineas.append
    grafos = {p: leer(p) for p in dict.fromkeys(integrada + extendida)}
    res = {p: resumen(g) for p, g in grafos.items()}

    out('Conteo independiente con rdflib (sólo lo declarado, sin inferencia).')
    out(f'Archivos de la configuración "integrated" leídos de {CATALOGO}: {len(integrada)}')
    out('')
    # A. Ocho módulos base frente a la Tabla 3.11.
    out('A. Ocho módulos base de ontologies/ frente a la Tabla 3.11')
    out(f'{"módulo":<13} {"archivo":<34} {"clases loc/nuevas":>17} {"objeto":>7} {"datos":>6} {"anot":>5}  Tabla 3.11 (clases loc/nuevas, objeto, datos)  ¿coincide?')
    vistos = {'clases': set(), 'objeto': set(), 'datos': set()}
    filas = []
    for nombre, path in BASE:
        assert path in integrada, path
        r = res[path]
        nuevas = len(r['clases'] - vistos['clases'])
        for k in vistos:
            vistos[k] |= r[k]
        propio = (len(r['clases']), nuevas, len(r['objeto']), len(r['datos']))
        t = TABLA_3_11[nombre]
        filas.append({'modulo': nombre, 'archivo': path, 'clases_locales': propio[0], 'clases_nuevas': propio[1],
                      'objeto': propio[2], 'datos': propio[3], 'anotacion': len(r['anotacion']), 'tabla_3_11': t,
                      'coincide': propio == t})
        out(f'{nombre:<13} {path:<34} {propio[0]:>8}/{propio[1]:<8} {propio[2]:>7} {propio[3]:>6} {len(r["anotacion"]):>5}  '
            f'{t[0]}/{t[1]}, {t[2]}, {t[3]}  {"sí" if propio == t else "NO"}')
    total_base = (len(vistos['clases']), len(vistos['objeto']), len(vistos['datos']))
    out(f'Total de los ocho módulos (distintas): {total_base[0]} clases, {total_base[1]} de objeto, {total_base[2]} de datos; '
        f'Tabla 3.11: {TABLA_3_11_TOTAL_BASE[0]}, {TABLA_3_11_TOTAL_BASE[1]}, {TABLA_3_11_TOTAL_BASE[2]} '
        f'({"coincide" if total_base == TABLA_3_11_TOTAL_BASE else "NO coincide"})')
    datos['A_ocho_modulos'] = {'filas': filas, 'total': total_base, 'tabla_3_11_total': TABLA_3_11_TOTAL_BASE}
    out('')

    # B. Conjunto integrated archivo por archivo.
    out('B. Configuración "integrated" (módulo Canonical_Catalog_Integrated), lo que agrega cada archivo')
    out(f'{"archivo":<42} {"clases":>7} {"objeto":>7} {"datos":>6}   (nuevas respecto de los archivos anteriores)')
    acum = {'clases': set(), 'objeto': set(), 'datos': set(), 'anotacion': set()}
    pasos = []
    for path in integrada:
        r = res[path]
        nuevo = {k: r[k] - acum[k] for k in acum}
        for k in acum:
            acum[k] |= r[k]
        pasos.append({'archivo': path, **{k: len(v) for k, v in nuevo.items()}})
        out(f'{path:<42} {len(nuevo["clases"]):>7} {len(nuevo["objeto"]):>7} {len(nuevo["datos"]):>6}')
    tot_int = (len(acum['clases']), len(acum['objeto']), len(acum['datos']))
    out(f'Total "integrated": {tot_int[0]} clases, {tot_int[1]} de objeto, {tot_int[2]} de datos, '
        f'{len(acum["anotacion"])} de anotación')
    datos['B_integrated'] = {'pasos': pasos, 'total': tot_int, 'anotacion': len(acum['anotacion'])}
    out('')

    # C. Lo que falta para el total de la tesis con el piloto.
    out(f'C. Archivos de "h3_extended" que no están en "integrated" (total de la tesis con piloto: '
        f'{TESIS_TOTAL_CON_PILOTO[0]} clases, {TESIS_TOTAL_CON_PILOTO[1]} de objeto, {TESIS_TOTAL_CON_PILOTO[2]} de datos)')
    ext = {k: set(v) for k, v in acum.items()}
    pasos_c = []
    for path in extendida:
        if path in integrada:
            continue
        r = res[path]
        nuevo = {k: r[k] - ext[k] for k in ext}
        for k in ext:
            ext[k] |= r[k]
        detalle = {k: sorted(corto(u) for u in nuevo[k]) for k in ('clases', 'objeto', 'datos')}
        pasos_c.append({'archivo': path, **{k: len(v) for k, v in nuevo.items()}, 'detalle': detalle})
        out(f'+ {path:<40} {len(nuevo["clases"]):>7} {len(nuevo["objeto"]):>7} {len(nuevo["datos"]):>6}   '
            f'clases: {", ".join(detalle["clases"]) or "—"}; objeto: {", ".join(detalle["objeto"]) or "—"}; '
            f'datos: {", ".join(detalle["datos"]) or "—"}')
    tot_ext = (len(ext['clases']), len(ext['objeto']), len(ext['datos']))
    out(f'Total "h3_extended": {tot_ext[0]} clases, {tot_ext[1]} de objeto, {tot_ext[2]} de datos '
        f'({"coincide" if tot_ext == TESIS_TOTAL_CON_PILOTO else "NO coincide"} con el total de la tesis con piloto)')
    datos['C_h3_extended'] = {'pasos': pasos_c, 'total': tot_ext, 'tesis': TESIS_TOTAL_CON_PILOTO}
    out('')

    # D. Características sin declaración de tipo y predicados usados sin declarar (conjunto integrated).
    g = Graph()
    for path in integrada:
        g += grafos[path]
    declaradas_todas = acum['objeto'] | acum['datos'] | acum['anotacion'] | declaradas(g, RDF.Property)
    con_caracteristica = {s for t in CARACTERISTICAS for s in g.subjects(RDF.type, t) if isinstance(s, URIRef)}
    sin_tipo = sorted(con_caracteristica - acum['objeto'] - acum['datos'])
    usados = {p for p in g.predicates() if isinstance(p, URIRef) and not str(p).startswith(VOCABULARIOS)}
    sin_declarar = sorted(usados - declaradas_todas)
    out('D. Fuera de los árboles (conjunto "integrated")')
    out(f'Propiedades con una característica declarada y sin owl:ObjectProperty ni owl:DatatypeProperty: {len(sin_tipo)}'
        + (f' ({", ".join(corto(u) for u in sin_tipo)})' if sin_tipo else ''))
    out(f'Predicados usados que no están declarados como propiedad (fuera de RDF, RDFS, OWL, XSD y SWRL): {len(sin_declarar)}'
        + (f' ({", ".join(str(u) for u in sin_declarar)})' if sin_declarar else ''))
    swrl = Namespace('http://www.w3.org/2003/11/swrl#')
    referidas = set(g.objects(None, swrl.propertyPredicate)) | set(g.objects(None, RDFS.subPropertyOf)) \
        | set(g.objects(None, OWL.inverseOf)) | set(g.objects(None, OWL.equivalentProperty)) | set(g.objects(None, OWL.onProperty))
    referidas_sin_declarar = sorted(u for u in referidas - declaradas_todas if isinstance(u, URIRef))
    out(f'Propiedades nombradas en reglas SWRL, rdfs:subPropertyOf, owl:inverseOf, owl:equivalentProperty u owl:onProperty '
        f'sin declarar: {len(referidas_sin_declarar)}' + (f' ({", ".join(str(u) for u in referidas_sin_declarar)})' if referidas_sin_declarar else ''))
    out('rdfs:label, rdfs:comment y owl:versionInfo se usan como anotaciones incorporadas de RDFS y OWL; no se declaran '
        'como owl:AnnotationProperty y no se cuentan.')
    datos['D_fuera_de_los_arboles'] = {'caracteristica_sin_tipo': [str(u) for u in sin_tipo],
                                       'predicados_sin_declarar': [str(u) for u in sin_declarar],
                                       'referidas_sin_declarar': [str(u) for u in referidas_sin_declarar]}
    out('')

    # E. Árboles independientes y comparación con el servicio.
    arbol_obj, arbol_dat = arbol(g, acum['objeto']), arbol(g, acum['datos'])
    api = json.load(open(api_path, encoding='utf-8'))
    resp = api['respuesta']
    api_obj, api_dat = uris_api(resp['objectProperties']), uris_api(resp['dataProperties'])
    rd_obj, rd_dat = {str(u) for u in acum['objeto']}, {str(u) for u in acum['datos']}
    comp = {
        'objeto': {'rdflib': len(rd_obj), 'servicio': len(api_obj), 'servicio_declara': resp['objectPropertyCount'],
                   'solo_rdflib': sorted(rd_obj - api_obj), 'solo_servicio': sorted(api_obj - rd_obj),
                   'raices_rdflib': arbol_obj['raices'], 'raices_servicio': api['resumen']['propiedades_de_objeto']['raices'],
                   'profundidad_rdflib': arbol_obj['profundidad_maxima_por_nodos'],
                   'profundidad_servicio': api['resumen']['propiedades_de_objeto']['profundidad_maxima_por_nodos']},
        'datos': {'rdflib': len(rd_dat), 'servicio': len(api_dat), 'servicio_declara': resp['dataPropertyCount'],
                  'solo_rdflib': sorted(rd_dat - api_dat), 'solo_servicio': sorted(api_dat - rd_dat),
                  'raices_rdflib': arbol_dat['raices'], 'raices_servicio': api['resumen']['propiedades_de_datos']['raices'],
                  'profundidad_rdflib': arbol_dat['profundidad_maxima_por_nodos'],
                  'profundidad_servicio': api['resumen']['propiedades_de_datos']['profundidad_maxima_por_nodos']},
        'anotacion': {'rdflib': len(acum['anotacion']), 'servicio': resp['annotationPropertyCount']},
    }
    out('E. Árboles con la regla del servicio (raíz = sin superpropiedad nombrada en el grupo) y comparación con el servicio')
    for nombre, a in (('objeto', arbol_obj), ('datos', arbol_dat)):
        c = comp[nombre]
        iguales = (not c['solo_rdflib'] and not c['solo_servicio'] and c['rdflib'] == c['servicio_declara']
                   and c['raices_rdflib'] == c['raices_servicio'] and c['profundidad_rdflib'] == c['profundidad_servicio'])
        c['coincide'] = iguales
        out(f'de {nombre}: rdflib {c["rdflib"]} propiedades, {a["raices"]} raíces, profundidad {a["profundidad_maxima_por_nodos"]} '
            f'por nodos ({a["profundidad_maxima_por_aristas"]} por aristas); servicio {c["servicio"]} alcanzadas en el árbol '
            f'({c["servicio_declara"]} declaradas), {c["raices_servicio"]} raíces, profundidad {c["profundidad_servicio"]} por nodos. '
            f'{"Coinciden" if iguales else "NO coinciden"}'
            + (f'; sólo rdflib: {c["solo_rdflib"]}' if c['solo_rdflib'] else '')
            + (f'; sólo servicio: {c["solo_servicio"]}' if c['solo_servicio'] else ''))
    c = comp['anotacion']
    out(f'de anotación: rdflib {c["rdflib"]}, servicio {c["servicio"]} '
        f'({"coinciden" if c["rdflib"] == c["servicio"] else "NO coinciden"})')
    datos['E_comparacion'] = comp
    datos['E_arboles_rdflib'] = {'objeto': arbol_obj, 'datos': arbol_dat}

    json.dump(datos, open(salida, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    print('\n'.join(lineas))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
