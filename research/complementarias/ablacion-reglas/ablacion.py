"""Ablación de a una regla, con las 21 consultas del catálogo, las 18 complementarias (QC01-QC18) y las 4 ASK.

Corridas (cada una, un proceso nuevo de AcademicRunner con comun.ejecutar):
- «union»: la configuración del catálogo sin correspondencias ni reglas, con sus datos (functional-union.ttl).
- ALL: la red integrada con las 22 reglas y los datos del caso (functional.ttl).
- sin-Rxx: ALL sin una regla (22 corridas). OWL_ONLY (trabajador sin reglas) y NONE (sin trabajador).
Las consultas del catálogo, las 4 ASK y las 18 complementarias se piden en la misma corrida; sólo cambian
`enabled_rules` (y la configuración, en «union»).

Salidas: por corrida, execution.json y las respuestas; complementarias.csv (filas, coincidencia con la esperada,
ALL frente a union); ablacion-regla-x-consulta.csv (regla x 21 + 18 consultas y 4 ASK); resumen.json.

uso: python3 ablacion.py <carpeta-salida> <carpeta-temporal>
"""
import collections, csv, json, shutil, sys
from pathlib import Path
import rdflib
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'consultas-qc'))
from comun import CATALOGO, RAIZ, configuracion, ejecutar, entorno, equivalentes, filas, guardar, peticion  # noqa: E402
import esperadas as E  # noqa: E402

COMP = Path(__file__).resolve().parents[1] / 'consultas-qc'


def triples_nuevos(d):
    """Triples del modelo final que no están en la entrada preparada de la misma corrida, sin contar los que tienen
    nodos anónimos (las definiciones de reglas y las listas OWL). No se usa «materializados - entrada» porque al
    desactivar reglas el ejecutor también quita del modelo los triples que las definen."""
    if not (d / 'result-model.ttl').exists():
        return None
    sin_bnode = lambda g: {t for t in g if not any(isinstance(x, rdflib.BNode) for x in t)}
    return len(sin_bnode(rdflib.Graph().parse(d / 'result-model.ttl')) - sin_bnode(rdflib.Graph().parse(d / 'prepared-input.ttl')))
QC = sorted(p.stem for p in COMP.glob('QC*.sparql'))
REGLAS = configuracion('integrated')['enabled_rule_ids']
CATAL = [q['id'] for q in CATALOGO['queries']]
VALID = configuracion('integrated')['validation_ids']
REGLA_DE = {r['consulta']: r['regla'] for r in csv.DictReader(open(COMP / 'tabla.csv', encoding='utf-8'))}


def con_qc(spec):
    spec['queries'] = spec['queries'] + [{'id': q, 'path': str((COMP / f'{q}.sparql').relative_to(RAIZ))} for q in QC]
    return spec


def corrida(nombre, spec, tmp, salida):
    res = ejecutar(spec, tmp / nombre)
    dest = salida / 'corridas' / nombre
    dest.mkdir(parents=True, exist_ok=True)
    ids = [q['id'] for q in spec['queries']]
    for f in ['request.json', 'execution.json', 'validation.json', 'validation-after-swrl.json'] + [x + '.json' for x in ids]:
        if (tmp / nombre / f).exists():
            shutil.copy(tmp / nombre / f, dest / f)
    res['_nuevos'] = triples_nuevos(tmp / nombre)
    resp = {x: json.loads((tmp / nombre / (x + '.json')).read_text()) for x in ids if (tmp / nombre / (x + '.json')).exists()}
    shutil.rmtree(tmp / nombre)
    return res, resp


def diferencia(a, b):
    """Filas de a que no están en b y de b que no están en a (fila serializada exacta)."""
    sa = collections.Counter(json.dumps(x, sort_keys=True) for x in a['results']['bindings'])
    sb = collections.Counter(json.dumps(x, sort_keys=True) for x in b['results']['bindings'])
    return sum((sa - sb).values()), sum((sb - sa).values())


