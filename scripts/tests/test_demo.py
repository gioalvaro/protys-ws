"""Ownership and failure-recovery checks for the local demo CLI, without Docker."""
import argparse
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
import urllib.error

# A third-party distribution may also provide a package named ``scripts``.
# Load this checkout's exact file independently of the interpreter's sys.path.
_demo_path = Path(__file__).resolve().parents[1] / 'demo.py'
_demo_spec = importlib.util.spec_from_file_location('protys_demo_under_test', _demo_path)
if _demo_spec is None or _demo_spec.loader is None:
    raise ImportError('Cannot load repository demo.py: ' + str(_demo_path))
demo = importlib.util.module_from_spec(_demo_spec)
_demo_spec.loader.exec_module(demo)


class DemoSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.work = self.root / '.local-demo' / 'case'
        self.work.mkdir(parents=True)
        self.frozen = self.work / 'compose.yml'
        self.frozen.write_text('services: {protys-frontend: {image: test-ui}}\n')
        self.state_path = self.work / 'state.json'
        self.state = {
            'kind': 'PROTYS_OWNED_LOCAL_DEMO',
            'project': 'protys-demo-012345abcdef',
            'repository': str(self.root),
            'compose_file': str(self.frozen),
            'compose_sha256': demo.sha(self.frozen),
            'ports': {'frontend': 41234, 'backend': 41235,
                      'fuseki': 41236, 'postgres': 41237},
        }
        self.state_path.write_text(json.dumps(self.state))
        self.frontend_id = 'owned-original-frontend'
        (self.work / 'ready.json').write_text(json.dumps({
            'frontend_container_id': self.frontend_id,
        }))
        catalog = self.root / 'research' / 'catalog.json'
        catalog.parent.mkdir()
        # This focused test does not repeat query correctness; it reaches recovery.
        catalog.write_text(json.dumps({'queries': []}))
        self.root_patch = patch.object(demo, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def run_smoke(self):
        # Import only the unrelated comparison API; no research execution is run.
        evaluator = types.ModuleType('run_evaluation')
        evaluator.equivalent = lambda actual, expected: actual == expected
        old_sys_path = list(sys.path)
        try:
            with patch.dict(sys.modules, {'run_evaluation': evaluator}):
                with contextlib.redirect_stdout(io.StringIO()):
                    return demo.smoke(argparse.Namespace(state=self.state_path))
        finally:
            sys.path[:] = old_sys_path

    def smoke_report(self):
        reports = list(self.work.glob('smoke-*/report.json'))
        self.assertEqual(len(reports), 1)
        return json.loads(reports[0].read_text())

    def test_live_compose_changes_do_not_replace_frozen_definition(self):
        live = self.root / 'docker' / 'docker-compose.yml'
        live.parent.mkdir()
        live.write_text('services: {unrelated: {image: changed-after-start}}\n')
        with patch.object(demo, 'COMPOSE', live):
            state = demo.load(self.state_path)
            with patch.object(demo.subprocess, 'run') as run:
                demo.compose(state, 'down')
        command = run.call_args.args[0]
        self.assertEqual(command[command.index('-f') + 1], str(self.frozen))
        self.assertNotIn(str(live), command)
        self.assertEqual(command[command.index('-p') + 1], self.state['project'])
        self.assertEqual(run.call_args.kwargs['env']['PROTYS_FRONTEND_PORT'], '41234')
        self.assertTrue(run.call_args.kwargs['check'])
        self.assertEqual(demo.sha(self.frozen), state['compose_sha256'])

    def test_changed_frozen_compose_rejected_before_resource_management(self):
        loaded = demo.load(self.state_path)
        self.frozen.write_text('services: {foreign: {image: altered}}\n')
        with patch.object(demo.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'Compose definition absent or changed'):
                demo.load(self.state_path)
            with self.assertRaisesRegex(ValueError, 'Compose definition changed'):
                demo.compose(loaded, 'down')
        run.assert_not_called()

    def test_foreign_port_reuse_without_owned_frontend_rejected_before_http(self):
        # Docker's project/service filters omit the foreign container at this port.
        with patch.object(demo.subprocess, 'check_output', return_value='') as docker:
            with patch.object(demo, 'request') as request:
                with patch.object(demo.urllib.request, 'urlopen') as urlopen:
                    result = self.run_smoke()
        self.assertEqual(result, 1)
        request.assert_not_called()
        urlopen.assert_not_called()
        command = docker.call_args.args[0]
        self.assertIn('label=com.docker.compose.project=' + self.state['project'], command)
        self.assertIn('label=com.docker.compose.service=protys-frontend', command)
        report = self.smoke_report()
        self.assertEqual(report['status'], 'FAIL')
        self.assertIn('Owned frontend is not running', report['error'])
        self.assertEqual(report['checks'], [])

    def test_foreign_identity_port_or_replaced_container_rejected_before_http(self):
        expected = {
            'Id': self.frontend_id,
            'Config': {'Labels': {
                'com.docker.compose.project': self.state['project'],
                'com.docker.compose.service': 'protys-frontend',
            }},
            'NetworkSettings': {'Ports': {'80/tcp': [{
                'HostIp': '127.0.0.1', 'HostPort': '41234',
            }]}},
        }
        for change in ('foreign_project', 'changed_port', 'replaced_id'):
            with self.subTest(change=change):
                previous_reports = set(self.work.glob('smoke-*/report.json'))
                details = json.loads(json.dumps(expected))
                if change == 'foreign_project':
                    details['Config']['Labels']['com.docker.compose.project'] = 'foreign-stack'
                elif change == 'changed_port':
                    details['NetworkSettings']['Ports']['80/tcp'][0]['HostPort'] = '49999'
                else:
                    details['Id'] = 'replacement-frontend'
                with patch.object(demo.subprocess, 'check_output', side_effect=[
                    'container-short-id\n', json.dumps([details]),
                ]) as docker:
                    with patch.object(demo, 'request') as request:
                        with patch.object(demo.urllib.request, 'urlopen') as urlopen:
                            result = self.run_smoke()
                self.assertEqual(result, 1)
                self.assertEqual(docker.call_count, 2)
                request.assert_not_called()
                urlopen.assert_not_called()
                new_reports = set(self.work.glob('smoke-*/report.json')) - previous_reports
                self.assertEqual(len(new_reports), 1)
                report = json.loads(new_reports.pop().read_text())
                expected_error = ('Original frontend container was replaced'
                                  if change == 'replaced_id'
                                  else 'Frontend ownership or port differs')
                self.assertIn(expected_error, report['error'])

    def test_confirmed_frontend_health_required(self):
        details = {
            'Id': self.frontend_id,
            'Config': {'Labels': {
                'com.docker.compose.project': self.state['project'],
                'com.docker.compose.service': 'protys-frontend',
            }},
            'NetworkSettings': {'Ports': {'80/tcp': [{
                'HostIp': '127.0.0.1', 'HostPort': '41234',
            }]}},
            'State': {'Health': {'Status': 'healthy'}},
        }
        for status in ('healthy', 'starting', 'unhealthy', None):
            with self.subTest(status=status):
                details['State']['Health']['Status'] = status
                with patch.object(demo.subprocess, 'check_output', side_effect=[
                    'container-short-id\n', json.dumps([details]),
                ]):
                    if status == 'healthy':
                        self.assertEqual(demo.verify_frontend(self.state, self.frontend_id), self.frontend_id)
                    else:
                        with self.assertRaisesRegex(RuntimeError, 'health is not confirmed'):
                            demo.verify_frontend(self.state, self.frontend_id)

    def test_applied_r03_disable_with_lost_response_attempts_restoration(self):
        remote = {'r03_active': True, 'lost_disable_response': False}
        calls = []
        rules = [{'id': 'rule03', 'name': 'R03 recorded input flow'}]
        rules.extend({'id': str(n), 'name': 'Other rule ' + str(n)} for n in range(21))
        reasoning_calls = 0

        def fake_request(base, path, body=None, method=None, metadata=None):
            nonlocal reasoning_calls
            calls.append((path, method, body))
            if path.endswith('/toggle?active=false'):
                # The mutation reached the server; the client never got its reply.
                remote['r03_active'] = False
                remote['lost_disable_response'] = True
                raise urllib.error.URLError('response lost after accepted R03 disable')
            if metadata is not None:
                metadata.update(http_status=200, content_type='application/json')
            if path.endswith('/toggle?active=true'):
                self.assertFalse(remote['r03_active'])
                remote['r03_active'] = True
                return {'id': 'rule03', 'active': True}
            if path == '/api/alignment/rules':
                return rules
            if path == '/api/alignment/reasoning/execute':
                reasoning_calls += 1
                return {
                    'validationStatus': 'CONSISTENT',
                    'activeRules': 22 if remote['r03_active'] else 21,
                    'contextEvaluation': {'status': 'CONTEXT_VALID'},
                    'numericEvaluation': {'status': 'VALID'},
                    'cleaningEvaluation': {'status': 'MISSING_CLEANING_RECORD'},
                    'cacheHit': reasoning_calls == 2,
                    'reasoningTimeMs': 0 if reasoning_calls == 2 else 10,
                }
            if path == '/api/sparql/execute':
                return {'askResult': remote['r03_active']}
            self.fail('Unexpected request: ' + path)

        with patch.object(demo, 'verify_frontend', return_value=self.frontend_id):
            with patch.object(demo, 'request', side_effect=fake_request):
                with patch.object(demo.urllib.request, 'urlopen') as urlopen:
                    result = self.run_smoke()
        urlopen.assert_not_called()
        self.assertEqual(result, 1)  # Recovery does not turn a failed check into PASS.
        self.assertTrue(remote['lost_disable_response'])
        self.assertTrue(remote['r03_active'])
        disable = ('/api/alignment/rules/rule03/toggle?active=false', 'PUT', {})
        restore = ('/api/alignment/rules/rule03/toggle?active=true', 'PUT', {})
        self.assertEqual(calls.count(disable), 1)
        self.assertEqual(calls.count(restore), 1)
        self.assertLess(calls.index(disable), calls.index(restore))
        self.assertEqual(calls[-1][0], '/api/alignment/reasoning/execute')
        self.assertEqual(reasoning_calls, 3)
        report = self.smoke_report()
        self.assertEqual(report['status'], 'FAIL')
        self.assertIn('response lost', report['error'])
        self.assertTrue(report['rule_state_restored_after_failure'])
        self.assertNotIn('restore_error', report)


if __name__ == '__main__':
    unittest.main()
