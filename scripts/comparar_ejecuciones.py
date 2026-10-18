#!/usr/bin/env python3
"""Compara dos informes funcionales (functional.json) de research/reproduce.sh.

Sirve para medir el efecto de un cambio del modelo (por ejemplo, un comentario
de la directora que obliga a agregar un axioma) contra una ejecución de
referencia. No modifica ninguna de las dos ejecuciones ni las respuestas
esperadas: sólo lee y resume diferencias.

Uso:
    python3 scripts/comparar_ejecuciones.py BASE CANDIDATA [--json SALIDA.json]

BASE y CANDIDATA pueden ser la carpeta de una ejecución (que contiene
functional.json) o la ruta directa al archivo. Sin --json, imprime un resumen en
Markdown por la salida estándar. El código de salida es 0 si no hay diferencias
de resultados (estado, consistencia, consultas, comprobaciones y aserciones),
1 si las hay y 2 ante un error de entrada. Las diferencias de tiempos no cuentan
como diferencias de resultados.
"""
import argparse
import json
import sys
from pathlib import Path

# Campos de cada configuración que describen resultados lógicos, no tiempos.
CAMPOS_RESULTADO = (
    'status', 'owl_validation_status', 'profile_valid', 'unsatisfiable_classes',
    'profile_violations', 'swrl_rule_count', 'input_named_individuals',
    'materialized_named_individuals', 'input_triples', 'dl_materialized_triples',
    'construct_added_triples', 'materialized_triples',
)
CAMPOS_ASERCION = (
    'pass', 'execution_status', 'owl_status', 'failure_kind', 'numeric_status',
    'cleaning_status', 'context_status',
)


def cargar(ruta):
    p = Path(ruta)
    if p.is_dir():
        p = p / 'functional.json'
    if not p.is_file():
        raise FileNotFoundError('No se encontró functional.json en ' + str(ruta))
    return p, json.loads(p.read_text(encoding='utf-8'))


def por_id(items, *claves):
    return {tuple(i.get(c) for c in claves): i for i in items or []}


def comparar(base, cand):
    out = {'estado_global': None, 'configuraciones': [], 'consultas': [],
           'comprobaciones': [], 'aserciones': [], 'archivos_de_entrada': {}}
    if base.get('status') != cand.get('status'):
        out['estado_global'] = {'base': base.get('status'), 'candidata': cand.get('status')}

    cb = {c['id']: c.get('result', {}) for c in base.get('configurations', [])}
    cc = {c['id']: c.get('result', {}) for c in cand.get('configurations', [])}
    for cid in sorted(set(cb) | set(cc)):
        rb, rc = cb.get(cid), cc.get(cid)
        if rb is None or rc is None:
            out['configuraciones'].append({'configuracion': cid, 'campo': '(presencia)',
                                           'base': rb is not None, 'candidata': rc is not None})
            continue
        for campo in CAMPOS_RESULTADO:
            if rb.get(campo) != rc.get(campo):
                out['configuraciones'].append({'configuracion': cid, 'campo': campo,
                                               'base': rb.get(campo), 'candidata': rc.get(campo)})
        qb = {q['id']: q.get('result_count') for q in rb.get('queries', [])}
        qc = {q['id']: q.get('result_count') for q in rc.get('queries', [])}
        for qid in sorted(set(qb) | set(qc)):
            if qb.get(qid) != qc.get(qid):
                out['consultas'].append({'configuracion': cid, 'consulta': qid,
                                         'filas_base': qb.get(qid), 'filas_candidata': qc.get(qid)})

    gb = por_id(base.get('golden_checks'), 'configuration', 'id')
    gc = por_id(cand.get('golden_checks'), 'configuration', 'id')
    for k in sorted(set(gb) | set(gc), key=str):
        a, b = gb.get(k, {}), gc.get(k, {})
        if (a.get('pass'), a.get('actual_rows')) != (b.get('pass'), b.get('actual_rows')):
            out['comprobaciones'].append({'configuracion': k[0], 'consulta': k[1],
                                          'pasa_base': a.get('pass'), 'pasa_candidata': b.get('pass'),
                                          'filas_base': a.get('actual_rows'),
                                          'filas_candidata': b.get('actual_rows')})

    ab = por_id(base.get('assertions'), 'id')
    ac = por_id(cand.get('assertions'), 'id')
    for k in sorted(set(ab) | set(ac), key=str):
        a, b = ab.get(k, {}), ac.get(k, {})
        cambios = {c: [a.get(c), b.get(c)] for c in CAMPOS_ASERCION if a.get(c) != b.get(c)}
        if cambios:
            out['aserciones'].append({'asercion': k[0], 'cambios': cambios})

    hb, hc = base.get('input_hashes', {}), cand.get('input_hashes', {})
    out['archivos_de_entrada'] = {
        'modificados': sorted(p for p in set(hb) & set(hc) if hb[p] != hc[p]),
        'agregados': sorted(set(hc) - set(hb)),
        'eliminados': sorted(set(hb) - set(hc)),
    }
    return out


