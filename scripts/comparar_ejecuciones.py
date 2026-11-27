#!/usr/bin/env python3
"""Compara los resultados funcionales de dos ejecuciones de la evaluación de PROTYS(KB).

Cubre functional.json, runs.jsonl, capacity.json, demonstration.json y census.json; si se indican,
también los cuerpos de las respuestas HTTP de la demostración y todas las respuestas SPARQL
guardadas. Antes de comparar quita lo que depende de la máquina o del momento y no del modelo:

- tiempos (claves terminadas en _ms o TimeMs), memoria (rss, heap), fechas y marcas de tiempo;
- diccionarios de hashes de archivos fuente y binarios (cambian si cambia cualquier archivo del
  árbol, aunque no lo lea el razonador);
- identificadores de proceso, de contenedor, UUID que asigna la API y puertos;
- el hash de cada respuesta HTTP (los cuerpos se comparan aparte, con --respuestas-*);
- la raíz del repositorio y los directorios temporales dentro de las rutas.

Todo lo demás se compara exactamente: estados, conteos de triples y axiomas, filas de cada
consulta, resultado de cada aserción y hashes de la materialización y de las deducciones SWRL.

Dos casos tienen un criterio propio y el informe los cuenta aparte:

- Hash de serialización (entrada OWL del razonador, materialización y deducciones SWRL): un valor
  distinto se acepta sólo si, con --crudos-*, aparecen los dos archivos con esos hashes y son
  grafos RDF isomorfos, es decir, los mismos triples salvo el nombre de los nodos en blanco. Con
  --ignorar-comentarios se acepta también, en una categoría aparte, si son isomorfos después de
  quitar los triples rdfs:comment. Requiere rdflib.
- Orden: las listas que devuelve la API y las filas de una respuesta SPARQL se aceptan en otro
  orden si contienen exactamente los mismos elementos.

Uso:
    python3 scripts/comparar_ejecuciones.py REFERENCIA CANDIDATA [--json SALIDA.json]
        [--census-referencia RUTA] [--census-candidata RUTA]
        [--respuestas-referencia DIR] [--respuestas-candidata DIR]
        [--crudos-referencia DIR] [--crudos-candidata DIR]

REFERENCIA y CANDIDATA son carpetas con functional.json, runs.jsonl, demonstration.json,
capacity.json (o capacity/capacity.json) y census.json. --crudos-* son las carpetas completas de
cada ejecución (research/evaluation/current). Imprime un resumen en Markdown. Código de salida:
0 sin diferencias, 1 con alguna diferencia, 2 ante un error de entrada.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

CLAVES_DICCIONARIO_HASH = {
    'input_hashes', 'input_hashes_before', 'input_hashes_after',
    'runtime_hashes', 'runtime_hashes_before', 'runtime_hashes_after',
    'source_hashes', 'runtime_binary_hashes', 'backend_artifact_hashes', 'catalog_sha256',
}
CLAVES_QUITADAS = re.compile(
    r'(_ms$|^ms$|TimeMs$|rss|heap|timestamp|_utc$|^pid$|_pid$|process_group|container_id'
    r'|container_name|postgres_port|private_tdb2_path|^response_sha256$)')
CLAVES_HASH_SERIALIZACION = re.compile(r'(\.swrl\.(?:input|materialized|deductions)_sha256|\.raw_input\.sha256'
                                       r'|\.request\.file_sha256)$')
UUID = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
SUSTITUCIONES = [
    # Raíz de cualquier copia del repositorio (por ejemplo /Users/<usuario>/.../protys-ws/).
    (re.compile(r'/(?:Users|home)/[^/\s"\']+/(?:[^/\s"\']+/)*?protys-ws[^/\s"\']*/'), ''),
    (re.compile(r'/(?:private/)?var/folders/[^\s"\']+'), '<TEMPORAL>'),
    (re.compile(r'127\.0\.0\.1:\d+'), '127.0.0.1:<PUERTO>'),
    (re.compile(r'localhost:\d+'), 'localhost:<PUERTO>'),
    (re.compile(r'protys-academic-[0-9a-f]{10}'), 'protys-academic-<ID>'),
    (re.compile(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?'), '<FECHA>'),
    (UUID, '<UUID>'),
]


def es_archivo_fuente(valor):
    ruta = valor.get('path')
    return isinstance(ruta, str) and ruta.startswith(('ontologies/', 'research/model/', 'research/data/'))


def normalizar(valor):
    if isinstance(valor, dict):
        # El SHA-256 de un archivo fuente (por ejemplo, cada componente del censo) es un hash de archivo.
        fuente = es_archivo_fuente(valor)
        return {k: normalizar(v) for k, v in valor.items()
                if k not in CLAVES_DICCIONARIO_HASH and not CLAVES_QUITADAS.search(k)
                and not (fuente and k == 'sha256')}
    if isinstance(valor, list):
        return [normalizar(v) for v in valor]
    if isinstance(valor, str):
        # Mensajes que terminan en un objeto JSON (por ejemplo, el informe del validador dentro de
        # un error de la API): se normaliza también ese objeto.
        inicio = valor.find('{')
        if inicio >= 0:
            try:
                incrustado = json.loads(valor[inicio:])
            except ValueError:
                incrustado = None
            if isinstance(incrustado, dict):
                return normalizar(valor[:inicio]) + canonica(normalizar(incrustado))
        for patron, reemplazo in SUSTITUCIONES:
            valor = patron.sub(reemplazo, valor)
    return valor


def canonica(valor):
    return json.dumps(valor, sort_keys=True, ensure_ascii=False)


def mismo_multiconjunto(a, b):
    return isinstance(a, list) and isinstance(b, list) and sorted(map(canonica, a)) == sorted(map(canonica, b))


def diferencias(a, b, ruta='', salida=None, orden=None, limite=500):
    """Agrega a salida (ruta, referencia, candidata) por cada valor distinto.

    Si se pasa la lista orden, una lista con los mismos elementos en otro orden se anota allí y no
    cuenta como diferencia.
    """
    if salida is None:
        salida = []
    if len(salida) >= limite:
        return salida
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b), key=str):
            if k not in a or k not in b:
                salida.append((f'{ruta}.{k}', a.get(k, '<ausente>'), b.get(k, '<ausente>')))
            else:
                diferencias(a[k], b[k], f'{ruta}.{k}', salida, orden, limite)
    elif isinstance(a, list) and isinstance(b, list):
        if a == b:
            return salida
        if orden is not None and mismo_multiconjunto(a, b):
            orden.append(ruta)
            return salida
        if len(a) != len(b):
            salida.append((f'{ruta}[longitud]', len(a), len(b)))
        for i, (x, y) in enumerate(zip(a, b)):
            diferencias(x, y, f'{ruta}[{i}]', salida, orden, limite)
    elif a != b:
        salida.append((ruta, a, b))
    return salida


def leer_json(ruta):
    return json.loads(Path(ruta).read_text(encoding='utf-8'))


def leer_runs(ruta):
    filas = {}
    for linea in Path(ruta).read_text(encoding='utf-8').splitlines():
        if linea.strip():
            r = json.loads(linea)
            filas[f"{r['configuration']}#r{r['replica']:02d}"] = r
    return filas


def ubicar(carpeta, *nombres):
    for n in nombres:
        p = Path(carpeta) / n
        if p.is_file():
            return p
    raise FileNotFoundError(f'No se encontró {nombres[0]} en {carpeta}')


def comparar_respuestas_http(dir_ref, dir_cand, orden):
    salida = []
    ref = {p.name: p for p in Path(dir_ref).glob('*.json')}
    cand = {p.name: p for p in Path(dir_cand).glob('*.json')}
    for nombre in sorted(set(ref) | set(cand)):
        if nombre not in ref or nombre not in cand:
            salida.append((nombre, nombre in ref, nombre in cand))
            continue
        diferencias(normalizar(leer_json(ref[nombre])), normalizar(leer_json(cand[nombre])), nombre, salida, orden)
    return salida


def comparar_respuestas_sparql(crudos_ref, crudos_cand, orden):
    """Compara cada respuesta SPARQL (Q*.json, R*.json y aserciones) con la misma ruta relativa."""
    salida, total = [], 0
    patrones = ['functional/*/[QR]*.json', 'runs/*/[QR]*.json', 'assertion-groups/**/*.json',
                'capacity/*/[QR]*.json']
    rel_ref, rel_cand = set(), set()
    for patron in patrones:
        rel_ref |= {p.relative_to(crudos_ref).as_posix() for p in Path(crudos_ref).glob(patron)}
        rel_cand |= {p.relative_to(crudos_cand).as_posix() for p in Path(crudos_cand).glob(patron)}
    for rel in sorted(rel_ref | rel_cand):
        if rel not in rel_ref or rel not in rel_cand:
            salida.append((rel, rel in rel_ref, rel in rel_cand))
            continue
        a, b = leer_json(Path(crudos_ref, rel)), leer_json(Path(crudos_cand, rel))
        if not (isinstance(a, dict) and ('results' in a or 'boolean' in a)):
            continue  # no es una respuesta SPARQL
        total += 1
        diferencias(normalizar(a), normalizar(b), rel, salida, orden)
    return salida, total


def explicar_por_isomorfismo(pendientes, crudos_ref, crudos_cand, ignorar_comentarios=False):
    """Separa las diferencias de hash de serialización cuyos archivos son grafos isomorfos."""
    import rdflib
    from rdflib.compare import isomorphic
    from rdflib.namespace import RDFS

    def sin_comentarios(g):
        h = rdflib.Graph()
        for t in g:
            if t[1] != RDFS.comment:
                h.add(t)
        return h

    buscados = {d[1] for d in pendientes} | {d[2] for d in pendientes}
    ubicados = {}
    for raiz in (crudos_ref, crudos_cand):
        for p in Path(raiz).rglob('*.owl'):
            h = hashlib.sha256(p.read_bytes()).hexdigest()
            if h in buscados:
                ubicados.setdefault(h, p)
    explicadas, no_explicadas, cache = [], [], {}
    for ruta, h_ref, h_cand in pendientes:
        par = (h_ref, h_cand)
        if par not in cache:
            if h_ref in ubicados and h_cand in ubicados:
                g_ref = rdflib.Graph().parse(ubicados[h_ref], format='xml')
                g_cand = rdflib.Graph().parse(ubicados[h_cand], format='xml')
                iso = isomorphic(g_ref, g_cand)
                criterio = 'isomorfos' if iso else None
                if not iso and ignorar_comentarios and len(g_ref) == len(g_cand):
                    if isomorphic(sin_comentarios(g_ref), sin_comentarios(g_cand)):
                        criterio = 'isomorfos sin rdfs:comment'
                cache[par] = (criterio, len(g_ref), len(g_cand))
            else:
                cache[par] = (None, None, None)
        criterio, n_ref, n_cand = cache[par]
        registro = {'ruta': ruta, 'referencia': h_ref, 'candidata': h_cand, 'criterio': criterio,
                    'triples_referencia': n_ref, 'triples_candidata': n_cand}
        (explicadas if criterio else no_explicadas).append(registro)
    return explicadas, no_explicadas


def resumir(valor):
    texto = json.dumps(valor, ensure_ascii=False) if not isinstance(valor, str) else valor
    return texto if len(texto) <= 120 else texto[:117] + '...'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('referencia')
    ap.add_argument('candidata')
    ap.add_argument('--json', help='escribe todas las diferencias en este archivo (no lo sobrescribe)')
    ap.add_argument('--census-referencia')
    ap.add_argument('--census-candidata')
    ap.add_argument('--respuestas-referencia')
    ap.add_argument('--respuestas-candidata')
    ap.add_argument('--crudos-referencia')
    ap.add_argument('--crudos-candidata')
    ap.add_argument('--ignorar-comentarios', action='store_true',
                    help='acepta hashes de serialización cuyos grafos sólo difieren en triples rdfs:comment')
    args = ap.parse_args(argv)
    if args.json and Path(args.json).exists():
        print(f'Error: {args.json} ya existe; no se sobrescribe.', file=sys.stderr)
        return 2

    orden = {}
    try:
        pares = {
            'functional.json': [ubicar(args.referencia, 'functional.json'), ubicar(args.candidata, 'functional.json')],
            'capacity.json': [ubicar(args.referencia, 'capacity.json', 'capacity/capacity.json'),
                              ubicar(args.candidata, 'capacity.json', 'capacity/capacity.json')],
            'demonstration.json': [ubicar(args.referencia, 'demonstration.json'),
                                   ubicar(args.candidata, 'demonstration.json')],
            'census.json': [Path(args.census_referencia) if args.census_referencia else ubicar(args.referencia, 'census.json'),
                            Path(args.census_candidata) if args.census_candidata else ubicar(args.candidata, 'census.json')],
        }
        resultado = {}
        for nombre, (r, c) in pares.items():
            orden[nombre] = []
            resultado[nombre] = diferencias(normalizar(leer_json(r)), normalizar(leer_json(c)), '', None, orden[nombre])
        rr = leer_runs(ubicar(args.referencia, 'runs.jsonl'))
        rc = leer_runs(ubicar(args.candidata, 'runs.jsonl'))
        orden['runs.jsonl'] = []
        resultado['runs.jsonl'] = []
        for clave in sorted(set(rr) | set(rc)):
            if clave not in rr or clave not in rc:
                resultado['runs.jsonl'].append((clave, clave in rr, clave in rc))
            else:
                diferencias(normalizar(rr[clave]), normalizar(rc[clave]), clave, resultado['runs.jsonl'],
                            orden['runs.jsonl'])
        if args.respuestas_referencia and args.respuestas_candidata:
            orden['demonstration/http-responses'] = []
            resultado['demonstration/http-responses'] = comparar_respuestas_http(
                args.respuestas_referencia, args.respuestas_candidata, orden['demonstration/http-responses'])
        respuestas_sparql = None
        if args.crudos_referencia and args.crudos_candidata:
            orden['respuestas SPARQL crudas'] = []
            resultado['respuestas SPARQL crudas'], respuestas_sparql = comparar_respuestas_sparql(
                args.crudos_referencia, args.crudos_candidata, orden['respuestas SPARQL crudas'])
    except (OSError, ValueError, KeyError) as e:
        print(f'Error: {e}', file=sys.stderr)
        return 2

    # Hashes de serialización: se separan y, si hay ejecuciones crudas, se prueban por isomorfismo.
    pendientes = [(f'{n}:{d[0]}', d[1], d[2]) for n, difs in resultado.items() for d in difs
                  if CLAVES_HASH_SERIALIZACION.search(str(d[0]))]
    for n in resultado:
        resultado[n] = [d for d in resultado[n] if not CLAVES_HASH_SERIALIZACION.search(str(d[0]))]
    explicadas, no_explicadas = [], [{'ruta': r, 'referencia': a, 'candidata': b, 'criterio': None}
                                     for r, a, b in pendientes]
    if pendientes and args.crudos_referencia and args.crudos_candidata:
        explicadas, no_explicadas = explicar_por_isomorfismo(pendientes, args.crudos_referencia, args.crudos_candidata,
                                                             args.ignorar_comentarios)

    total = sum(len(v) for v in resultado.values()) + len(no_explicadas)
    L = ['# Comparación de resultados funcionales', '',
         f'- Referencia: `{args.referencia}`', f'- Candidata: `{args.candidata}`',
         f'- Réplicas comparadas: {len(rr)} de referencia, {len(rc)} candidatas']
    if respuestas_sparql is not None:
        L.append(f'- Respuestas SPARQL crudas comparadas: {respuestas_sparql}')
    L += ['', f'**Diferencias: {total}**', '', '| Archivo | Diferencias | Listas en otro orden con los mismos elementos |',
          '|---|---:|---:|']
    L += [f'| {n} | {len(v)} | {len(orden.get(n, []))} |' for n, v in resultado.items()]
    L += [f'| hashes de serialización sin explicar | {len(no_explicadas)} | |', '',
          f"Hashes de serialización distintos con grafos isomorfos: {sum(e['criterio'] == 'isomorfos' for e in explicadas)}.",
          f"Hashes de serialización distintos con grafos isomorfos sólo después de quitar los triples rdfs:comment: "
          f"{sum(e['criterio'] == 'isomorfos sin rdfs:comment' for e in explicadas)}."]
    if explicadas:
        vistos = {}
        for e in explicadas:
            clave = (e['ruta'].split(':', 1)[0], re.sub(r'\[\d+\]|#r\d+', '', e['ruta'].split(':', 1)[1]).split('.')[-1], e['criterio'])
            vistos.setdefault(clave, [0, set()])
            vistos[clave][0] += 1
            vistos[clave][1].add((e['triples_referencia'], e['triples_candidata']))
        L += ['', '| Archivo | Campo | Criterio | Apariciones | Triples (ref. / cand.) |', '|---|---|---|---:|---|']
        L += [f'| {a} | `{c}` | {k} | {v[0]} | {", ".join(f"{x} / {y}" for x, y in sorted(v[1]))} |'
              for (a, c, k), v in vistos.items()]
    for nombre, difs in resultado.items():
        if difs:
            L += ['', f'## {nombre}', '', '| Ruta | Referencia | Candidata |', '|---|---|---|']
            L += [f'| `{d[0]}` | {resumir(d[1])} | {resumir(d[2])} |' for d in difs[:100]]
    if no_explicadas:
        L += ['', '## Hashes de serialización sin explicar', '']
        L += [f'- `{e["ruta"]}`: {e["referencia"]} → {e["candidata"]}' for e in no_explicadas]
    sys.stdout.write('\n'.join(L) + '\n')

    if args.json:
        Path(args.json).write_text(json.dumps({
            'diferencias': {k: [list(d) for d in v] for k, v in resultado.items()},
            'listas_en_otro_orden': orden,
            'hashes_de_serializacion_isomorfos': explicadas,
            'hashes_de_serializacion_sin_explicar': no_explicadas,
        }, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main())
