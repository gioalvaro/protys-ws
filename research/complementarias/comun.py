"""Piezas comunes de las pruebas complementarias (disyunciones, ablación por regla, consultas QC y curva de costo).

- `ejecutar` es copia de `execute` de research/evaluation/run_evaluation.py (mismo comando Java, -Xmx2g, proceso nuevo,
  muestreo de RSS del árbol de procesos cada 100 ms y límite de 240 s). La única diferencia es que la salida puede ir
  fuera de research/evaluation/ (allí sólo se escribe la evaluación publicada).
- `peticion` arma la petición de la configuración del catálogo igual que `specification` de run_evaluation.py, con
  la opción de reemplazar los datos, agregar archivos o cambiar las reglas activas.
- `equivalentes` reutiliza la comparación de multiconjuntos con tolerancia numérica de run_evaluation.py.
"""
import collections, hashlib, json, os, platform, signal, subprocess, sys, time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
PAQ = Path(__file__).resolve().parent
sys.path.insert(0, str(RAIZ / 'research/evaluation'))
import run_evaluation as ev  # noqa: E402  (sólo se usan funciones puras: specification, equivalent, enabled_rules_text)

CATALOGO = json.loads((RAIZ / 'research/catalog.json').read_text())
LIMITE_S = 240


def java_home():
    return Path(os.environ['JAVA_HOME'])


def classpath():
    return str(RAIZ / 'research/runtime/validator/target/classes') + os.pathsep + \
        (RAIZ / 'research/runtime/validator/target/classpath.txt').read_text().strip()


def configuracion(ident):
    return next(c for c in CATALOGO['configurations'] if c['id'] == ident)


def peticion(config='integrated', datos=None, extra=(), reglas=None):
    spec = ev.specification(CATALOGO, configuracion(config), datos)
    spec['files'] = spec['files'] + [str(Path(x).relative_to(RAIZ)) if Path(x).is_absolute() and Path(x).is_relative_to(RAIZ) else str(x) for x in extra]
    if reglas is not None:
        spec['enabled_rules'] = ev.enabled_rules_text(reglas)
    return spec


def guardar(ruta, valor):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(valor, indent=2, ensure_ascii=False) + '\n')


def ejecutar(spec, destino):
    destino.mkdir(parents=True, exist_ok=False)
    guardar(destino / 'request.json', spec)
    cmd = [str(java_home() / 'bin/java'), '-Xmx2g', '-Djava.awt.headless=true', '-cp', classpath(),
           'org.protys.research.AcademicRunner', str(RAIZ), str(destino / 'request.json'), str(destino)]
    inicio = time.monotonic(); pico = 0; vencido = False
    with (destino / 'stdout.txt').open('x') as out, (destino / 'stderr.txt').open('x') as err:
        proc = subprocess.Popen(cmd, stdout=out, stderr=err, start_new_session=True)
        try:
            while proc.poll() is None:
                try:
                    filas = [list(map(int, l.split())) for l in subprocess.check_output(['ps', '-axo', 'pid,ppid,rss'], text=True).splitlines()[1:] if len(l.split()) == 3]
                    familia = {proc.pid}
                    for _ in range(3):
                        familia |= {pid for pid, ppid, rss in filas if ppid in familia}
                    pico = max(pico, sum(rss * 1024 for pid, ppid, rss in filas if pid in familia))
                except (ValueError, subprocess.SubprocessError):
                    pass
                if time.monotonic() - inicio > LIMITE_S:
                    vencido = True; os.killpg(proc.pid, signal.SIGKILL); proc.wait(); break
                time.sleep(.1)
        except BaseException:
            if proc.poll() is None:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL); proc.wait()
            raise
    codigo = proc.wait()
    vfile = destino / 'validation-after-swrl.json' if (destino / 'validation-after-swrl.json').exists() else destino / 'validation.json'
    if (destino / 'run.json').exists() and codigo == 0:
        res = json.loads((destino / 'run.json').read_text())
    else:
        val = json.loads(vfile.read_text()) if vfile.exists() else {}
        estado = val.get('status') if val.get('status') in ['INCONSISTENT', 'NOT_EVALUATED'] else 'NOT_EVALUATED'
        res = {'status': estado, 'failure_kind': val.get('failure_kind', 'PROCESS_ERROR'), 'last_completed_validation': dict(val),
               'validation': dict(val, status=estado), 'stderr': (destino / 'stderr.txt').read_text()[-5000:]}
        for k in ['owl_validation_status', 'numeric_evaluation', 'cleaning_evaluation', 'context_evaluation', 'error']:
            if k in val:
                res[k] = val[k]
    if vencido:
        res = {'status': 'NOT_EVALUATED', 'failure_kind': 'TIMEOUT', 'timeout_seconds': LIMITE_S,
               'error': 'Process exceeded configured time limit; no completed timing/result accepted.',
               'last_completed_validation': res.get('validation', {}), 'validation': {'status': 'NOT_EVALUATED'}}
    res.update(exit_code=codigo, wall_process_ms=(time.monotonic() - inicio) * 1000, observed_peak_process_tree_rss_bytes=pico,
               rss_sampling_interval_ms=100)
    if res.get('status') == 'CONSISTENT':
        esperadas = [q['id'] for q in spec.get('queries', [])]; obtenidas = [q.get('id') for q in res.get('queries', [])]
        faltan = [i for i in esperadas if not (destino / (i + '.json')).is_file()]
        if collections.Counter(esperadas) != collections.Counter(obtenidas) or faltan:
            res.update(status='NOT_EVALUATED', failure_kind='INCOMPLETE_QUERIES', missing_query_outputs=faltan)
    guardar(destino / 'execution.json', res)
    return res


def equivalentes(a, b):
    return ev.equivalent(a, b)


def filas(resultado):
    return len(resultado.get('results', {}).get('bindings', []))


def sha256(ruta):
    return hashlib.sha256(Path(ruta).read_bytes()).hexdigest()


def entorno():
    def cmd(*c):
        try:
            return subprocess.run(c, capture_output=True, text=True).stdout.strip() or subprocess.run(c, capture_output=True, text=True).stderr.strip()
        except OSError as e:
            return str(e)
    return {
        'fecha_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'ontologias_sha256': {p.name: sha256(p) for p in sorted((RAIZ / 'ontologies').iterdir()) if p.is_file()},
        'plataforma': platform.platform(), 'maquina': platform.machine(), 'cpu_count': os.cpu_count(),
        'hw.model': cmd('sysctl', '-n', 'hw.model'), 'cpu': cmd('sysctl', '-n', 'machdep.cpu.brand_string'),
        'hw.memsize': cmd('sysctl', '-n', 'hw.memsize'),
        'java_home': str(java_home()).replace(os.environ.get('HOME', '~'), '~'),
        'java_version': subprocess.run([str(java_home() / 'bin/java'), '-version'], capture_output=True, text=True).stderr,
        'parametros_jvm': '-Xmx2g -Djava.awt.headless=true (proceso principal); el trabajador SWRL lo lanza AcademicRunner con -Xmx2g',
        'python': platform.python_version(),
    }
