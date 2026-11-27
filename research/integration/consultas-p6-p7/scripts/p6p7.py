"""Ejecuta P6 y P7 por la plataforma (POST /api/sparql/execute) y las compara fila por fila con la respuesta
esperada (esperadas/, calculada por esperadas.py sin pasar por la red).
uso: python3 p6p7.py <etiqueta-del-estado> <salida.json>
"""
import json, sys
from pathlib import Path
from comun import http, guardar

BASE = Path(__file__).resolve().parent.parent


def filas(bindings):
    return {tuple(sorted((k, v['value']) for k, v in b.items())) for b in bindings}


def main(estado, salida):
    resultado = {'estado': estado, 'consultas': {}}
    for q in ('P6', 'P7'):
        texto = (BASE / 'consultas' / f'{q}.rq').read_text(encoding='utf-8')
        codigo, cuerpo = http('POST', '/sparql/execute', {'query': texto})
        obtenido = json.loads(cuerpo)['sparqlJson'] if codigo == 200 else {'error': cuerpo[:500]}
        esperado = json.loads((BASE / 'esperadas' / f'{q}.json').read_text(encoding='utf-8'))
        got = filas(obtenido.get('results', {}).get('bindings', [])) if codigo == 200 else set()
        want = filas(esperado['results']['bindings'])
        resultado['consultas'][q] = {'codigo': codigo, 'variables': obtenido.get('head', {}).get('vars'),
                                     'filas_obtenidas': len(got), 'filas_esperadas': len(want), 'iguales': got == want,
                                     'faltan': [dict(f) for f in sorted(want - got)], 'sobran': [dict(f) for f in sorted(got - want)],
                                     'respuesta': obtenido}
        print(f"{q} ({estado}): {len(got)} filas, esperadas {len(want)}, iguales {got == want}")
        for f in sorted(want - got):
            print('   falta', dict(f))
        for f in sorted(got - want):
            print('   sobra', dict(f))
    guardar(salida, resultado)


if __name__ == '__main__':
    main(*sys.argv[1:3])
