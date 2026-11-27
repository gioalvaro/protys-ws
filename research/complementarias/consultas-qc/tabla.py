"""Consultas complementarias QC01-QC18: filas obtenidas y esperadas, sobre el caso y sobre el caso con las instancias.

Corridas (comun.ejecutar, un proceso nuevo de AcademicRunner por corrida, red integrada con las 22 reglas):
- ALL-caso: datos del caso (research/data/functional.ttl).
- ALL-caso-mas-instancias: los mismos datos más ontologies/paint-case-study-instances.ttl.
- sin-R15-caso-mas-instancias: la anterior sin R15, para ver el efecto de la única regla cuyo antecedente necesita
  datos que sólo están en las instancias (un registro de mantenimiento ACTIVE).

Esperadas (esperadas.py, Python sobre los triples afirmados, sin razonador ni reglas):
- caso y caso más instancias;
- QC02 además en versión encadenada: primero lo que describe R02 (c:WaterFlow como flujo del inventario de un
  proceso con consumo de agua) y después R03 sobre ese inventario ampliado. La esperada simple lee sólo los datos y
  no encadena reglas.

Comparación: multiconjunto de filas con la tolerancia numérica de research/evaluation/run_evaluation.py.

uso: python3 tabla.py <carpeta-salida> <carpeta-temporal>
"""
import csv, json, shutil, sys
from pathlib import Path
import rdflib
from rdflib import RDF

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI.parent))
sys.path.insert(0, str(AQUI))
from comun import RAIZ, configuracion, ejecutar, entorno, equivalentes, filas, guardar, peticion, sha256  # noqa: E402
import esperadas as E  # noqa: E402

QC = sorted(p.stem for p in AQUI.glob('QC*.sparql'))
REGLAS = configuracion('integrated')['enabled_rule_ids']
INSTANCIAS = 'ontologies/paint-case-study-instances.ttl'
c, e, i, a = E.c, E.e, E.i, E.a


def con_qc(spec):
    spec['queries'] = spec['queries'] + [{'id': q, 'path': str((AQUI / f'{q}.sparql').relative_to(RAIZ))} for q in QC]
    return spec


def respuesta(vars_, filas_):
    orden = sorted(filas_, key=lambda f: [str(x) for x in f])
    return {'head': {'vars': vars_}, 'results': {'bindings': [{v: E.termino(x) for v, x in zip(vars_, f)} for f in orden]}}


def qc02_encadenada(d):
    inv = lambda p: d.vals(p, a.hasInventoryRepresentation)
    ops = d.de_tipo(i.ProcessOperation)
    r02 = {lci for op in ops if d.vals(op, i.consumesWaterL) for p in d.vals(op, i.partOfProcess) for lci in inv(p)}
    reglas = rdflib.Graph().parse(RAIZ / 'ontologies/alignment-rules.owl')
    agua_es_productflow = any((c.WaterFlow, RDF.type, k) in reglas for k in d.subclases.get(e.ProductFlow, {e.ProductFlow}))
    flujos = lambda lci: d.vals(lci, e.hasInventoryFlow) | ({c.WaterFlow} if lci in r02 else set())
    es_pf = lambda fl: d.es(fl, e.ProductFlow) or (fl == c.WaterFlow and agua_es_productflow)
    lotes = d.de_tipo(i.Lot)
    return set(d.pares(a.hasRecordedInputFlowForLot)) | {
        (lot, fl) for lot in lotes if d.sujetos(i.producesLot, lot) for p in d.vals(lot, i.hasProcess) for lci in inv(p)
        if d.es(lci, e.UnitProcess) for fl in flujos(lci) if es_pf(fl)}


def esperadas(con_instancias, salida):
    d = E.Datos()
    if con_instancias:
        d.g.parse(RAIZ / INSTANCIAS, format='turtle')
    res = {}
    for ident, (vars_, filas_) in E.calcular(d).items():
        res[ident] = respuesta(vars_, filas_)
    vars_, _ = E.calcular(d)['QC02']
    res['QC02-encadenada'] = respuesta(vars_, qc02_encadenada(d))
    salida.mkdir(parents=True, exist_ok=True)
    for ident, r in res.items():
        (salida / f'{ident}.json').write_text(json.dumps(r, indent=2, ensure_ascii=False) + '\n')
    return res


def correr(nombre, spec, tmp, salida):
    res = ejecutar(spec, tmp / nombre)
    dest = salida / 'corridas' / nombre
    dest.mkdir(parents=True, exist_ok=True)
    ids = [q['id'] for q in spec['queries']] + [v for v in configuracion('integrated')['validation_ids']]
    for f in ['request.json', 'execution.json', 'validation.json', 'validation-after-swrl.json'] + [x + '.json' for x in ids]:
        if (tmp / nombre / f).exists():
            shutil.copy(tmp / nombre / f, dest / f)
    resp = {x: json.loads((tmp / nombre / (x + '.json')).read_text()) for x in ids if (tmp / nombre / (x + '.json')).exists()}
    shutil.rmtree(tmp / nombre)
    return res, resp


