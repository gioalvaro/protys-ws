"""La configuración integrada con los módulos de v1.0.0 y con los de v1.1.0, sobre los mismos
datos del caso, con el mismo motor que la evaluación académica y la plataforma (AcademicRunner: OWL2DLProfile,
HermiT, SWRLAPI/Drools, construcciones previas y las 21 consultas).

Para cada versión se arma una raíz temporal: ontologies/ sale de la etiqueta v1.0.0 o del árbol de trabajo; research/ es un
enlace al de este repositorio (datos del caso, consultas, validaciones, construcciones y runtime), así que sólo
cambian los módulos. La petición es la de research/evaluation/run_evaluation.py (specification) para «integrated».
Java 17 de JAVA_HOME.

uso: python3 corridas.py <carpeta-salida> <carpeta-temporal>
"""
import hashlib, json, os, shutil, subprocess, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from versiones import ANTES, RAIZ, VERSIONES, leer, listar  # noqa: E402
GUARDAR = ['run.json', 'validation.json', 'validation-after-swrl.json', 'dl-pre-deductions.ttl', 'dl-post-deductions.ttl',
           'swrl/deductions.owl', 'swrl/swrl.json', 'stderr.txt']


def peticion(catalogo):
    # Igual que specification(cat, cfg) de run_evaluation.py para la configuración integrada.
    cfg = next(c for c in catalogo['configurations'] if c['id'] == 'integrated')
    files = cfg['tbox_paths'] + cfg['abox_paths'] + cfg['rules_paths']
    queries = [q for q in catalogo['queries'] if q['id'] in cfg['query_ids']]
    queries += [q for q in catalogo['validations'] if q['id'] in cfg['validation_ids']]
    return {'files': list(dict.fromkeys(files)), 'enabled_rules': ','.join(cfg['enabled_rule_ids']),
            'numeric_controls_enabled': cfg['numeric_controls_enabled'], 'queries': [{'id': q['id'], 'path': q['path']} for q in queries],
            'pre_inference_constructs': catalogo['pre_inference_constructs'] if cfg['run_pre_inference_constructs'] else []}


def raiz_temporal(version, tmp):
    raiz = tmp / version
    if raiz.exists():
        shutil.rmtree(raiz)
    (raiz / 'ontologies').mkdir(parents=True)
    huellas = {}
    for ruta in listar(version):
        datos = leer(version, ruta)
        (raiz / ruta).write_bytes(datos)
        huellas[ruta] = hashlib.sha256(datos).hexdigest()
    (raiz / 'research').symlink_to(RAIZ / 'research')
    return raiz, huellas


def correr(raiz, salida_tmp):
    cp = f"{RAIZ}/research/runtime/validator/target/classes:" + (RAIZ / 'research/runtime/validator/target/classpath.txt').read_text().strip()
    java = Path(os.environ['JAVA_HOME']) / 'bin/java'
    cmd = [str(java), '-Xmx2g', '-Djava.awt.headless=true', '-cp', cp,
           'org.protys.research.AcademicRunner', str(raiz), str(raiz / 'request.json'), str(salida_tmp)]
    return subprocess.run(cmd, capture_output=True, text=True, cwd=raiz)


def main(salida, tmp):
    salida, tmp = Path(salida), Path(tmp)
    catalogo = json.loads((RAIZ / 'research/catalog.json').read_text())
    resumen = {'java': subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-version'], capture_output=True, text=True).stderr.splitlines()[0], 'peticion': peticion(catalogo), 'versiones': {}}
    for version in VERSIONES:
        raiz, huellas = raiz_temporal(version, tmp)
        (raiz / 'request.json').write_text(json.dumps(resumen['peticion'], indent=2))
        out = raiz / 'out'
        proc = correr(raiz, out)
        destino = salida / version
        destino.mkdir(parents=True, exist_ok=True)
        for nombre in GUARDAR + [q['id'] + '.json' for q in resumen['peticion']['queries']]:
            if (out / nombre).exists():
                (destino / nombre).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy(out / nombre, destino / nombre)
        run = json.loads((out / 'run.json').read_text()) if (out / 'run.json').exists() else {}
        resumen['versiones'][version] = {
            'codigo_salida': proc.returncode, 'huellas_ontologies': huellas,
            **{k: run.get(k) for k in ('status', 'profile_valid', 'raw_input_triples', 'construct_added_triples', 'input_triples',
                                         'dl_materialized_triples', 'post_swrl_dl_materialized_triples', 'materialized_triples',
                                         'input_named_individuals', 'materialized_named_individuals', 'reasoning_stage_ms', 'total_ms', 'java')},
            'swrl': {k: run.get('swrl', {}).get(k) for k in ('status', 'imported_rule_count', 'new_axiom_count', 'engine_inferred_axiom_count')}}
        print(version, json.dumps({k: v for k, v in resumen['versiones'][version].items() if k != 'huellas_ontologies'}, ensure_ascii=False))
    (salida / 'resumen.json').write_text(json.dumps(resumen, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main(*sys.argv[1:3])
