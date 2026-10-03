"""Archive completed research evidence without changing its source files."""
from pathlib import Path
import argparse
import collections
import gzip
import hashlib
import json
import re
import sys
import tarfile
from verificar_benchmark import audit, require_safe_output

if sys.flags.optimize:
    raise RuntimeError('Evidence packaging must run without Python optimization')


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x') as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def inventory(source, repo):
    records = []
    for path in sorted(source.rglob('*')):
        assert not path.is_symlink(), 'Evidence must have explicit regular files'
        if path.is_file():
            records.append({'path': str(path.relative_to(repo)), 'bytes': path.stat().st_size,
                            'sha256': digest(path)})
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True, type=Path)
    parser.add_argument('--assets', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--audit', required=True, type=Path)
    args = parser.parse_args()
    repo = args.repo.resolve()
    source = repo / 'research/evaluation/current'
    require_safe_output(repo,args.manifest)
    require_safe_output(repo,args.assets)
    assert re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*',args.tag) and '..' not in args.tag, 'Tag must be a simple safe archive identifier'
    assert not args.manifest.exists(), 'Preserve previous manifest'
    assert not args.manifest.resolve().is_relative_to(source.resolve()), 'Manifest must be outside raw evidence'
    assert not args.assets.resolve().is_relative_to(source.resolve()), 'Archives must be outside raw evidence'
    assert args.audit.is_file(), 'An independently conserved audit is required'
    proof = json.loads(args.audit.read_text())
    assert proof == audit(repo), 'Inputs, binaries, requests, outputs or summaries differ from audited evidence'
    functional = json.loads((source / 'functional.json').read_text())
    summary = json.loads((source / 'summary.json').read_text())
    runs = [json.loads(line) for line in (source / 'runs.jsonl').read_text().splitlines() if line]
    assert functional['status'] == 'PASS'
    assert functional['source_hashes_unchanged'] and functional['runtime_hashes_unchanged']
    assert summary['replicates'] == 120 and len(runs) == 120
    configs = collections.Counter(r['configuration'] for r in runs)
    assert len(configs) == 4 and set(configs.values()) == {30}
    assert len({(r['configuration'], r['replica']) for r in runs}) == 120
    assert all(r['status'] == 'CONSISTENT' for r in runs)
    for stem in ['benchmark-input-hashes', 'benchmark-runtime-hashes']:
        before = json.loads((source / (stem + '.json')).read_text())
        after = json.loads((source / (stem + '-after.json')).read_text())
        assert before == after, 'Changed evaluation inputs or runtime binaries'
    hashes = json.loads((source / 'benchmark-input-hashes.json').read_text())
    for relative, expected in hashes.items():
        file = repo / relative
        assert file.resolve().is_relative_to(repo) and file.is_file()
        assert digest(file) == expected, 'Changed source: ' + relative

    records = inventory(source, repo)
    # Small independent tar files avoid a hosting size constraint and do not
    # require deleting or splitting any original record.
    groups, current, size = [], [], 0
    for record in records:
        assert record['bytes'] < 40 * 1024 * 1024, 'Individual file too large for this packer'
        if current and size + record['bytes'] > 40 * 1024 * 1024:
            groups.append(current)
            current, size = [], 0
        current.append(record)
        size += record['bytes']
    if current:
        groups.append(current)
    args.assets.mkdir(parents=True, exist_ok=True)
    for number in range(1, len(groups) + 1):
        planned = args.assets / ('protys-evidence-' + args.tag + f'-part-{number:03d}.tar.gz')
        assert not planned.exists(), 'Preserve previous archive: ' + str(planned)
    archives = []
    for number, group in enumerate(groups, 1):
        name = 'protys-evidence-' + args.tag + f'-part-{number:03d}.tar.gz'
        archive = args.assets / name
        assert not archive.exists(), 'Preserve previous archive: ' + str(archive)
        with archive.open('xb') as raw:
            with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as zipped:
                with tarfile.open(fileobj=zipped, mode='w') as tar:
                    for record in group:
                        file = repo / record['path']
                        info = tar.gettarinfo(str(file), arcname=record['path'])
                        info.uid = info.gid = info.mtime = 0
                        info.uname = info.gname = ''
                        info.mode = 0o644
                        with file.open('rb') as content:
                            tar.addfile(info, content)
        assert archive.stat().st_size < 100 * 1024 * 1024
        with tarfile.open(archive, 'r:gz') as tar:
            members = tar.getmembers()
            assert [x.name for x in members] == [x['path'] for x in group]
            for member, record in zip(members, group):
                content = tar.extractfile(member)
                h = hashlib.sha256()
                for chunk in iter(lambda: content.read(1024 * 1024), b''):
                    h.update(chunk)
                assert h.hexdigest() == record['sha256'], 'Archive verification failed'
        archives.append({'name': name, 'bytes': archive.stat().st_size,
                         'sha256': digest(archive), 'members': [x['path'] for x in group]})
    # Re-read original files after packaging to prove that packaging did not
    # rewrite any output, log, request, model or measurement.
    assert records == inventory(source, repo), 'Original raw evidence inventory changed during packaging'
    assert proof == audit(repo), 'Verified evidence, sources or binaries changed during packaging'
    manifest = {'schema': 'protys-research-evidence-v1', 'tag': args.tag,
                'functional_status': 'PASS', 'replicates': 120,
                'configuration_counts': dict(configs),
                'source_hashes': hashes, 'files': records, 'archives': archives,
                'independent_audit_sha256': digest(args.audit),
                'global_evidence_hashes': proof['global_evidence_hashes'],
                'verifier_script_sha256': digest(Path(__file__).with_name('verificar_benchmark.py')),
                'packer_script_sha256': digest(Path(__file__)),
                'coverage': 'All regular files in research/evaluation/current at packaging time',
                'originals_unchanged': True,
                'publication_status': 'PACKAGED_PENDING_REMOTE_VERIFICATION'}
    write_json(args.manifest, manifest)
    print(json.dumps({'archives': len(archives), 'files': len(records),
                      'uncompressed_bytes': sum(x['bytes'] for x in records),
                      'manifest': str(args.manifest)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