def main(salida, tmp):
    salida, tmp = Path(salida), Path(tmp)
    guardar(salida / 'entorno.json', {**entorno(), 'instancias_sha256': sha256(RAIZ / INSTANCIAS)})
    esp = {'caso': esperadas(False, salida / 'esperadas/caso'), 'caso_mas_instancias': esperadas(True, salida / 'esperadas/caso-mas-instancias')}
    todas = ','.join(REGLAS)
    corridas = {
        'caso': ('ALL-caso', con_qc(peticion('integrated', reglas=todas))),
        'caso_mas_instancias': ('ALL-caso-mas-instancias', con_qc(peticion('integrated', extra=[RAIZ / INSTANCIAS], reglas=todas))),
        'sin_R15': ('sin-R15-caso-mas-instancias', con_qc(peticion('integrated', extra=[RAIZ / INSTANCIAS], reglas=','.join(r for r in REGLAS if r != 'R15')))),
    }
    obt, ejec = {}, {}
    for k, (nombre, spec) in corridas.items():
        ejec[k], obt[k] = correr(nombre, spec, tmp, salida)
        r = ejec[k]
        print(nombre, r.get('status'), r.get('input_triples'), r.get('materialized_triples'), flush=True)

    tabla = []
    for q in QC:
        fila = {'consulta': q}
        for k in ('caso', 'caso_mas_instancias'):
            o = obt[k].get(q)
            fila[f'{k}_obtenidas'] = filas(o) if o else 'SIN_SALIDA'
            fila[f'{k}_esperadas'] = filas(esp[k][q])
            fila[f'{k}_coincide'] = bool(o) and equivalentes(o, esp[k][q])
            if q == 'QC02':
                fila[f'{k}_esperadas_encadenada'] = filas(esp[k]['QC02-encadenada'])
                fila[f'{k}_coincide_encadenada'] = bool(o) and equivalentes(o, esp[k]['QC02-encadenada'])
        o = obt['sin_R15'].get(q)
        fila['sin_R15_obtenidas'] = filas(o) if o else 'SIN_SALIDA'
        fila['sin_R15_igual_a_ALL'] = bool(o) and equivalentes(o, obt['caso_mas_instancias'][q])
        tabla.append(fila)

    campos = list(dict.fromkeys(k for f in tabla for k in f))
    with (salida / 'tabla.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=campos); w.writeheader(); w.writerows(tabla)
    si = lambda x: 'sí' if x else 'no'
    md = ['| Consulta | Caso: obtenidas | Caso: esperadas | Coincide | Caso + instancias: obtenidas | Caso + instancias: esperadas | Coincide |',
          '|---|---:|---:|---|---:|---:|---|']
    for f in tabla:
        if f['consulta'] == 'QC02':
            md.append(f"| QC02 | {f['caso_obtenidas']} | {f['caso_esperadas']} (simple) / {f['caso_esperadas_encadenada']} (encadenada R02→R03) | "
                      f"{si(f['caso_coincide'])} / {si(f['caso_coincide_encadenada'])} | {f['caso_mas_instancias_obtenidas']} | "
                      f"{f['caso_mas_instancias_esperadas']} (simple) / {f['caso_mas_instancias_esperadas_encadenada']} (encadenada R02→R03) | "
                      f"{si(f['caso_mas_instancias_coincide'])} / {si(f['caso_mas_instancias_coincide_encadenada'])} |")
        else:
            md.append(f"| {f['consulta']} | {f['caso_obtenidas']} | {f['caso_esperadas']} | {si(f['caso_coincide'])} | "
                      f"{f['caso_mas_instancias_obtenidas']} | {f['caso_mas_instancias_esperadas']} | {si(f['caso_mas_instancias_coincide'])} |")
    (salida / 'tabla.md').write_text('\n'.join(md) + '\n', encoding='utf-8')
    resumen = {
        'corridas': {corridas[k][0]: {x: ejec[k].get(x) for x in ('status', 'input_triples', 'materialized_triples')} for k in corridas},
        'coinciden_caso': sum(1 for f in tabla if f['caso_coincide'] or f.get('caso_coincide_encadenada')),
        'coinciden_caso_mas_instancias': sum(1 for f in tabla if f['caso_mas_instancias_coincide'] or f.get('caso_mas_instancias_coincide_encadenada')),
        'consultas_que_cambian_sin_R15': [f['consulta'] for f in tabla if not f['sin_R15_igual_a_ALL']],
        'tabla': tabla,
    }
    guardar(salida / 'resumen.json', resumen)
    print(json.dumps({k: v for k, v in resumen.items() if k != 'tabla'}, indent=1, ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:3])
