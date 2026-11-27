#!/usr/bin/env python3
"""Sondas de compatibilidad de HermiT con OWL API 5.1.20.

Ejecuta el validador académico (research/runtime/validator) sobre dos ontologías mínimas:

- version-compatibility/1.4.5.519-valid: HermiT 1.4.5.519, ontología válida y una clasificación ASK;
- version-compatibility/1.4.5.519-inconsistent: HermiT 1.4.5.519, ontología inconsistente;
- version-compatibility/1.4.5.456-valid: la misma ontología válida con HermiT 1.4.5.456 en lugar
  de 1.4.5.519 en el classpath.

Escribe compatibility.json con el resumen. Requiere haber preparado los ejecutores
(./research/reproduce.sh --prepare-only o la fase funcional) y JAVA_HOME en un JDK 17.

Uso:
    python3 research/evaluation/version-compatibility/ejecutar_sondas.py --hermit-456 RUTA_AL_JAR
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[2]
VC = 'research/evaluation/version-compatibility'


def classpath(hermit_456=None):
    target = RAIZ / 'research/runtime/validator/target'
    entradas = (target / 'classpath.txt').read_text().strip().split(os.pathsep)
    if hermit_456:
        entradas = [hermit_456 if '/org.semanticweb.hermit/1.4.5.519/' in e else e for e in entradas]
        if hermit_456 not in entradas:
            raise SystemExit('No se encontró HermiT 1.4.5.519 en el classpath del validador')
    return os.pathsep.join([str(target / 'classes')] + entradas)


def ejecutar(solicitud, salida, cp):
    """Escribe la solicitud, vacía la carpeta de salida y ejecuta el validador."""
    salida = RAIZ / salida
    if salida.exists():
        shutil.rmtree(salida)
    salida.mkdir(parents=True)
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    orden = [str(java), '-Djava.awt.headless=true', '-cp', cp, 'org.protys.research.AcademicRunner',
             '.', str(solicitud), str(salida.relative_to(RAIZ))]
    r = subprocess.run(orden, cwd=RAIZ, capture_output=True, text=True)
    (salida / 'stdout.txt').write_text(r.stdout)
    (salida / 'stderr.txt').write_text(r.stderr)
    return r.returncode


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--hermit-456', required=True, help='JAR de org.semanticweb.hermit 1.4.5.456')
    args = ap.parse_args()
    hermit_456 = str(Path(args.hermit_456).resolve())

    casos = [
        ('1.4.5.456', 'valid', f'{VC}/1.4.5.456-valid', classpath(hermit_456),
         {'files': [f'{VC}/valid.ttl'], 'enabled_rules': 'NONE',
          'queries': [{'id': 'DL_CLASSIFICATION', 'path': f'{VC}/classify.sparql'}]}),
        ('1.4.5.519', 'valid', f'{VC}/1.4.5.519-valid', classpath(),
         {'files': [f'{VC}/valid.ttl'], 'enabled_rules': 'NONE',
          'queries': [{'id': 'DL_CLASSIFICATION', 'path': f'{VC}/classify.sparql'}]}),
        ('1.4.5.519', 'inconsistent', f'{VC}/1.4.5.519-inconsistent', classpath(),
         {'files': [f'{VC}/inconsistent.ttl'], 'enabled_rules': 'NONE', 'queries': []}),
    ]
    checks = []
    for hermit, caso, carpeta, cp, solicitud in casos:
        temporal = RAIZ / carpeta.replace('version-compatibility/', 'version-compatibility/.solicitud-')
        temporal = temporal.with_suffix('.json')
        temporal.write_text(json.dumps(solicitud, indent=2) + '\n')
        codigo = ejecutar(temporal.relative_to(RAIZ), carpeta, cp)
        shutil.move(temporal, RAIZ / carpeta / 'request.json')
        validacion = json.loads((RAIZ / carpeta / 'validation.json').read_text())
        fila = {'HermiT': hermit, 'OWLAPI': '5.1.20', 'case': caso, 'exit_code': codigo, 'validation': validacion}
        clasif = RAIZ / carpeta / 'DL_CLASSIFICATION.json'
        if clasif.exists():
            fila['classified_parent_type'] = json.loads(clasif.read_text()).get('boolean')
        checks.append(fila)
        print(hermit, caso, codigo, validacion.get('status'))

    elegido = next(c for c in checks if c['HermiT'] == '1.4.5.519' and c['case'] == 'valid')
    inconsistente = next(c for c in checks if c['case'] == 'inconsistent')
    estado = ('PASS' if elegido['validation'].get('status') == 'CONSISTENT' and elegido.get('classified_parent_type')
              and inconsistente['validation'].get('status') == 'INCONSISTENT' else 'FAIL')
    (RAIZ / VC / 'compatibility.json').write_text(json.dumps(
        {'status': estado, 'selected_HermiT': '1.4.5.519', 'checks': checks}, indent=2, ensure_ascii=False) + '\n')

    print('compatibility.json:', estado)
    return 0 if estado == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
