"""Output selection and exclusive phase reservations; no evidence is deleted."""
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[2]


def evaluation_dir(value=None, *, repo=ROOT, fresh=False):
    """Relative arguments/env values always refer to the repository, not cwd."""
    value = value or os.environ.get('PROTYS_EVALUATION_OUT')
    base = (repo / 'research/evaluation').resolve()
    if value:
        path = Path(value).expanduser()
        path = (path if path.is_absolute() else repo / path).resolve()
    elif fresh:
        name = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex
        path = base / 'runs' / name
    else:
        path = base / 'current'
    if path == base or not path.is_relative_to(base):
        raise ValueError('Evaluation directory must be a child of ' + str(base))
    if path.exists() and not path.is_dir():
        raise ValueError('Evaluation output is not a directory: ' + str(path))
    return path


PHASE_OUTPUTS = {
    'functional': ['functional', 'assertion-groups', 'functional.json'],
    'benchmark': ['runs', 'runs.jsonl', 'summary.json', 'summary.csv', 'replicate-order.json', 'benchmark-environment.json',
                  'benchmark-input-hashes.json', 'benchmark-input-hashes-after.json',
                  'benchmark-runtime-hashes.json', 'benchmark-runtime-hashes-after.json'],
    'capacity': ['capacity', 'capacity.json'],
    'demonstration': ['demonstration', 'demonstration.json'],
}
INITIAL_OUTPUTS = ['environment.json', 'artifacts.json', 'runtime-binaries.json', 'runtime']


def check_phases(out, phases):
    for phase in phases:
        paths = PHASE_OUTPUTS[phase] + ['.phase-' + phase + '.json']
        if phase == 'functional':
            paths += INITIAL_OUTPUTS
        for relative in paths:
            if (out / relative).exists() or (out / relative).is_symlink():
                raise FileExistsError('Preserve existing or partial evidence: ' + str(out / relative)
                                      + '. Select a new evaluation directory; phases are not resumed.')
    if 'benchmark' in phases and 'functional' not in phases and not (out / 'functional.json').is_file():
        raise FileNotFoundError('Benchmark requires functional.json in selected evaluation: ' + str(out))


@contextmanager
def reserve_phases(out, phases):
    """An interrupted phase retains its reservation and partial evidence."""
    check_phases(out, phases)
    out.mkdir(parents=True, exist_ok=True)
    lock = out / '.evaluation-lock'
    lock.mkdir()  # atomic exclusion for cooperating evaluation tools
    try:
        check_phases(out, phases)
        for phase in phases:
            with (out / ('.phase-' + phase + '.json')).open('x') as stream:
                json.dump({'phase': phase, 'pid': os.getpid(), 'status': 'RESERVED',
                           'timestamp_utc': datetime.now(timezone.utc).isoformat()}, stream)
                stream.write('\n')
        yield
    finally:
        lock.rmdir()
