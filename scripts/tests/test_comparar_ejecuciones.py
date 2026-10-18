"""Pruebas de scripts/comparar_ejecuciones.py con informes sintéticos."""
import copy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import comparar_ejecuciones as comp

BASE = {
    'status': 'PASS',
    'configurations': [{'id': 'integrated', 'result': {
        'status': 'CONSISTENT', 'owl_validation_status': 'CONSISTENT', 'profile_valid': True,
        'unsatisfiable_classes': [], 'materialized_triples': 100, 'total_ms': 10.0,
        'queries': [{'id': 'Q01', 'result_count': 4, 'query_ms': 1.0}]}}],
    'golden_checks': [{'configuration': 'integrated', 'id': 'Q01', 'pass': True, 'actual_rows': 4}],
    'assertions': [{'id': 'R01_positive', 'pass': True, 'owl_status': 'CONSISTENT'}],
    'input_hashes': {'ontologies/process-module.owl': 'a', 'research/catalog.json': 'b'},
}


class CompararTests(unittest.TestCase):
    def ejecutar(self, base, cand):
        with tempfile.TemporaryDirectory() as t:
            pb, pc = Path(t, 'base'), Path(t, 'cand')
            pb.mkdir(); pc.mkdir()
            (pb / 'functional.json').write_text(json.dumps(base))
            (pc / 'functional.json').write_text(json.dumps(cand))
            buf = io.StringIO()
            with redirect_stdout(buf):
                rc = comp.main([str(pb), str(pc)])
            return rc, buf.getvalue()

    def test_identicas_sin_diferencias(self):
        rc, texto = self.ejecutar(BASE, copy.deepcopy(BASE))
        self.assertEqual(rc, 0)
        self.assertIn('**Diferencias de resultados:** no', texto)

    def test_tiempos_no_cuentan(self):
        cand = copy.deepcopy(BASE)
        cand['configurations'][0]['result']['total_ms'] = 99.0
        cand['configurations'][0]['result']['queries'][0]['query_ms'] = 9.0
        rc, _ = self.ejecutar(BASE, cand)
        self.assertEqual(rc, 0)

    def test_detecta_cambios_logicos_y_de_entrada(self):
        cand = copy.deepcopy(BASE)
        r = cand['configurations'][0]['result']
        r['materialized_triples'] = 104
        r['queries'][0]['result_count'] = 5
        cand['golden_checks'][0].update({'pass': False, 'actual_rows': 5})
        cand['assertions'][0]['pass'] = False
        cand['input_hashes']['ontologies/process-module.owl'] = 'z'
        cand['input_hashes']['ontologies/nuevo.owl'] = 'n'
        d = comp.comparar(BASE, cand)
        self.assertTrue(comp.hay_diferencias_de_resultado(d))
        self.assertEqual(d['consultas'][0]['filas_candidata'], 5)
        self.assertEqual(d['comprobaciones'][0]['pasa_candidata'], False)
        self.assertEqual(d['aserciones'][0]['cambios']['pass'], [True, False])
        self.assertEqual(d['archivos_de_entrada']['modificados'], ['ontologies/process-module.owl'])
        self.assertEqual(d['archivos_de_entrada']['agregados'], ['ontologies/nuevo.owl'])
        rc, texto = self.ejecutar(BASE, cand)
        self.assertEqual(rc, 1)
        self.assertIn('materialized_triples', texto)

    def test_entrada_inexistente(self):
        err = io.StringIO()
        with redirect_stderr(err):
            self.assertEqual(comp.main(['/no/existe', '/no/existe']), 2)

    def test_no_sobrescribe_json(self):
        with tempfile.TemporaryDirectory() as t:
            p = Path(t, 'run'); p.mkdir()
            (p / 'functional.json').write_text(json.dumps(BASE))
            destino = Path(t, 'salida.json'); destino.write_text('previo')
            err = io.StringIO()
            with redirect_stdout(io.StringIO()), redirect_stderr(err):
                self.assertEqual(comp.main([str(p), str(p), '--json', str(destino)]), 2)
            self.assertEqual(destino.read_text(), 'previo')


if __name__ == '__main__':
    unittest.main()
