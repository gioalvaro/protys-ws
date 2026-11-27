"""Cargas de 20, 50, 100 y 200 lotes con el generador del caso.

Usa `load_scenario(lotes, semilla)` y `Turtle.projection('union')` de research/data/generate_dataset.py sin
modificarlo, con la semilla de la evaluación (42) y sus parámetros. El generador sólo trae 20, 100 y 200 lotes
(small, medium, large); 50 se obtiene con la misma función. Las cargas se escriben en cargas/, junto a este
guion, no en research/data/.

Control: las cargas de 20, 100 y 200 lotes deben ser idénticas byte a byte a load-small, load-medium y load-large
de research/data/ (y sus proyecciones «union»).

uso: python3 cargas.py
"""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from comun import RAIZ, guardar, sha256  # noqa: E402
PAQ = Path(__file__).resolve().parent

sys.path.insert(0, str(RAIZ / 'research/data'))
import generate_dataset as gen  # noqa: E402

SEMILLA = 42
CANONICAS = {20: 'small', 100: 'medium', 200: 'large'}


def main():
    destino = PAQ / 'cargas'
    manifiesto = {'semilla': SEMILLA, 'generador': 'research/data/generate_dataset.py', 'generador_sha256': sha256(RAIZ / 'research/data/generate_dataset.py'),
                  'funciones': 'load_scenario(lotes, 42) y Turtle.projection("union")', 'archivos': []}
    for lotes in (20, 50, 100, 200):
        g, _ = gen.load_scenario(lotes, SEMILLA)
        for nombre, grafo in ((f'load-{lotes}.ttl', g), (f'load-{lotes}-union.ttl', g.projection('union'))):
            meta = grafo.write(destino / nombre)
            meta['lotes'] = lotes
            meta['configuracion'] = 'union' if nombre.endswith('-union.ttl') else 'integrated'
            if lotes in CANONICAS:
                canon = RAIZ / 'research/data' / nombre.replace(str(lotes), CANONICAS[lotes])
                meta['canonica'] = str(canon.relative_to(RAIZ))
                meta['identica_a_la_canonica'] = sha256(canon) == meta['sha256']
            manifiesto['archivos'].append(meta)
            print(json.dumps(meta, ensure_ascii=False))
    guardar(destino / 'manifiesto.json', manifiesto)
    if not all(m.get('identica_a_la_canonica', True) for m in manifiesto['archivos']):
        raise SystemExit('una carga regenerada no coincide con la canónica')


if __name__ == '__main__':
    main()
