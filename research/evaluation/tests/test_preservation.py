"""Lightweight preservation checks; no Java model or benchmark execution."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

EVALUATION = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(EVALUATION))
import output_paths
import run_evaluation as runner
import verificar_benchmark as verifier
import empaquetar_evidencia as packer


def inventory(path):
    return {str(p.relative_to(path)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in path.rglob('*') if p.is_file()}


class PreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name).resolve()
        (self.repo / 'research/evaluation').mkdir(parents=True)
        self.out = self.repo / 'research/evaluation/runs/test'
        self.environment = patch.dict(os.environ, {}, clear=True)
        self.environment.start()

    def tearDown(self):
        self.environment.stop()
        self.temp.cleanup()

    def test_unique_default_and_repo_relative_paths(self):
        a = output_paths.evaluation_dir(repo=self.repo, fresh=True)
        b = output_paths.evaluation_dir(repo=self.repo, fresh=True)
        self.assertNotEqual(a, b)
        self.assertFalse(a.exists())
        old = Path.cwd()
        try:
            os.chdir(self.repo.parent)
            self.assertEqual(output_paths.evaluation_dir('research/evaluation/runs/test', repo=self.repo), self.out)
            os.environ['PROTYS_EVALUATION_OUT'] = 'research/evaluation/runs/test'
            self.assertEqual(output_paths.evaluation_dir(repo=self.repo), self.out)
            self.assertEqual(output_paths.evaluation_dir('research/evaluation/runs/override', repo=self.repo), self.out.parent / 'override')
        finally:
            os.chdir(old)

    def test_escape_and_symlink_escape_rejected(self):
        for path in ['research/evaluation', '..', '/tmp/protys-outside']:
            with self.assertRaises(ValueError):
                output_paths.evaluation_dir(path, repo=self.repo)
        link = self.repo / 'research/evaluation/escape'
        link.symlink_to(self.repo.parent, target_is_directory=True)
        with self.assertRaises(ValueError):
            output_paths.evaluation_dir('research/evaluation/escape/run', repo=self.repo)

    def test_partial_phase_and_summary_are_preserved(self):
        for phase, relative in [('functional', 'assertion-groups/g01/request.json'),
                                ('benchmark', 'runs.jsonl'), ('capacity', 'capacity/snapshot/data.ttl'),
                                ('demonstration', 'demonstration.json')]:
            root = self.out / phase
            file = root / relative
            file.parent.mkdir(parents=True)
            file.write_text('evidence that must remain')
            before = inventory(root)
            with self.assertRaises(FileExistsError):
                with output_paths.reserve_phases(root, [phase]):
                    self.fail('occupied phase was admitted')
            self.assertEqual(before, inventory(root))

    def test_concurrent_phase_and_interruption_reservations(self):
        with self.assertRaisesRegex(RuntimeError, 'interrupted'):
            with output_paths.reserve_phases(self.out, ['functional']):
                with self.assertRaises(FileExistsError):
                    with output_paths.reserve_phases(self.out, ['capacity']):
                        self.fail('concurrent writer was admitted')
                (self.out / 'partial.txt').write_text('partial results')
                raise RuntimeError('interrupted')
        self.assertFalse((self.out / '.evaluation-lock').exists())
        before = inventory(self.out)
        with self.assertRaises(FileExistsError):
            with output_paths.reserve_phases(self.out, ['functional']):
                self.fail('interrupted phase was restarted')
        self.assertEqual(before, inventory(self.out))

    def test_exclusive_json_write_never_truncates(self):
        file = self.repo / 'protected.json'
        runner.save(file, {'previous': True})
        before = file.read_bytes()
        with self.assertRaises(FileExistsError):
            runner.save(file, {'previous': False})
        self.assertEqual(file.read_bytes(), before)

    def test_execute_rejects_even_empty_existing_target_before_java(self):
        target = self.out / 'runs/001'
        target.mkdir(parents=True)
        with patch.object(runner, 'ROOT', self.repo), patch.object(runner.subprocess, 'Popen') as launch:
            with self.assertRaises(FileExistsError):
                runner.execute({}, target)
            launch.assert_not_called()
        self.assertEqual(inventory(target), {})

    def test_failed_process_leaves_request_and_logs_and_cannot_be_restarted(self):
        target = self.out / 'runs/failed'
        with patch.object(runner, 'ROOT', self.repo), patch.object(runner, 'JAVA', Path('/fake/java')), \
             patch.object(runner, 'CP', 'fake-cp'), \
             patch.object(runner.subprocess, 'Popen', side_effect=OSError('simulated launch failure')):
            with self.assertRaises(OSError):
                runner.execute({'files': ['declared-input']}, target)
            self.assertTrue((target / 'request.json').is_file())
            self.assertTrue((target / 'stdout.txt').is_file())
            before = inventory(target)
            with self.assertRaises(FileExistsError):
                runner.execute({}, target)
            self.assertEqual(before, inventory(target))

    def test_functional_then_benchmark_does_not_replace_environment(self):
        (self.repo / 'research/catalog.json').write_text('{}')
        def environment():
            runner.save(runner.OUT / 'environment.json', {'original': True})
        def functional(cat):
            report = {'status': 'PASS'}
            runner.save(runner.OUT / 'functional.json', report)
            return report
        def benchmark(cat):
            self.assertEqual(json.loads((runner.OUT / 'functional.json').read_text())['status'], 'PASS')
            runner.save(runner.OUT / 'summary.json', {'simulated_unit_test': True})
        with patch.object(runner, 'ROOT', self.repo), patch.object(runner, 'configure_runtime'), \
             patch.object(runner, 'environment', side_effect=environment) as prepare, \
             patch.object(runner, 'functional', side_effect=functional), \
             patch.object(runner, 'benchmark', side_effect=benchmark), \
             patch.object(runner, 'evaluation_dir', side_effect=lambda value, **kw: output_paths.evaluation_dir(value, repo=self.repo, **kw)), \
             contextlib.redirect_stdout(io.StringIO()):
            runner.main(['--functional', '--output', str(self.out)])
            before = inventory(self.out)
            runner.main(['--benchmark', '--output', str(self.out)])
            self.assertEqual(prepare.call_count, 1)
            for relative, digest in before.items():
                self.assertEqual(inventory(self.out)[relative], digest)
            after = inventory(self.out)
            with self.assertRaises(FileExistsError):
                runner.main(['--benchmark', '--output', str(self.out)])
            self.assertEqual(after, inventory(self.out))

    def test_benchmark_requires_existing_functional_without_creating_output(self):
        with self.assertRaises(FileNotFoundError):
            output_paths.check_phases(self.out, ['benchmark'])
        self.assertFalse(self.out.exists())

    def test_lazy_comparator_import_has_no_java_or_classpath_dependency(self):
        result = subprocess.run([sys.executable, '-c',
            'import sys;sys.path.insert(0,sys.argv[1]);from run_evaluation import equivalent;'
            'assert equivalent({"boolean":True},{"boolean":True})', str(EVALUATION)],
            cwd=self.repo, env={}, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_auditor_selects_requested_evidence_and_guards_raw_outputs(self):
        selected = self.out
        reads = []
        def read(path):
            reads.append(path)
            raise RuntimeError('stop before any expensive audit')
        with patch.object(verifier, 'read', side_effect=read):
            with self.assertRaises(RuntimeError):
                verifier.audit(self.repo, 'research/evaluation/runs/test')
        # Catalog is first; the selected functional file is reached after it.
        with patch.object(verifier, 'read', side_effect=lambda p: {} if p.name == 'catalog.json' else read(p)):
            with self.assertRaises(RuntimeError):
                verifier.audit(self.repo, 'research/evaluation/runs/test')
        self.assertIn(selected / 'functional.json', reads)
        with self.assertRaises(AssertionError):
            verifier.require_safe_output(self.repo, selected / 'audit.json', selected)

    def test_packer_selects_requested_evidence_before_packaging(self):
        audit = self.repo / 'saved-audit.json';audit.write_text('{}')
        argv = ['empaquetar_evidencia.py', '--repo', str(self.repo), '--evaluation-dir',
                'research/evaluation/runs/test', '--audit', str(audit),
                '--assets', str(self.repo / 'assets'), '--manifest', str(self.repo / 'manifest.json'),
                '--tag', 'new-test']
        with patch.object(sys, 'argv', argv), \
             patch.object(packer, 'audit', side_effect=RuntimeError('stop before packaging')) as examine:
            with self.assertRaises(RuntimeError):
                packer.main()
            examine.assert_called_once_with(self.repo, self.out)
        self.assertFalse((self.repo / 'assets').exists())
        self.assertFalse((self.repo / 'manifest.json').exists())

    def test_audit_rejects_active_lock_before_reading_results(self):
        (self.out / '.evaluation-lock').mkdir(parents=True)
        with patch.object(verifier, 'read') as read:
            with self.assertRaises(AssertionError):
                verifier.audit(self.repo, str(self.out))
            read.assert_not_called()

    def test_shell_prepare_only_never_creates_evaluation(self):
        script = self.repo / 'research/reproduce.sh'
        shutil.copyfile(EVALUATION.parent / 'reproduce.sh', script)
        vendor = self.repo / 'research/runtime/vendor'
        vendor.mkdir(parents=True)
        pom = vendor / 'owlapi-parent-4.5.27.pom'
        pom.write_text('fake test POM')
        (vendor / 'provenance.json').write_text(json.dumps({'sha256': hashlib.sha256(pom.read_bytes()).hexdigest()}))
        bin_dir = self.repo / 'jdk/bin';bin_dir.mkdir(parents=True)
        java = bin_dir / 'java';java.write_text('#!/bin/sh\nif [ "$1" = "-version" ]; then echo \'openjdk version "17.0.0"\' >&2; fi\n')
        java.chmod(0o755)
        mvnw = self.repo / 'backend/mvnw';mvnw.parent.mkdir()
        mvnw.write_text('#!/bin/sh\nexit 0\n');mvnw.chmod(0o755)
        for module in ['swrl-worker', 'validator']:
            target = self.repo / 'research/runtime' / module / 'target';target.mkdir(parents=True)
            (target / 'classpath.txt').write_text('fake.jar')
        env = {'JAVA_HOME': str(bin_dir.parent), 'PYTHON': sys.executable, 'PATH': '/usr/bin:/bin'}
        before = inventory(self.repo / 'research/evaluation')
        run = subprocess.run(['/bin/sh', str(script), '--prepare-only'], cwd=self.repo.parent,
                             env=env, capture_output=True, text=True)
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(before, inventory(self.repo / 'research/evaluation'))

    def test_shell_rejects_occupied_output_before_maven_or_java(self):
        script = self.repo / 'research/reproduce.sh'
        shutil.copyfile(EVALUATION.parent / 'reproduce.sh', script)
        for name in ['run_evaluation.py', 'output_paths.py']:
            shutil.copyfile(EVALUATION / name, self.repo / 'research/evaluation' / name)
        occupied = self.repo / 'research/evaluation/current'
        occupied.mkdir()
        (occupied / 'functional.json').write_text('conserved output')
        before = inventory(occupied)
        env = {'PYTHON': sys.executable, 'PATH': '/usr/bin:/bin'}  # deliberately no JDK/Maven
        result = subprocess.run(['/bin/sh', str(script), '--functional', '--output',
                                 'research/evaluation/current'], cwd=self.repo.parent,
                                env=env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Preserve existing or partial evidence', result.stderr)
        self.assertNotIn('Set JAVA_HOME', result.stderr)
        self.assertEqual(before, inventory(occupied))

    def test_auxiliary_producers_are_guarded_before_runtime(self):
        for name in ['run_evaluation.py', 'output_paths.py', 'demonstrate.py', 'capacity_pilot.py']:
            shutil.copyfile(EVALUATION / name, self.repo / 'research/evaluation' / name)
        self.out.mkdir(parents=True)
        case = self.out / 'functional/integrated/raw-input.owl'
        case.parent.mkdir(parents=True);case.write_text('owned test input')
        (self.out / 'demonstration.json').write_text('previous REST evidence')
        (self.out / 'capacity').mkdir()
        before = inventory(self.out)
        for producer in ['demonstrate.py', 'capacity_pilot.py']:
            result = subprocess.run([sys.executable, str(self.repo / 'research/evaluation' / producer),
                                     '--output', 'research/evaluation/runs/test'],
                                    cwd=self.repo.parent, env={}, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Preserve existing or partial evidence', result.stderr)
            self.assertNotIn('JAVA_HOME', result.stderr)
            self.assertEqual(before, inventory(self.out))


if __name__ == '__main__':
    unittest.main()
