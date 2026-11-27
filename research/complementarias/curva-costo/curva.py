"""Curva de costo con el protocolo de la evaluación de rendimiento (research/README.md).

- Cargas de 20, 50, 100 y 200 lotes (cargas/, generadas con cargas.py).
- Dos configuraciones del catálogo: «integrated» (red integrada, 22 reglas, 4 ASK, construcciones previas) con
  load-N.ttl, y «union» (unión sin correspondencias ni reglas) con load-N-union.ttl, igual que benchmark_abox.
- 30 repeticiones por par (carga, configuración), 240 procesos JVM nuevos, en orden aleatorio con la semilla de la
  evaluación (20261003). Cada repetición: comun.ejecutar (copia de execute de run_evaluation.py: -Xmx2g, trabajador
  SWRL con -Xmx2g, RSS del árbol de procesos cada 100 ms, límite de 240 s).
- Por par: media, desviación estándar muestral y mediana del tiempo interno (total_ms), del
  tiempo de proceso (wall_process_ms) y del máximo observado de RSS; individuos y triples de entrada y triples
  inferidos (materializados - entrada; además los contadores del motor).

uso: python3 curva.py <carpeta-salida> <carpeta-temporal> [repeticiones]
"""
import csv, json, random, shutil, statistics, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from comun import RAIZ, ejecutar, entorno, guardar, peticion, sha256  # noqa: E402
PAQ = Path(__file__).resolve().parent

SEMILLA_ORDEN = 20261003
CARGAS = (20, 50, 100, 200)
CONFIGS = ('integrated', 'union')
CLAVES = ['total_ms', 'wall_process_ms', 'observed_peak_process_tree_rss_bytes', 'load_ms', 'reasoning_stage_ms', 'query_stage_ms', 'peak_heap_bytes']


def datos(config, lotes):
    return str((PAQ / 'cargas' / (f'load-{lotes}.ttl' if config == 'integrated' else f'load-{lotes}-union.ttl')).relative_to(RAIZ))


def una(config, lotes, destino, keep):
    res = ejecutar(peticion(config, datos=datos(config, lotes)), destino)
    keep.mkdir(parents=True, exist_ok=True)
    for f in ('request.json', 'execution.json'):
        shutil.copy(destino / f, keep / f)
    if res.get('status') != 'CONSISTENT':
        shutil.copy(destino / 'stderr.txt', keep / 'stderr.txt')
    shutil.rmtree(destino)  # los modelos completos ocupan decenas de MB por corrida
    return res


def conteos(r):
    return {'input_named_individuals': r.get('input_named_individuals'), 'raw_input_triples': r.get('raw_input_triples'),
            'construct_added_triples': r.get('construct_added_triples'), 'input_triples': r.get('input_triples'),
            'materialized_triples': r.get('materialized_triples'),
            'inferidos_materializados_menos_entrada': (r.get('materialized_triples') or 0) - (r.get('input_triples') or 0),
            'dl_pre': r.get('dl_materialized_triples'), 'dl_post': r.get('post_swrl_dl_materialized_triples'),
            'swrl_axiomas_nuevos': (r.get('swrl') or {}).get('new_axiom_count')}


