"""Gestión de individuos por la API: recorrido completo, entradas inválidas, deshacer y tiempos de la validación posterior.

Cada paso guarda la petición, el código, el cuerpo y una consulta SPARQL de control (por la plataforma).
Todo lo que se crea se borra con la plataforma; el guion termina con el grafo como lo encontró.
uso: python3 individuos_api.py <salida.json>
"""
import json, sys, time
from comun import enc, http, sparql, guardar

HR = 'http://w3id.org/protys/ontology/iso15531#HumanResource'
PLANT = 'http://w3id.org/protys/ontology/paint-ext#Plant'
EMPLEADOS = 'http://w3id.org/protys/ontology/paint-ext#numberOfEmployees'
EXISTENTE = 'http://w3id.org/protys/fixture#Operator_01'
pasos = []


def control(iri):
    return sparql(f'SELECT ?g ?p ?o WHERE {{ GRAPH ?g {{ <{iri}> ?p ?o }} }} ORDER BY ?g ?p ?o')


def paso(nombre, metodo, ruta, cuerpo=None, iri=None):
    t0 = time.time()
    codigo, texto = http(metodo, ruta, cuerpo, timeout=900)
    try:
        respuesta = json.loads(texto)
    except ValueError:
        respuesta = texto[:500]
    r = {'paso': nombre, 'peticion': f'{metodo} /api{ruta}', 'cuerpo': cuerpo, 'codigo': codigo,
         'ms_reloj': round((time.time() - t0) * 1000), 'respuesta': respuesta}
    if iri:
        r['control_sparql'] = control(iri)
    pasos.append(r)
    v = respuesta.get('validation') if isinstance(respuesta, dict) else None
    extra = f" | validación {v['status']} {v['timeMs']} ms cache={v['cacheHit']}" if v else ''
    msg = respuesta.get('message') if isinstance(respuesta, dict) else ''
    print(f"{nombre}: {codigo}{extra}{' | ' + msg if msg and not v else ''}{' | control ' + str(len(r['control_sparql'])) + ' triples' if iri else ''}")
    return codigo, respuesta


def main(salida):
    # A1: recorrido completo por la API.
    a = 'http://example.org/individuos/operario-api'
    paso('A1.1 crear (IRI y rdfs:label)', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': a, 'label': 'Operario API'}, iri=a)
    paso('A1.2 leer', 'GET', f'/ontology/individuals?uri={enc(a)}')
    paso('A1.2 listar los individuos de la clase', 'GET', f'/ontology/individuals?classUri={enc(HR)}')
    paso('A1.3 modificar la etiqueta', 'PUT', f'/ontology/individuals?uri={enc(a)}', {'label': 'Operario API (modificado)'}, iri=a)
    paso('A1.4 eliminar', 'DELETE', f'/ontology/individuals?uri={enc(a)}', iri=a)
    paso('A1.5 leer después de eliminar', 'GET', f'/ontology/individuals?uri={enc(a)}')

    # A4: entradas inválidas.
    paso('A4.1 clase inexistente', 'POST', f'/ontology/individuals?classUri={enc("http://w3id.org/protys/ontology/iso15531#ClaseQueNoExiste")}', {'label': 'x'})
    paso('A4.2 classUri que no es un IRI', 'POST', f'/ontology/individuals?classUri={enc("esto no es un IRI")}', {'label': 'x'})
    paso('A4.3 classUri relativo', 'POST', f'/ontology/individuals?classUri=relativo', {'label': 'x'})
    paso('A4.4 sin classUri', 'POST', '/ontology/individuals', {'label': 'x'})
    paso('A4.5 IRI del individuo mal formado', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': 'no es un IRI'})
    paso('A4.6 clave de propiedad que no es un IRI', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'properties': {'uri': 'x'}})
    paso('A4.7 individuo que ya existe en el caso (fixture:Operator_01)', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': EXISTENTE}, iri=EXISTENTE)
    d = 'http://example.org/individuos/duplicado'
    paso('A4.8 duplicado, primer envío', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': d, 'label': 'Duplicado'})
    paso('A4.8 duplicado, segundo envío idéntico', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': d, 'label': 'Duplicado'}, iri=d)
    paso('A4.8 limpieza del duplicado', 'DELETE', f'/ontology/individuals?uri={enc(d)}', iri=d)
    paso('A4.9 modificar un individuo inexistente', 'PUT', f'/ontology/individuals?uri={enc("http://example.org/individuos/nadie")}', {'label': 'x'})
    paso('A4.10 eliminar un individuo inexistente', 'DELETE', f'/ontology/individuals?uri={enc("http://example.org/individuos/nadie")}')
    paso('A4.11 forma anterior: IRI en la ruta (ya no se admite)', 'GET', f'/ontology/individuals/{enc(a)}')

    # A5: deshacer, con una edición consistente y con una que deja la red inconsistente.
    u = 'http://example.org/individuos/para-deshacer'
    _, r = paso('A5.1 crear para deshacer', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': u, 'label': 'Para deshacer'}, iri=u)
    paso('A5.2 deshacer el alta', 'POST', f"/ontology/individuals/edits/{r['editId']}/undo", iri=u)
    paso('A5.3 deshacer otra vez la misma edición', 'POST', f"/ontology/individuals/edits/{r['editId']}/undo")
    i = 'http://example.org/individuos/planta-inconsistente'
    _, r = paso('A5.4 alta que contradice el rango xsd:integer de paint-ext:numberOfEmployees', 'POST',
                f'/ontology/individuals?classUri={enc(PLANT)}', {'uri': i, 'label': 'Planta inconsistente', 'properties': {EMPLEADOS: 'muchos'}}, iri=i)
    paso('A5.5 deshacer el alta inconsistente', 'POST', f"/ontology/individuals/edits/{r['editId']}/undo", iri=i)

    # A6: tiempos de la validación posterior (tres ciclos alta/modificación/baja).
    for n in range(1, 4):
        t = f'http://example.org/individuos/tiempo-{n}'
        paso(f'A6.{n} alta', 'POST', f'/ontology/individuals?classUri={enc(HR)}', {'uri': t, 'label': f'Tiempo {n}'})
        paso(f'A6.{n} modificación', 'PUT', f'/ontology/individuals?uri={enc(t)}', {'label': f'Tiempo {n} (modificado)'})
        paso(f'A6.{n} baja', 'DELETE', f'/ontology/individuals?uri={enc(t)}', iri=t)

    tiempos = [{'paso': p['paso'], 'validacion_ms': p['respuesta']['validation']['timeMs'], 'cache': p['respuesta']['validation']['cacheHit'],
                'estado': p['respuesta']['validation']['status'], 'escritura_ms': p['respuesta'].get('writeTimeMs'), 'reloj_ms': p['ms_reloj']}
               for p in pasos if isinstance(p['respuesta'], dict) and p['respuesta'].get('validation')]
    sin_cache = sorted(t['validacion_ms'] for t in tiempos if not t['cache'])
    resumen = {'ediciones_validadas': len(tiempos), 'sin_cache': len(sin_cache),
               'validacion_ms_sin_cache': {'min': sin_cache[0], 'mediana': sin_cache[len(sin_cache) // 2], 'max': sin_cache[-1]} if sin_cache else None,
               'con_cache': sorted(t['validacion_ms'] for t in tiempos if t['cache'])}
    print('tiempos:', json.dumps(resumen))
    guardar(salida, {'pasos': pasos, 'tiempos': tiempos, 'resumen_tiempos': resumen})


if __name__ == '__main__':
    main(sys.argv[1])
