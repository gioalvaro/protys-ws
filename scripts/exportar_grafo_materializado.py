#!/usr/bin/env python3
"""Exporta el grafo materializado de la configuración integrada del conjunto funcional.

Es el modelo sobre el que el ejecutor corrió las 21 consultas: entradas, deducciones OWL, reglas
SWRL y las dos construcciones previas, en un único grafo por defecto (sin grafos con nombre). El
ejecutor lo guarda en functional/integrated/result-model.ttl. Este guion lo deja en un solo
archivo Turtle y en RDF/XML (para Protégé), comprueba con Jena RIOT que ambos tengan la cantidad de
triples que registra functional.json y escribe un resumen con los hashes.

Usa Jena 4.10.0 del classpath del validador (research/runtime/validator/target/classpath.txt).

Uso (desde la raíz, con JAVA_HOME en un JDK 17):
    python3 scripts/exportar_grafo_materializado.py [--ejecucion research/evaluation/current]
        [--destino research/release/grafo-materializado]
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
NOMBRE = 'integrado-funcional-materializado'


def jena(*argumentos, salida=None):
    target = RAIZ / 'research/runtime/validator/target'
    cp = os.pathsep.join([str(target / 'classes'), (target / 'classpath.txt').read_text().strip()])
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    orden = [str(java), '-cp', cp, *argumentos]
    if salida:
        with open(salida, 'wb') as destino:
            r = subprocess.run(orden, cwd=RAIZ, stdout=destino, stderr=subprocess.PIPE, check=True)
        return r.stderr.decode()
    r = subprocess.run(orden, cwd=RAIZ, capture_output=True, check=True)
    return r.stdout.decode() + r.stderr.decode()


def contar(ruta):
    texto = jena('riotcmd.riot', '--count', str(ruta))
    # Formato de RIOT: "<archivo> : Triples = N"
    for linea in texto.splitlines():
        if 'Triples' in linea:
            return int(linea.split('=')[-1].strip().replace(',', ''))
    raise RuntimeError('RIOT no informó la cantidad de triples: ' + texto)


def sha256(ruta):
    return hashlib.sha256(Path(ruta).read_bytes()).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--ejecucion', type=Path, default=RAIZ / 'research/evaluation/current')
    ap.add_argument('--destino', type=Path, default=RAIZ / 'research/release/grafo-materializado')
    args = ap.parse_args()

    origen = args.ejecucion / 'functional/integrated/result-model.ttl'
    funcional = json.loads((args.ejecucion / 'functional.json').read_text(encoding='utf-8'))
    integrada = next(c['result'] for c in funcional['configurations'] if c['id'] == 'integrated')
    esperados = integrada['materialized_triples']

    args.destino.mkdir(parents=True, exist_ok=True)
    ttl = args.destino / (NOMBRE + '.ttl')
    rdf = args.destino / (NOMBRE + '.rdf')
    shutil.copyfile(origen, ttl)
    jena('riotcmd.riot', '--output=RDFXML', str(ttl), salida=rdf)

    conteos = {'turtle': contar(ttl), 'rdfxml': contar(rdf)}
    if any(n != esperados for n in conteos.values()):
        print(f'Cantidad de triples distinta de la registrada ({esperados}): {conteos}', file=sys.stderr)
        return 1
    resumen = {
        'configuracion': 'integrated',
        'conjunto': 'funcional (research/data/functional.ttl)',
        'triples_de_entrada': integrada['input_triples'],
        'triples_materializados': esperados,
        'grafos_con_nombre': 0,
        'origen': str(origen.relative_to(RAIZ)) if origen.is_relative_to(RAIZ) else str(origen),
        'origen_sha256': sha256(origen),
        'archivos': {p.name: {'sha256': sha256(p), 'bytes': p.stat().st_size, 'triples': n}
                     for p, n in [(ttl, conteos['turtle']), (rdf, conteos['rdfxml'])]},
    }
    (args.destino / 'resumen.json').write_text(json.dumps(resumen, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{esperados} triples en {ttl.name} y {rdf.name}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
