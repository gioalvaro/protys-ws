#!/usr/bin/env python3
"""Corre las 21 consultas con arq de Apache Jena sobre el grafo materializado exportado.

Para cada research/model/queries/Qnn.sparql ejecuta `arq --data GRAFO --query Qnn.sparql
--results=JSON` y compara las filas con research/model/expected/Qnn.json, la respuesta esperada de
la configuración integrada, con el mismo criterio que el evaluador: mismas variables, filas como
multiconjunto, literales numéricos iguales si su diferencia relativa no supera 1e-9 (las
respuestas esperadas se calcularon en Python con 28 dígitos; Jena divide con 24) y xsd:string
equivalente a un literal simple. Además compara, textualmente, con la respuesta que el evaluador
guardó para la misma consulta (functional/integrated/Qnn.json de la ejecución), si se indica
--ejecucion.

Uso:
    python3 scripts/consultar_grafo_exportado.py --arq RUTA/apache-jena-4.10.0/bin/arq \
        [--grafo research/release/grafo-materializado/integrado-funcional-materializado.ttl] \
        [--salida research/release/grafo-materializado/consultas-arq] [--ejecucion research/evaluation/current]
"""
import argparse
import json
import subprocess
import sys
from decimal import Decimal, InvalidOperation
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
XSD = 'http://www.w3.org/2001/XMLSchema#'
NUMERICOS = {XSD + n for n in ('integer', 'decimal', 'double', 'float', 'int', 'long', 'short',
                               'nonNegativeInteger', 'positiveInteger')}


def igual_valor(a, b):
    if a.get('type') != b.get('type'):
        return False
    if a.get('type') == 'literal' and a.get('datatype') in NUMERICOS and b.get('datatype') in NUMERICOS:
        try:
            x, y = Decimal(a['value']), Decimal(b['value'])
        except InvalidOperation:
            return False
        return abs(x - y) <= max(Decimal('1e-9'), Decimal('1e-9') * max(abs(x), abs(y)))
    simple = lambda v: {k: w for k, w in v.items() if not (k == 'datatype' and w == XSD + 'string')}
    return simple(a) == simple(b)


def coincide(obtenida, esperada):
    if set(obtenida['head'].get('vars', [])) != set(esperada['head'].get('vars', [])):
        return False
    got = obtenida['results']['bindings']
    want = esperada['results']['bindings']
    if len(got) != len(want):
        return False
    candidatos = [[i for i, g in enumerate(got) if g.keys() == w.keys() and all(igual_valor(g[k], v) for k, v in w.items())]
                  for w in want]
    asignadas = {}

    def asignar(j, vistas):
        for i in candidatos[j]:
            if i in vistas:
                continue
            vistas.add(i)
            if i not in asignadas or asignar(asignadas[i], vistas):
                asignadas[i] = j
                return True
        return False
    return all(asignar(j, set()) for j in range(len(want)))


def textual(a, b):
    clave = lambda r: sorted(json.dumps(x, sort_keys=True, ensure_ascii=False) for x in r['results']['bindings'])
    return set(a['head'].get('vars', [])) == set(b['head'].get('vars', [])) and clave(a) == clave(b)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--arq', required=True)
    ap.add_argument('--grafo', type=Path,
                    default=RAIZ / 'research/release/grafo-materializado/integrado-funcional-materializado.ttl')
    ap.add_argument('--salida', type=Path, default=RAIZ / 'research/release/grafo-materializado/consultas-arq')
    ap.add_argument('--ejecucion', type=Path, help='carpeta de la ejecución (research/evaluation/current)')
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)
    version = subprocess.run([args.arq, '--version'], capture_output=True, text=True).stdout.strip()

    informe, coinciden = [], 0
    for consulta in sorted((RAIZ / 'research/model/queries').glob('Q*.sparql')):
        ident = consulta.stem
        r = subprocess.run([args.arq, '--data', str(args.grafo), '--query', str(consulta), '--results=JSON'],
                           capture_output=True, text=True)
        fila = {'consulta': ident, 'codigo': r.returncode}
        if r.returncode != 0:
            fila.update(estado='ERROR', error=r.stderr.strip()[:500])
            informe.append(fila)
            continue
        obtenida = json.loads(r.stdout)
        (args.salida / f'{ident}.json').write_text(json.dumps(obtenida, ensure_ascii=False, indent=1) + '\n',
                                                    encoding='utf-8')
        esperada = json.loads((RAIZ / 'research/model/expected' / f'{ident}.json').read_text(encoding='utf-8'))
        igual = coincide(obtenida, esperada)
        fila.update(estado='COINCIDE' if igual else 'DISTINTA',
                    filas_obtenidas=len(obtenida['results']['bindings']),
                    filas_esperadas=len(esperada['results']['bindings']))
        if args.ejecucion:
            evaluador = json.loads((args.ejecucion / 'functional/integrated' / f'{ident}.json').read_text(encoding='utf-8'))
            fila['igual_al_evaluador'] = textual(obtenida, evaluador)
        coinciden += igual
        informe.append(fila)

    resumen = {'arq': version, 'grafo': args.grafo.name, 'consultas': len(informe), 'coinciden': coinciden,
               'iguales_al_evaluador': sum(1 for f in informe if f.get('igual_al_evaluador')),
               'detalle': informe}
    (args.salida / 'resumen.json').write_text(json.dumps(resumen, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{version}\n{coinciden} de {len(informe)} consultas coinciden con research/model/expected/')
    for f in informe:
        extra = (f"; igual al evaluador: {'sí' if f['igual_al_evaluador'] else 'no'}" if 'igual_al_evaluador' in f else '')
        print(f"  {f['consulta']}: {f['estado']}" + (f" ({f['filas_obtenidas']} filas{extra})"
                                                    if 'filas_obtenidas' in f else f" {f.get('error', '')[:200]}"))
    return 0 if coinciden == len(informe) else 1


if __name__ == '__main__':
    sys.exit(main())
