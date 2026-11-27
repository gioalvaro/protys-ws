#!/usr/bin/env python3
"""Comprobación de sintaxis de los ejemplos, consultas y validaciones de research/model/.

Pasa al analizador del validador académico (org.protys.research.SyntaxCheck) todos los archivos
.ttl y .sparql de research/model/examples/, research/model/queries/ y research/model/validations/,
en orden alfabético de ruta. Escribe runtime/syntax-check.json y runtime/syntax-check-stderr.txt
dentro de la carpeta de la ejecución. Sólo comprueba sintaxis: no evalúa perfil OWL, consistencia
ni reglas.

Uso (desde la raíz, con JAVA_HOME en un JDK 17 y los ejecutores preparados):
    python3 research/evaluation/comprobar_sintaxis.py [--salida research/evaluation/current]
"""
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
CARPETAS = ['research/model/examples', 'research/model/queries', 'research/model/validations']


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--salida', type=Path, default=RAIZ / 'research/evaluation/current')
    args = ap.parse_args()
    archivos = sorted(str(p.relative_to(RAIZ)) for c in CARPETAS for p in (RAIZ / c).iterdir()
                      if p.suffix in ('.ttl', '.sparql'))
    target = RAIZ / 'research/runtime/validator/target'
    cp = os.pathsep.join([str(target / 'classes'), (target / 'classpath.txt').read_text().strip()])
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    destino = args.salida / 'runtime'
    destino.mkdir(parents=True, exist_ok=True)
    r = subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', cp, 'org.protys.research.SyntaxCheck', *archivos],
                       cwd=RAIZ, capture_output=True, text=True)
    (destino / 'syntax-check.json').write_text(r.stdout)
    (destino / 'syntax-check-stderr.txt').write_text(r.stderr)
    informe = json.loads(r.stdout)
    fallas = [c['path'] for c in informe['checks'] if c['status'] != 'PASS']
    print(f"{informe['status']}: {len(informe['checks'])} archivos, {len(fallas)} con error")
    for f in fallas:
        print('  ' + f)
    return r.returncode


if __name__ == '__main__':
    sys.exit(main())
