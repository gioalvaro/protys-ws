"""Pruebas de scripts/comparar_ejecuciones.py con ejecuciones sintéticas."""
import copy
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import comparar_ejecuciones as comp

FUNCIONAL = {
    'status': 'PASS',
    'configurations': [{'id': 'integrated', 'result': {
        'status': 'CONSISTENT', 'materialized_triples': 100, 'total_ms': 10.0, 'peak_heap_bytes': 5,
        'queries': [{'id': 'Q01', 'result_count': 4, 'query_ms': 1.0}]}}],
    'assertions': [{'id': 'R01_positive', 'pass': True,
                    'group': '/Users/Shared/protys-ws/research/evaluation/current/assertion-groups/g01'}],
    'input_hashes': {'ontologies/core-concepts.owl': 'a'},
}
RUN = {'configuration': 'integrated', 'replica': 1, 'status': 'CONSISTENT', 'input_triples': 10,
       'total_ms': 3.0, 'observed_peak_process_tree_rss_bytes': 7}
DEMO = {'status': 'PASS', 'checks': [{'id': 'IMPORT22', 'pass': True}],
        'services': {'backend': 'http://127.0.0.1:5000'}, 'owned_resources': {'fuseki_pid': 1}}
CAPACIDAD = {'status': 'PASS', 'pilots': [{'lots': 100, 'result': {'input_triples': 44444, 'load_ms': 9.0}}]}
CENSO = {'status': 'PASS', 'base_eight_unique_counts': {'Class': 87}}


def escribir(carpeta, funcional, run, demo, capacidad, censo):
    carpeta.mkdir()
    (carpeta / 'functional.json').write_text(json.dumps(funcional))
    (carpeta / 'runs.jsonl').write_text(json.dumps(run) + '\n')
    (carpeta / 'demonstration.json').write_text(json.dumps(demo))
    (carpeta / 'capacity.json').write_text(json.dumps(capacidad))
    (carpeta / 'census.json').write_text(json.dumps(censo))


class CompararTests(unittest.TestCase):
    def ejecutar(self, **cambios):
        datos = dict(funcional=FUNCIONAL, run=RUN, demo=DEMO, capacidad=CAPACIDAD, censo=CENSO)
        cand = {k: copy.deepcopy(v) for k, v in datos.items()}
        for clave, mutar in cambios.items():
            mutar(cand[clave])
        with tempfile.TemporaryDirectory() as t:
            escribir(Path(t, 'ref'), **datos)
            escribir(Path(t, 'cand'), **cand)
            with redirect_stdout(io.StringIO()) as salida, redirect_stderr(io.StringIO()):
                rc = comp.main([str(Path(t, 'ref')), str(Path(t, 'cand'))])
        return rc, salida.getvalue()

    def test_identicas(self):
        self.assertEqual(self.ejecutar()[0], 0)

    def test_tiempos_memoria_puertos_hashes_y_rutas_no_cuentan(self):
        def mutar_funcional(f):
            f['configurations'][0]['result']['total_ms'] = 99.0
            f['configurations'][0]['result']['peak_heap_bytes'] = 99
            f['input_hashes']['ontologies/core-concepts.owl'] = 'b'
            f['assertions'][0]['group'] = '/home/otra/copias/protys-ws/research/evaluation/current/assertion-groups/g01'

        def mutar_demo(d):
            d['services']['backend'] = 'http://127.0.0.1:6000'
            d['owned_resources']['fuseki_pid'] = 2
        rc, _ = self.ejecutar(funcional=mutar_funcional, demo=mutar_demo,
                              run=lambda r: r.update(total_ms=8.0, observed_peak_process_tree_rss_bytes=9))
        self.assertEqual(rc, 0)

    def test_conteo_de_triples_distinto(self):
        rc, texto = self.ejecutar(run=lambda r: r.update(input_triples=11))
        self.assertEqual(rc, 1)
        self.assertIn('input_triples', texto)

    def test_filas_de_consulta_distintas(self):
        rc, _ = self.ejecutar(funcional=lambda f: f['configurations'][0]['result']['queries'][0].update(result_count=5))
        self.assertEqual(rc, 1)

    def test_censo_distinto(self):
        rc, _ = self.ejecutar(censo=lambda c: c['base_eight_unique_counts'].update(Class=88))
        self.assertEqual(rc, 1)

    def test_lista_en_otro_orden_no_cuenta(self):
        orden = []
        a = {'bindings': [{'x': 1}, {'x': 2}]}
        b = {'bindings': [{'x': 2}, {'x': 1}]}
        self.assertEqual(comp.diferencias(a, b, '', None, orden), [])
        self.assertEqual(orden, ['.bindings'])
        self.assertTrue(comp.diferencias(a, {'bindings': [{'x': 2}, {'x': 3}]}, '', None, []))

    def test_json_incrustado_en_un_mensaje(self):
        a = 'Validation status: CONSISTENT; {"status": "CONSISTENT", "dl_ms": 1.0}'
        b = 'Validation status: CONSISTENT; {"dl_ms": 2.0, "status": "CONSISTENT"}'
        c = 'Validation status: CONSISTENT; {"status": "INCONSISTENT", "dl_ms": 1.0}'
        self.assertEqual(comp.normalizar(a), comp.normalizar(b))
        self.assertNotEqual(comp.normalizar(a), comp.normalizar(c))

    def test_hash_de_archivo_fuente_del_censo_no_cuenta(self):
        a = {'components': [{'path': 'ontologies/core-concepts.owl', 'sha256': 'x', 'counts': {'Class': 4}}]}
        b = {'components': [{'path': 'ontologies/core-concepts.owl', 'sha256': 'y', 'counts': {'Class': 4}}]}
        c = {'components': [{'path': 'ontologies/core-concepts.owl', 'sha256': 'y', 'counts': {'Class': 5}}]}
        self.assertEqual(comp.diferencias(comp.normalizar(a), comp.normalizar(b)), [])
        self.assertTrue(comp.diferencias(comp.normalizar(a), comp.normalizar(c)))

    def test_hash_de_serializacion_sin_crudos_cuenta(self):
        rc, texto = self.ejecutar(run=lambda r: r.update(swrl={'input_sha256': 'b'}))
        self.assertEqual(rc, 1)

    def test_falta_un_archivo(self):
        with tempfile.TemporaryDirectory() as t:
            with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(comp.main([t, t]), 2)


if __name__ == '__main__':
    unittest.main()
