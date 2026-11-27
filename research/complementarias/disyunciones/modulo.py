"""Módulo opcional de disyunciones (ontologies/disjointness-module.ttl, 75 pares) con el caso y con las instancias.

Corridas (AcademicRunner, proceso nuevo, configuración «integrated» con las 22 reglas, datos functional.ttl):
- caso-sin-modulo y caso-con-modulo;
- instancias-sin-modulo y instancias-con-modulo: además ontologies/paint-case-study-instances.ttl, con P6 y P7.
Para cada una: consistencia antes y después de las reglas, clases insatisfacibles, triples, las 21 consultas del
catálogo y las 4 ASK frente a la misma corrida sin el módulo, y P6/P7 frente a sus respuestas esperadas
(research/integration/consultas-p6-p7/esperadas). Cuenta además las clases y propiedades declaradas por los ocho
módulos base, con y sin el módulo.

uso: python3 modulo.py <carpeta-salida> <carpeta-temporal>
"""
import json, shutil, sys
from pathlib import Path
import rdflib
from rdflib import OWL, RDF, URIRef
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from comun import CATALOGO, RAIZ, configuracion, ejecutar, entorno, equivalentes, filas, guardar, peticion, sha256  # noqa: E402
from disyunciones import es_inconsistente, estado  # noqa: E402

MODULO = 'ontologies/disjointness-module.ttl'
INSTANCIAS = 'ontologies/paint-case-study-instances.ttl'
P67_DIR = RAIZ / 'research/integration/consultas-p6-p7'
P67 = [{'id': q, 'path': str((P67_DIR / f'consultas/{q}.rq').relative_to(RAIZ))} for q in ('P6', 'P7')]
CATAL = [q['id'] for q in CATALOGO['queries']]
VALID = configuracion('integrated')['validation_ids']
BASE_OCHO = ['core-concepts.owl', 'product-module.owl', 'process-module.owl', 'resource-module.owl', 'enterprise-module.owl',
             'iso15531-module.owl', 'iso14040-module.owl', 'alignment-rules.owl']
CLAVES = ('status', 'profile_valid', 'raw_input_triples', 'construct_added_triples', 'input_triples', 'input_named_individuals',
          'dl_materialized_triples', 'post_swrl_dl_materialized_triples', 'materialized_triples')


def correr(nombre, extra, consultas_extra, tmp, salida):
    spec = peticion('integrated', extra=[RAIZ / x for x in extra])
    spec['queries'] = spec['queries'] + consultas_extra
    destino = tmp / nombre
    res = ejecutar(spec, destino)
    keep = salida / 'corridas' / nombre
    keep.mkdir(parents=True, exist_ok=True)
    ids = [q['id'] for q in spec['queries']] + VALID
    for f in ['request.json', 'execution.json', 'validation.json', 'validation-after-swrl.json'] + [x + '.json' for x in ids]:
        if (destino / f).exists():
            shutil.copy(destino / f, keep / f)
    resp = {x: json.loads((destino / (x + '.json')).read_text()) for x in ids if (destino / (x + '.json')).exists()}
    e = estado(res, destino)
    e.update({k: res.get(k) for k in CLAVES}, swrl_axiomas_nuevos=(res.get('swrl') or {}).get('new_axiom_count'))
    shutil.rmtree(destino)
    return e, resp


def comparar(a, b, ids):
    return {q: ('igual' if q in a and q in b and equivalentes(b[q], a[q]) else f"distinta ({filas(a.get(q, {}))} -> {filas(b.get(q, {}))} filas)") for q in ids}


def censo(con_modulo):
    tipos = {'Class': OWL.Class, 'ObjectProperty': OWL.ObjectProperty, 'DatatypeProperty': OWL.DatatypeProperty}
    union = {k: set() for k in tipos}
    rutas = [RAIZ / 'ontologies' / f for f in BASE_OCHO] + ([RAIZ / MODULO] if con_modulo else [])
    for r in rutas:
        g = rdflib.Graph().parse(r, format='turtle' if r.suffix == '.ttl' else 'xml')
        for k, t in tipos.items():
            union[k] |= {s for s in g.subjects(RDF.type, t) if isinstance(s, URIRef)}
    n = {k: len(v) for k, v in union.items()}
    return {**n, 'propiedades': n['ObjectProperty'] + n['DatatypeProperty']}


def main(salida, tmp):
    salida, tmp = Path(salida), Path(tmp)
    guardar(salida / 'entorno.json', entorno())
    esperadas = {q: json.loads((P67_DIR / f'esperadas/{q}.json').read_text()) for q in ('P6', 'P7')}
    plan = [('caso-sin-modulo', [], []), ('caso-con-modulo', [MODULO], []),
            ('instancias-sin-modulo', [INSTANCIAS], P67), ('instancias-con-modulo', [INSTANCIAS, MODULO], P67)]
    est, resp = {}, {}
    for nombre, extra, consultas in plan:
        est[nombre], resp[nombre] = correr(nombre, extra, consultas, tmp, salida)
        print(nombre, json.dumps(est[nombre], ensure_ascii=False), flush=True)
    corridas = {}
    for nombre, extra, consultas in plan:
        base = nombre.replace('con-modulo', 'sin-modulo')
        r, e = resp[nombre], est[nombre]
        corridas[nombre] = {
            'estado': e['status'], 'antes_de_las_reglas': e.get('antes_de_las_reglas'), 'despues_de_las_reglas': e.get('despues_de_las_reglas'),
            'inconsistente': es_inconsistente(e),
            'clases_insatisfacibles_antes': e.get('clases_insatisfacibles_antes'), 'clases_insatisfacibles_despues': e.get('clases_insatisfacibles_despues'),
            'triples_entrada': e.get('input_triples'), 'triples_materializados': e.get('materialized_triples'),
            'catalogo_21_distintas_de_la_corrida_sin_modulo': [q for q, x in comparar(resp[base], r, CATAL).items() if x != 'igual'],
            'ask': {q: r.get(q, {}).get('boolean') for q in VALID},
        }
        if consultas:
            corridas[nombre]['P6_P7_filas'] = {q: filas(r.get(q, {})) for q in ('P6', 'P7')}
            corridas[nombre]['P6_P7_igual_a_la_esperada'] = {q: (equivalentes(r[q], esperadas[q]) if q in r else 'SIN_SALIDA') for q in ('P6', 'P7')}
    g = rdflib.Graph().parse(RAIZ / MODULO, format='turtle')
    resultado = {
        'modulo': {'ruta': MODULO, 'sha256': sha256(RAIZ / MODULO), 'triples': len(g),
                   'pares_disjointWith': len(list(g.triples((None, OWL.disjointWith, None))))},
        'instancias': {'ruta': INSTANCIAS, 'sha256': sha256(RAIZ / INSTANCIAS)},
        'censo_ocho_modulos_base': {'sin_modulo': censo(False), 'con_modulo': censo(True)},
        'corridas': corridas,
    }
    guardar(salida / 'resultado.json', resultado)
    print(json.dumps(resultado, indent=1, ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:3])
