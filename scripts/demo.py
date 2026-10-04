#!/usr/bin/env python3
"""Start, check and stop an owned Docker demonstration without touching other stacks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / '.local-demo'
COMPOSE = ROOT / 'docker/docker-compose.yml'
PORTS = {'frontend': 'PROTYS_FRONTEND_PORT', 'backend': 'PROTYS_BACKEND_PORT',
         'fuseki': 'PROTYS_FUSEKI_PORT', 'postgres': 'PROTYS_POSTGRES_PORT'}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    with path.open('x') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')

def free_ports():
    sockets = []
    try:
        for _ in PORTS:
            s = socket.socket()
            s.bind(('127.0.0.1', 0))
            sockets.append(s)
        return dict(zip(PORTS, (s.getsockname()[1] for s in sockets)))
    finally:
        for s in sockets:
            s.close()

def load(state_path):
    state_path = state_path.resolve()
    value = json.loads(state_path.read_text())
    if value.get('kind') != 'PROTYS_OWNED_LOCAL_DEMO' or not re.fullmatch(r'protys-demo-[a-f0-9]{12}', value.get('project', '')):
        raise ValueError('Not a state file for an owned PROTYS demonstration')
    if value.get('repository') != str(ROOT):
        raise ValueError('State belongs to another checkout; use its original scripts/demo.py')
    definition = Path(value.get('compose_file', '')).resolve()
    if definition.parent != state_path.parent or definition.name != 'compose.yml' or sha(definition) != value.get('compose_sha256'):
        raise ValueError('Original Compose definition absent or changed; no resources were managed')
    return value

def compose(state, *args, log=None):
    definition = Path(state['compose_file'])
    if sha(definition) != state['compose_sha256']:
        raise ValueError('Original Compose definition changed; no resources were managed')
    env = dict(os.environ, **{PORTS[k]: str(v) for k, v in state['ports'].items()})
    command = ['docker', 'compose', '--project-directory', str(ROOT / 'docker'),
               '-f', str(definition), '-p', state['project'], *args]
    return subprocess.run(command, env=env, text=True, stdout=log, stderr=subprocess.STDOUT if log else None, check=True)

def request(base, path, body=None, method=None, metadata=None, timeout=240):
    data = json.dumps(body).encode() if body is not None else None
    # Browser mutations send Origin even through a same-origin reverse proxy.
    headers = {'Origin': base}
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        if metadata is not None:
            metadata.update(http_status=response.status, content_type=response.headers.get('Content-Type'))
        return json.load(response)

def verify_frontend(state, expected_id=None):
    ids = subprocess.check_output(['docker', 'ps', '--filter', 'label=com.docker.compose.project=' + state['project'],
                                   '--filter', 'label=com.docker.compose.service=protys-frontend', '--format', '{{.ID}}'], text=True).split()
    if len(ids) != 1:
        raise RuntimeError('Owned frontend is not running; no API operation was attempted')
    details = json.loads(subprocess.check_output(['docker', 'inspect', ids[0]], text=True))[0]
    labels = details['Config'].get('Labels', {})
    ports = details['NetworkSettings']['Ports'].get('80/tcp') or []
    correct_port = any(p.get('HostIp') == '127.0.0.1' and p.get('HostPort') == str(state['ports']['frontend']) for p in ports)
    if labels.get('com.docker.compose.project') != state['project'] or labels.get('com.docker.compose.service') != 'protys-frontend' or not correct_port:
        raise RuntimeError('Frontend ownership or port differs; no API operation was attempted')
    if expected_id is not None and details['Id'] != expected_id:
        raise RuntimeError('Original frontend container was replaced; no API operation was attempted')
    if details.get('State', {}).get('Health', {}).get('Status') != 'healthy':
        raise RuntimeError('Owned frontend health is not confirmed; no API operation was attempted')
    return details['Id']

def up(_args):
    LOCAL.mkdir(exist_ok=True)
    ident = uuid.uuid4().hex[:12]
    work = LOCAL / ident
    work.mkdir()
    state_path = work / 'state.json'
    definition = work / 'compose.yml'
    shutil.copy2(COMPOSE, definition)
    state = dict(kind='PROTYS_OWNED_LOCAL_DEMO', project='protys-demo-' + ident,
                 repository=str(ROOT), ports=free_ports(), created_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                 compose_sha256=sha(definition), compose_file=str(definition))
    write(state_path, state)
    print('Preparing isolated demonstration. Build log: ' + str(work / 'startup.log'), flush=True)
    try:
        with (work / 'startup.log').open('x') as stream:
            compose(state, 'up', '--build', '-d', log=stream)
        base = 'http://127.0.0.1:' + str(state['ports']['frontend'])
        deadline = time.monotonic() + 240
        while True:
            try:
                frontend_id = verify_frontend(state)
                with urllib.request.urlopen(base + '/health', timeout=5) as response:
                    if response.status != 200:
                        raise RuntimeError('Frontend health did not return HTTP 200')
                health = request(base, '/api/dashboard/health', timeout=5)
                modules = request(base, '/api/ontology/modules', timeout=5)
                rules = request(base, '/api/alignment/rules', timeout=5)
                templates = request(base, '/api/sparql/templates', timeout=5)
                if not modules or len(rules) != 22 or len(templates) != 21:
                    raise RuntimeError('Canonical module, 22 rules and 21 templates are not ready')
                break
            except (OSError, ValueError, RuntimeError):
                if time.monotonic() >= deadline:
                    raise RuntimeError('Startup not confirmed; see startup.log and compose logs')
                time.sleep(2)
        write(work / 'ready.json', dict(status='READY', frontend=base, frontend_container_id=frontend_id, state_file=str(state_path),
                                       health=health, registered_modules=len(modules), rules=len(rules), templates=len(templates)))
        print(json.dumps(dict(status='READY', frontend=base, state_file=str(state_path)), indent=2))
    except BaseException:
        # Only this command's randomly named project is stopped; data/logs remain.
        try:
            compose(state, 'down')
        except subprocess.SubprocessError:
            pass
        raise

def smoke(args):
    state = load(args.state)
    ready = json.loads((args.state.resolve().parent / 'ready.json').read_text())
    expected_frontend_id = ready['frontend_container_id']
    dest = args.state.resolve().parent / ('smoke-' + uuid.uuid4().hex[:12])
    dest.mkdir()
    base = 'http://127.0.0.1:' + str(state['ports']['frontend'])
    sys.path.insert(0, str(ROOT / 'research/evaluation'))
    from run_evaluation import equivalent
    catalog = json.loads((ROOT / 'research/catalog.json').read_text())
    checks = []
    report = dict(status='FAIL', scope='Docker services and Nginx API proxy on the simulated functional case',
                  state_file=str(args.state.resolve()), query_catalog_sha256=sha(ROOT / 'research/catalog.json'), checks=checks)

    def observed(name, path, body=None, method=None):
        # Reject a stale state/port before every operation, especially mutations.
        verify_frontend(state, expected_frontend_id)
        write(dest / (name + '-request.json'), dict(url=base + path, origin=base, method=method or ('POST' if body is not None else 'GET'), body=body))
        metadata = {}
        try:
            value = request(base, path, body, method, metadata)
            write(dest / (name + '.json'), value)
            return value
        except urllib.error.HTTPError as error:
            metadata['http_status'] = error.code
            write(dest / (name + '-http-error.json'), dict(body=error.read().decode('utf-8', errors='replace')))
            raise
        finally:
            write(dest / (name + '-transport.json'), metadata)

    def check(name, condition):
        checks.append(dict(id=name, passed=bool(condition)))
        if not condition:
            raise RuntimeError('Check failed: ' + name)

    r03 = None
    toggled = False
    try:
        rules = observed('rules', '/api/alignment/rules')
        check('REGISTERED_22_RULES', len(rules) == 22)
        initial = observed('reasoning', '/api/alignment/reasoning/execute', {})
        check('CONSISTENT_WITH_22_RULES', initial.get('validationStatus') == 'CONSISTENT' and initial.get('activeRules') == 22)
        check('VALID_DECLARED_CONTEXT', initial.get('contextEvaluation', {}).get('status') == 'CONTEXT_VALID')
        check('VALID_NUMERICAL_RECORDS', initial.get('numericEvaluation', {}).get('status') == 'VALID')
        check('CLEANING_MISSING_RECORD_VISIBLE', initial.get('cleaningEvaluation', {}).get('status') == 'MISSING_CLEANING_RECORD')
        cached = observed('reasoning-cached', '/api/alignment/reasoning/execute', {})
        check('CACHE_REPORTS_ZERO_REASONING_TIME', cached.get('cacheHit') is True and cached.get('reasoningTimeMs') == 0)
        for q in catalog['queries']:
            result = observed(q['id'], '/api/sparql/execute', {'query': (ROOT / q['path']).read_text()})
            expected = json.loads((ROOT / q['expected_path']).read_text())
            check(q['id'] + '_COMPLETE_EXPECTED_OUTPUT', equivalent(result.get('sparqlJson', {}), expected))
        r03 = next(rule for rule in rules if rule['name'].startswith('R03'))
        query = 'PREFIX a: <http://w3id.org/protys/ontology/alignment#> ASK { ?lot a:hasRecordedInputFlowForLot ?flow }'
        check('R03_ACTIVE', observed('R03-on', '/api/sparql/execute', {'query': query}).get('askResult') is True)
        # Restoration is owed even if the server applies this change but its response is lost.
        toggled = True
        observed('R03-toggle-off', '/api/alignment/rules/' + r03['id'] + '/toggle?active=false', {}, 'PUT')
        disabled = observed('reasoning-R03-off', '/api/alignment/reasoning/execute', {})
        check('R03_DISABLED_NEW_MATERIALIZATION', disabled.get('activeRules') == 21 and disabled.get('cacheHit') is False)
        check('R03_DISABLED_REMOVES_RESULT', observed('R03-off', '/api/sparql/execute', {'query': query}).get('askResult') is False)
        observed('R03-toggle-on', '/api/alignment/rules/' + r03['id'] + '/toggle?active=true', {}, 'PUT')
        toggled = False
        restored = observed('reasoning-R03-restored', '/api/alignment/reasoning/execute', {})
        check('R03_RESTORED_MATERIALIZATION', restored.get('activeRules') == 22)
        check('R03_RESTORED_RESULT', observed('R03-restored', '/api/sparql/execute', {'query': query}).get('askResult') is True)
        report['status'] = 'PASS'
    except Exception as error:
        report['error'] = str(error)
    finally:
        if toggled and r03 is not None:
            try:
                observed('R03-emergency-restore', '/api/alignment/rules/' + r03['id'] + '/toggle?active=true', {}, 'PUT')
                observed('R03-emergency-materialization', '/api/alignment/reasoning/execute', {})
                report['rule_state_restored_after_failure'] = True
            except Exception as error:
                report['restore_error'] = str(error)
        write(dest / 'report.json', report)
    print(json.dumps(dict(status=report['status'], checks=len(checks), report=str(dest / 'report.json'), error=report.get('error')), indent=2))
    return 0 if report['status'] == 'PASS' else 1

def down(args):
    state = load(args.state)
    command = ['down'] + (['--volumes'] if args.remove_data else [])
    compose(state, *command)
    print('Owned demonstration stopped. Logs retained. ' + ('Owned data removed.' if args.remove_data else 'Data volumes retained.'))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('up', help='build and start a fresh isolated interactive demonstration').set_defaults(handler=up)
    p = commands.add_parser('smoke', help='check real API proxy, materialization and all 21 queries')
    p.add_argument('--state', type=Path, required=True)
    p.set_defaults(handler=smoke)
    p = commands.add_parser('down', help='stop only the project named in its state file')
    p.add_argument('--state', type=Path, required=True)
    p.add_argument('--remove-data', action='store_true', help='also remove this demonstration\'s data volumes')
    p.set_defaults(handler=down)
    args = parser.parse_args()
    try:
        return args.handler(args) or 0
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
