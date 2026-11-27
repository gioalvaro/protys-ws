#!/usr/bin/env python3
"""Copia los resúmenes de una ejecución a research/release/ y registra que las copias son idénticas.

Copia functional.json, summary.json, summary.csv, environment.json, demonstration.json,
capacity/capacity.json y runs.jsonl desde la carpeta de la ejecución a research/release/, compara el
SHA-256 de cada copia con su origen y escribe research/release/CONSERVACION_RESULTADOS.json.

Uso:
    python3 scripts/conservar_resultados.py [--ejecucion research/evaluation/current]
                                            [--destino research/release] [--reemplazar]

Sin --reemplazar, se niega a pisar un archivo de destino que ya exista con otro contenido.
"""
import argparse
import datetime
import hashlib
import json
import shutil
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ARCHIVOS = [
    ('functional.json', 'functional.json'),
    ('summary.json', 'summary.json'),
    ('summary.csv', 'summary.csv'),
    ('environment.json', 'environment.json'),
    ('demonstration.json', 'demonstration.json'),
    ('capacity/capacity.json', 'capacity.json'),
    ('runs.jsonl', 'runs.jsonl'),
]


def sha256(ruta):
    return hashlib.sha256(ruta.read_bytes()).hexdigest()


def relativa(ruta):
    try:
        return str(ruta.resolve().relative_to(RAIZ))
    except ValueError:
        return str(ruta)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--ejecucion', type=Path, default=RAIZ / 'research/evaluation/current')
    parser.add_argument('--destino', type=Path, default=RAIZ / 'research/release')
    parser.add_argument('--reemplazar', action='store_true')
    args = parser.parse_args()

    faltan = [o for o, _ in ARCHIVOS if not (args.ejecucion / o).is_file()]
    if faltan:
        print('Faltan en la ejecución: ' + ', '.join(faltan), file=sys.stderr)
        return 2
    if not args.reemplazar:
        conflictos = [d for o, d in ARCHIVOS
                      if (args.destino / d).exists() and sha256(args.destino / d) != sha256(args.ejecucion / o)]
        if conflictos:
            print('Ya existen con otro contenido (usar --reemplazar): ' + ', '.join(conflictos), file=sys.stderr)
            return 2

    args.destino.mkdir(parents=True, exist_ok=True)
    copias = []
    for origen, destino in ARCHIVOS:
        o, d = args.ejecucion / origen, args.destino / destino
        shutil.copyfile(o, d)
        h_o, h_d = sha256(o), sha256(d)
        if h_o != h_d:
            print(f'La copia de {origen} no es idéntica', file=sys.stderr)
            return 1
        copias.append({'source': relativa(o), 'copy': relativa(d), 'bytes': d.stat().st_size, 'sha256': h_d})

    registro = {
        'status': 'IDENTICAL_RESULT_COPIES_PREPARED_FOR_PUBLICATION',
        'timestamp_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'copies': copias,
        'limits': 'La publicación remota se comprueba aparte contra el commit, la etiqueta y los adjuntos.',
    }
    (args.destino / 'CONSERVACION_RESULTADOS.json').write_text(
        json.dumps(registro, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{len(copias)} copias idénticas en {relativa(args.destino)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