def hay_diferencias_de_resultado(d):
    return bool(d['estado_global'] or d['configuraciones'] or d['consultas']
                or d['comprobaciones'] or d['aserciones'])


def markdown(d, ruta_base, ruta_cand):
    L = ['# Comparación de ejecuciones funcionales', '',
         '- Base: `%s`' % ruta_base, '- Candidata: `%s`' % ruta_cand, '']
    L.append('**Diferencias de resultados:** %s' % ('sí' if hay_diferencias_de_resultado(d) else 'no'))
    L.append('')
    if d['estado_global']:
        L += ['## Estado global', '', '%(base)s → %(candidata)s' % d['estado_global'], '']
    secciones = (
        ('Configuraciones', 'configuraciones', ('configuracion', 'campo', 'base', 'candidata')),
        ('Consultas (cantidad de filas)', 'consultas', ('configuracion', 'consulta', 'filas_base', 'filas_candidata')),
        ('Comprobaciones contra respuestas esperadas', 'comprobaciones',
         ('configuracion', 'consulta', 'pasa_base', 'pasa_candidata', 'filas_base', 'filas_candidata')),
    )
    for titulo, clave, cols in secciones:
        L += ['## ' + titulo, '']
        if not d[clave]:
            L += ['Sin diferencias.', '']
            continue
        L.append('| ' + ' | '.join(cols) + ' |')
        L.append('|' + '---|' * len(cols))
        for fila in d[clave]:
            L.append('| ' + ' | '.join(str(fila.get(c)) for c in cols) + ' |')
        L.append('')
    L += ['## Aserciones', '']
    if not d['aserciones']:
        L += ['Sin diferencias.', '']
    for a in d['aserciones']:
        L.append('- %s: %s' % (a['asercion'], ', '.join('%s %s → %s' % (c, v[0], v[1]) for c, v in a['cambios'].items())))
    f = d['archivos_de_entrada']
    L += ['', '## Archivos de entrada', '',
          'Modificados: %d. Agregados: %d. Eliminados: %d.' % (len(f['modificados']), len(f['agregados']), len(f['eliminados'])), '']
    for clave in ('modificados', 'agregados', 'eliminados'):
        for p in f[clave]:
            L.append('- %s: `%s`' % (clave[:-1], p))
    return '\n'.join(L) + '\n'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('base')
    ap.add_argument('candidata')
    ap.add_argument('--json', help='escribe la comparación completa en este archivo JSON')
    args = ap.parse_args(argv)
    try:
        pb, base = cargar(args.base)
        pc, cand = cargar(args.candidata)
    except (OSError, ValueError) as e:
        print('Error: %s' % e, file=sys.stderr)
        return 2
    d = comparar(base, cand)
    if args.json:
        destino = Path(args.json)
        if destino.exists():
            print('Error: %s ya existe; no se sobrescribe.' % destino, file=sys.stderr)
            return 2
        destino.write_text(json.dumps(d, ensure_ascii=False, indent=1), encoding='utf-8')
    sys.stdout.write(markdown(d, pb, pc))
    return 1 if hay_diferencias_de_resultado(d) else 0


if __name__ == '__main__':
    sys.exit(main())