def main(salida, tmp):
    salida, tmp = Path(salida), Path(tmp)
    guardar(salida / 'entorno.json', entorno())
    calculadas = E.calcular(E.Datos())
    esperadas = {}
    for q in QC:
        vars_, filas_ = calculadas[q]
        orden = sorted(filas_, key=lambda f: [str(x) for x in f])
        esperadas[q] = {'head': {'vars': vars_}, 'results': {'bindings': [{v: E.termino(x) for v, x in zip(vars_, f)} for f in orden]}}
    corridas = [('union', con_qc(peticion('union'))), ('ALL', con_qc(peticion('integrated', reglas=','.join(REGLAS))))]
    corridas += [('sin-' + r, con_qc(peticion('integrated', reglas=','.join(x for x in REGLAS if x != r)))) for r in REGLAS]
    corridas += [('OWL_ONLY', con_qc(peticion('integrated', reglas='OWL_ONLY'))), ('NONE', con_qc(peticion('integrated', reglas='NONE')))]
    datos = {}
    for nombre, spec in corridas:
        datos[nombre] = corrida(nombre, spec, tmp, salida)
        r = datos[nombre][0]
        print(nombre, r.get('status'), r.get('input_triples'), r.get('materialized_triples'), r.get('_nuevos'), flush=True)

    # 2.4: complementarias en ALL y en union, frente a la esperada.
    tabla_qc = []
    for q in QC:
        fila = {'consulta': q, 'regla': REGLA_DE[q], 'filas_esperadas': filas(esperadas[q])}
        for conf in ('ALL', 'union'):
            res, resp = datos[conf]
            if q not in resp:
                fila[f'filas_{conf}'] = 'SIN_SALIDA'; fila[f'coincide_{conf}'] = False
                continue
            fila[f'filas_{conf}'] = filas(resp[q])
            fila[f'coincide_{conf}'] = equivalentes(resp[q], esperadas[q])
            if not fila[f'coincide_{conf}']:
                falt, sobr = diferencia(esperadas[q], resp[q])
                fila[f'{conf}_faltan_de_la_esperada'] = falt; fila[f'{conf}_sobran_respecto_de_la_esperada'] = sobr
        if q in datos['ALL'][1] and q in datos['union'][1]:
            fila['ALL_igual_a_union'] = equivalentes(datos['ALL'][1][q], datos['union'][1][q])
        tabla_qc.append(fila)
    campos = ['consulta', 'regla', 'filas_esperadas', 'filas_ALL', 'coincide_ALL', 'ALL_faltan_de_la_esperada', 'ALL_sobran_respecto_de_la_esperada',
              'filas_union', 'coincide_union', 'union_faltan_de_la_esperada', 'union_sobran_respecto_de_la_esperada', 'ALL_igual_a_union']
    with (salida / 'complementarias.csv').open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=campos); w.writeheader(); w.writerows(tabla_qc)

    # 2.5: ablación, cada consulta frente a ALL.
    base_res, base = datos['ALL']
    consultas = CATAL + QC
    tabla, detalle = [], {}
    for nombre, _ in corridas[1:]:
        res, resp = datos[nombre]
        fila = {'corrida': nombre, 'status': res.get('status'), 'input_triples': res.get('input_triples'), 'materialized_triples': res.get('materialized_triples'),
                'inferidos': res['_nuevos'], 'inferidos_menos_que_ALL': (base_res['_nuevos'] or 0) - (res['_nuevos'] or 0),
                'swrl_axiomas_nuevos': (res.get('swrl') or {}).get('new_axiom_count')}
        cambios = {}
        for q in consultas + VALID:
            if q not in resp:
                fila[q] = 'SIN_SALIDA'; cambios[q] = {'motivo': 'sin salida'}; continue
            if 'boolean' in base[q]:
                igual = resp[q].get('boolean') == base[q]['boolean']
                fila[q] = 'igual' if igual else f"{base[q]['boolean']}->{resp[q].get('boolean')}"
                if not igual:
                    cambios[q] = {'ALL': base[q]['boolean'], 'corrida': resp[q].get('boolean')}
                continue
            igual = equivalentes(resp[q], base[q])
            n0, n1 = filas(base[q]), filas(resp[q])
            fila[q] = 0 if igual else (n1 - n0 if n1 != n0 else 'distinta')
            if not igual:
                perd, nuev = diferencia(base[q], resp[q])
                cambios[q] = {'filas_ALL': n0, 'filas_corrida': n1, 'filas_perdidas': perd, 'filas_nuevas': nuev}
        fila['catalogo_que_cambian'] = ' '.join(q for q in CATAL if q in cambios)
        fila['complementarias_que_cambian'] = ' '.join(q for q in QC if q in cambios)
        fila['ask_que_cambian'] = ' '.join(q for q in VALID if q in cambios)
        tabla.append(fila); detalle[nombre] = cambios
    campos = ['corrida', 'status', 'input_triples', 'materialized_triples', 'inferidos', 'inferidos_menos_que_ALL', 'swrl_axiomas_nuevos',
              'catalogo_que_cambian', 'complementarias_que_cambian', 'ask_que_cambian'] + consultas + VALID
    with (salida / 'ablacion-regla-x-consulta.csv').open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=campos); w.writeheader(); w.writerows(tabla)

    por_regla = {r: [q for q in consultas if q in detalle['sin-' + r]] for r in REGLAS}
    resumen = {
        'complementarias': tabla_qc,
        'consultas_por_regla': por_regla,
        'reglas_sin_efecto_en_ninguna_consulta': [r for r, qs in por_regla.items() if not qs],
        'reglas_de_las_18_cuya_consulta_complementaria_cambia_al_quitarla': {r: [q for q in QC if q in detalle['sin-' + r]] for r in REGLAS},
        'complementarias_que_cambian_sin_una_regla_distinta_de_la_propia': {q: [r for r in REGLAS if q in detalle['sin-' + r] and r not in REGLA_DE[q].split()] for q in QC},
        'extremos': {n: {'catalogo': [q for q in CATAL if q in detalle[n]], 'complementarias': [q for q in QC if q in detalle[n]]} for n in ('OWL_ONLY', 'NONE')},
    }
    guardar(salida / 'ablacion-detalle.json', {'leyenda_csv': 'Por consulta: 0 = misma respuesta que ALL; entero = diferencia de filas respecto de ALL; '
                                                '"distinta" = misma cantidad de filas con otro contenido; ASK: "igual" o ALL->corrida.', 'tabla': tabla, 'cambios': detalle})
    guardar(salida / 'resumen.json', resumen)
    print(json.dumps(resumen, indent=1, ensure_ascii=False, default=str))


if __name__ == '__main__':
    main(*sys.argv[1:3])