def main(salida, tmp, reps='30'):
    salida, tmp, reps = Path(salida), Path(tmp), int(reps)
    guardar(salida / 'entorno.json', entorno())
    guardar(salida / 'huellas-cargas.json', {datos(c, n): sha256(RAIZ / datos(c, n)) for c in CONFIGS for n in CARGAS})
    orden = [(r, n, c) for r in range(reps) for n in CARGAS for c in CONFIGS]
    random.Random(SEMILLA_ORDEN).shuffle(orden)
    guardar(salida / 'orden.json', {'semilla': SEMILLA_ORDEN, 'orden': orden, 'proceso_nuevo_por_corrida': True})
    corridas = []
    with (salida / 'corridas.jsonl').open('w') as jl:
        for i, (r, n, c) in enumerate(orden, 1):
            nombre = f'{i:03d}-{c}-{n}-r{r:02d}'
            res = una(c, n, tmp / nombre, salida / 'corridas' / nombre)
            fila = {'orden': i, 'repeticion': r, 'lotes': n, 'configuracion': c, 'status': res.get('status'), 'failure_kind': res.get('failure_kind'),
                    **{k: res.get(k) for k in CLAVES}, **conteos(res)}
            corridas.append(fila); jl.write(json.dumps(fila) + '\n'); jl.flush()
            print(f"{i}/{len(orden)} {c} {n} {res.get('status')} {res.get('total_ms', 0) or 0:.0f} ms {fila['observed_peak_process_tree_rss_bytes'] / 2**20:.0f} MiB", flush=True)
    resumen = []
    for n in CARGAS:
        for c in CONFIGS:
            muestra = [x for x in corridas if x['lotes'] == n and x['configuracion'] == c]
            ok = [x for x in muestra if x['status'] == 'CONSISTENT']
            fila = {'lotes': n, 'configuracion': c, 'n': len(muestra), 'n_consistentes': len(ok)}
            for k in CLAVES:
                v = [x[k] for x in ok if x[k] is not None]
                if len(v) >= 2:
                    fila[k] = {'media': statistics.mean(v), 'de': statistics.stdev(v), 'mediana': statistics.median(v), 'min': min(v), 'max': max(v)}
            distintos = {json.dumps(conteos_fila(x), sort_keys=True) for x in ok}
            fila['conteos'] = conteos_fila(ok[0]) if ok else None
            fila['conteos_constantes_en_las_repeticiones'] = len(distintos) == 1
            resumen.append(fila)
    guardar(salida / 'resumen.json', {'protocolo': 'Proceso nuevo por corrida, -Xmx2g principal y trabajador, RSS del árbol de procesos cada 100 ms (máximo observado), límite 240 s; orden aleatorio con semilla 20261003; media, DE muestral y mediana.',
                                      'repeticiones': reps, 'curva': resumen})
    with (salida / 'resumen.csv').open('w', newline='') as f:
        w = csv.writer(f)
        w.writerow(['lotes', 'configuracion', 'n', 'n_consistentes', 'tiempo_medio_s', 'de_s', 'mediana_s', 'tiempo_proceso_medio_s', 'rss_media_MiB', 'rss_de_MiB', 'rss_mediana_MiB',
                    'individuos_entrada', 'triples_entrada', 'triples_inferidos'])
        for x in resumen:
            t, wp, m = x.get('total_ms', {}), x.get('wall_process_ms', {}), x.get('observed_peak_process_tree_rss_bytes', {})
            cc = x['conteos'] or {}
            w.writerow([x['lotes'], x['configuracion'], x['n'], x['n_consistentes'], fmt(t, 'media', 1e3), fmt(t, 'de', 1e3), fmt(t, 'mediana', 1e3), fmt(wp, 'media', 1e3),
                        fmt(m, 'media', 2**20), fmt(m, 'de', 2**20), fmt(m, 'mediana', 2**20), cc.get('input_named_individuals'), cc.get('input_triples'), cc.get('inferidos_materializados_menos_entrada')])
    tabla(salida, resumen)


def tabla(salida, resumen):
    md = ['| Lotes | Configuración | Corridas consistentes | Tiempo medio (s) | DE (s) | Mediana (s) | RSS medio (MiB) | DE (MiB) | Mediana (MiB) | Triples de entrada | Triples inferidos |',
          '|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    for x in resumen:
        t, m, cc = x.get('total_ms', {}), x.get('observed_peak_process_tree_rss_bytes', {}), x['conteos'] or {}
        md.append(f"| {x['lotes']} | {x['configuracion']} | {x['n_consistentes']}/{x['n']} | {fmt(t, 'media', 1e3)} | {fmt(t, 'de', 1e3)} | "
                  f"{fmt(t, 'mediana', 1e3)} | {fmt(m, 'media', 2**20)} | {fmt(m, 'de', 2**20)} | {fmt(m, 'mediana', 2**20)} | "
                  f"{cc.get('input_triples', '')} | {cc.get('inferidos_materializados_menos_entrada', '')} |")
    (salida / 'tabla.md').write_text('\n'.join(md) + '\n', encoding='utf-8')


def conteos_fila(x):
    return {k: x[k] for k in ('input_named_individuals', 'raw_input_triples', 'construct_added_triples', 'input_triples', 'materialized_triples',
                              'inferidos_materializados_menos_entrada', 'dl_pre', 'dl_post', 'swrl_axiomas_nuevos')}


def fmt(d, k, div):
    return round(d[k] / div, 3) if k in d else ''


if __name__ == '__main__':
    main(*sys.argv[1:4])
