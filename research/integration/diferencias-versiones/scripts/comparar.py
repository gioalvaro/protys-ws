"""Triples inferidos que aparecen o desaparecen entre v1.0.0 y v1.1.0, y las 21 consultas de cada versión
contra research/model/expected (configuración integrada), fila por fila.

Inferidos = (dl-pre-deductions ∪ dl-post-deductions ∪ swrl/deductions) − entrada cruda (los archivos de la petición), como en ReasoningService. Los
nodos en blanco se canonicalizan. La comparación con lo esperado usa la regla de run_evaluation.py (equivalent):
mismo conjunto de variables, emparejamiento uno a uno de filas, números con tolerancia relativa 1e-9 y textos
xsd:string iguales con o sin tipo.
uso: python3 c2_c3_comparar.py <carpeta-salida-de-c2_versiones>
"""
import json, subprocess, sys
from decimal import Decimal, DecimalException
from pathlib import Path
import rdflib
from rdflib.compare import to_canonical_graph

sys.path.insert(0, str(Path(__file__).resolve().parent))
from versiones import ANTES, DESPUES, RAIZ, leer  # noqa: E402
XSD = 'http://www.w3.org/2001/XMLSchema#'
NUM = {XSD + n for n in ['integer', 'decimal', 'double', 'float', 'int', 'long', 'short', 'nonNegativeInteger']}




def entrada_cruda(version):
    """Los archivos de la petición (configuración integrada): ontologies/ de la versión, el resto del árbol de trabajo.
    Es la entrada que recibe el razonador antes de materializar; combined.owl de AcademicRunner ya trae la
    materialización DL, así que no sirve para restar."""
    catalogo = json.loads((RAIZ / 'research/catalog.json').read_text())
    cfg = next(c for c in catalogo['configurations'] if c['id'] == 'integrated')
    g = rdflib.Graph()
    for ruta in cfg['tbox_paths'] + cfg['abox_paths'] + cfg['rules_paths']:
        datos = leer(version if ruta.startswith('ontologies/') else DESPUES, ruta)
        g.parse(data=datos, format='turtle' if ruta.endswith('.ttl') else 'xml')
    return g


def inferidos(carpeta):
    entrada = entrada_cruda(carpeta.name)
    g = rdflib.Graph()
    for nombre, fmt in (('dl-pre-deductions.ttl', 'turtle'), ('dl-post-deductions.ttl', 'turtle'), ('swrl/deductions.owl', 'xml')):
        f = carpeta / nombre
        if f.exists() and f.stat().st_size:
            g.parse(f, format=fmt)
    # Como ReasoningService: se quitan los triples que ya estaban en la entrada (los nodos en blanco, canonicalizados).
    return {t for t in to_canonical_graph(g)} - {t for t in to_canonical_graph(entrada)}


def valor_igual(a, b):
    # Copia de equivalent.value_eq (research/evaluation/run_evaluation.py).
    if a.get('type') != b.get('type'):
        return False
    if a.get('type') == 'literal' and a.get('datatype') in NUM and b.get('datatype') in NUM:
        try:
            x, y = Decimal(a['value']), Decimal(b['value'])
            return abs(x - y) <= max(Decimal('1e-9'), Decimal('1e-9') * max(abs(x), abs(y)))
        except (DecimalException, ValueError, TypeError):
            return False
    if a.get('type') == 'literal' and 'xml:lang' not in a and 'xml:lang' not in b:
        if a.get('datatype', XSD + 'string') == XSD + 'string' and b.get('datatype', XSD + 'string') == XSD + 'string':
            return a.get('value') == b.get('value')
    return a == b


def comparar(obtenido, esperado):
    if 'boolean' in esperado:
        return {'iguales': obtenido.get('boolean') is esperado['boolean'], 'filas_esperadas': None, 'filas_obtenidas': None, 'sin_par_esperadas': [], 'sin_par_obtenidas': []}
    vo, ve = set(obtenido['head']['vars']), set(esperado['head']['vars'])
    got, want = obtenido['results']['bindings'], esperado['results']['bindings']
    aristas = [[i for i, g in enumerate(got) if fila.keys() == g.keys() and all(valor_igual(g[k], v) for k, v in fila.items())] for fila in want]
    pareja = {}

    def asignar(i, vistos):
        for c in aristas[i]:
            if c in vistos:
                continue
            vistos.add(c)
            if c not in pareja or asignar(pareja[c], vistos):
                pareja[c] = i
                return True
        return False
    ok = [asignar(i, set()) for i in range(len(want))]
    sin_par_esp = [want[i] for i, x in enumerate(ok) if not x]
    sin_par_obt = [got[i] for i in range(len(got)) if i not in pareja]
    return {'iguales': vo == ve and len(got) == len(want) and not sin_par_esp, 'filas_esperadas': len(want), 'filas_obtenidas': len(got),
            'variables_iguales': vo == ve, 'sin_par_esperadas': sin_par_esp[:10], 'sin_par_obtenidas': sin_par_obt[:10]}


def main(carpeta):
    carpeta = Path(carpeta)
    catalogo = json.loads((RAIZ / 'research/catalog.json').read_text())
    inf = {v: inferidos(carpeta / v) for v in (ANTES, DESPUES)}
    solo_head = sorted(inf[DESPUES] - inf[ANTES])
    solo_etiqueta = sorted(inf[ANTES] - inf[DESPUES])
    corto = lambda x: x.n3().replace('<http://w3id.org/protys/ontology/', '<').replace('<http://w3id.org/protys/fixture#', '<f:')
    c2 = {'inferidos_'+ANTES: len(inf[ANTES]), 'inferidos_'+DESPUES: len(inf[DESPUES]),
          'aparecen_en_'+DESPUES: [' '.join(corto(x) for x in t) for t in solo_head],
          'desaparecen_en_'+DESPUES: [' '.join(corto(x) for x in t) for t in solo_etiqueta]}
    c3 = {}
    for v in (ANTES, DESPUES):
        c3[v] = {}
        for q in catalogo['queries']:
            ruta = q.get('expected_paths', {}).get('integrated', q.get('expected_path'))
            esperado = json.loads((RAIZ / ruta).read_text())
            obtenido = json.loads((carpeta / v / (q['id'] + '.json')).read_text())
            c3[v][q['id']] = {'esperado': ruta, **comparar(obtenido, esperado)}
    (carpeta / 'c2-inferidos.json').write_text(json.dumps(c2, indent=2, ensure_ascii=False) + '\n')
    (carpeta / 'c3-consultas.json').write_text(json.dumps(c3, indent=2, ensure_ascii=False) + '\n')
    print(f"Inferidos: {ANTES} {c2['inferidos_'+ANTES]}, {DESPUES} {c2['inferidos_'+DESPUES]}; aparecen {len(solo_head)}, desaparecen {len(solo_etiqueta)}")
    for t in c2['aparecen_en_'+DESPUES]:
        print('  +', t)
    for t in c2['desaparecen_en_'+DESPUES]:
        print('  -', t)
    for v in (ANTES, DESPUES):
        iguales = sum(1 for r in c3[v].values() if r['iguales'])
        print(f"Consultas {v}: {iguales}/21 iguales a lo esperado; distintas: {[k for k, r in c3[v].items() if not r['iguales']]}")


if __name__ == '__main__':
    main(sys.argv[1])
