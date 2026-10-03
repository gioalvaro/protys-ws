"""Plot conserved, independently audited measurements; never invent data."""
import argparse
import collections
import hashlib
import json
import math
import platform
import sys
from pathlib import Path

# The evidence conditions must not disappear under python -O.
if sys.flags.optimize:
    raise RuntimeError('Figure provenance checks require Python without optimization')


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_output_directory(path, inputs):
    destination = path.resolve()
    if path.exists() or path.is_symlink():
        raise FileExistsError('Preserve previous figures; choose a new directory: '+str(path))
    if any(source.resolve().is_relative_to(destination) for source in inputs):
        raise ValueError('The figure directory cannot contain an input or its audit')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runs', type=Path, required=True)
    parser.add_argument('--audit', type=Path, required=True)
    parser.add_argument('--output-directory', type=Path, required=True)
    args = parser.parse_args()
    check_output_directory(args.output_directory, [args.runs, args.audit])
    input_hashes = {'runs':sha256(args.runs), 'audit':sha256(args.audit)}
    proof = json.loads(args.audit.read_text())
    assert proof['status'] == 'PASS' and proof['replicates'] == 120
    assert proof['global_evidence_hashes']['runs.jsonl'] == input_hashes['runs']
    for field in ['source_and_binary_hashes_before_after_match', 'current_source_files_match',
                  'current_runtime_binaries_match', 'functional_raw_results_recotejado']:
        assert proof[field] is True, field
    runs = [json.loads(line) for line in args.runs.read_text().splitlines() if line]
    configs = ['iso15531', 'iso14040', 'union', 'integrated']
    assert len(runs) == 120
    assert collections.Counter(x['configuration'] for x in runs) == {x:30 for x in configs}
    assert proof['replicates_by_configuration'] == {x:30 for x in configs}
    assert all(type(x['replica']) is int and type(x['order']) is int for x in runs)
    assert collections.Counter((x['configuration'],x['replica']) for x in runs) == {(c,i):1 for c in configs for i in range(30)}
    assert [x['order'] for x in runs] == list(range(1,121))
    assert [(x['order'],x['configuration'],x['replica']) for x in proof['replicate_evidence']] == [(x['order'],x['configuration'],x['replica']) for x in runs]
    assert all(x['status'] == 'CONSISTENT' for x in runs)
    assert all(isinstance(x['total_ms'], (int,float)) and not isinstance(x['total_ms'],bool)
               and math.isfinite(x['total_ms']) and x['total_ms'] >= 0 for x in runs)
    assert all(type(x['observed_peak_process_tree_rss_bytes']) is int
               and x['observed_peak_process_tree_rss_bytes'] > 0 for x in runs)
    import matplotlib
    matplotlib.use('Agg')
    from matplotlib import font_manager, pyplot as plt
    plt.rcParams.update({'font.family':'sans-serif',
                         'font.sans-serif':['Liberation Sans', 'Arial', 'DejaVu Sans'],
                         'font.size':9.5, 'axes.titlesize':11, 'axes.labelsize':10,
                         'xtick.labelsize':9.5, 'ytick.labelsize':9.5,
                         'svg.fonttype':'none', 'pdf.fonttype':42})
    labels = ['ISO 15531', 'ISO 14040', 'Unión sin\nalineamiento', 'Modelo\nintegrado']
    colors = ['#1784bf', '#3a8b62', '#bf871d', '#8256a6']
    fig, axes = plt.subplots(2, 1, figsize=(6.27, 5.5))
    metrics = [('total_ms', 1000, 'Tiempo total interno (s)'),
               ('observed_peak_process_tree_rss_bytes', 1024**2, 'Máximo observado de RSS (MiB)')]
    for ax, (metric, divisor, label) in zip(axes, metrics):
        samples = [[x[metric]/divisor for x in runs if x['configuration'] == config] for config in configs]
        box = ax.boxplot(samples, positions=range(1,5), widths=.44, patch_artist=True,
                         showfliers=False, medianprops={'color':'#111111','linewidth':1.4},
                         whiskerprops={'linewidth':.8}, capprops={'linewidth':.8})
        for position, (values, color, patch) in enumerate(zip(samples, colors, box['boxes']), 1):
            patch.set(facecolor=color, alpha=.22, edgecolor=color, linewidth=1)
            offsets = [position - .11 + .22*i/29 for i in range(30)]
            ax.scatter(offsets, values, s=10, color=color, alpha=.8, linewidths=0, zorder=3)
        ax.set_xticks(range(1,5), labels)
        ax.set_ylabel(label)
        ax.set_ylim(bottom=0)
        ax.set_xlim(.55,4.45)
        ax.grid(axis='y', color='#c8cdd3', linewidth=.5, alpha=.6)
        ax.set_axisbelow(True)
        ax.spines[['top','right']].set_visible(False)
    fig.suptitle('Variabilidad de 30 réplicas por configuración', fontsize=12)
    fig.text(.5,.015,'Puntos: réplicas; cajas: rango intercuartílico; línea: mediana.',
             ha='center',fontsize=9.5)
    fig.tight_layout(rect=(0,.05,1,.96),h_pad=1.3)
    args.output_directory.mkdir(parents=True,exist_ok=False)
    outputs=[]
    for extension in ['png','svg','pdf']:
        path=args.output_directory/('figura4_7_resultados_definitivos.'+extension)
        metadata={'Date':None} if extension=='svg' else {'CreationDate':None,'ModDate':None} if extension=='pdf' else {}
        with path.open('xb') as stream:
            fig.savefig(stream,format=extension,dpi=600,facecolor='white',metadata=metadata)
        outputs.append({'path':str(path),'sha256':sha256(path),'bytes':path.stat().st_size})
    plt.close(fig)
    assert input_hashes == {'runs':sha256(args.runs), 'audit':sha256(args.audit)}, 'Inputs changed while plotting'
    manifest={'status':'CREATED_PENDING_VISUAL_QA',
              'source_runs_sha256':input_hashes['runs'],'independent_audit_sha256':input_hashes['audit'],
              'plotter_script_sha256':sha256(Path(__file__)),
              'verifier_script_sha256':proof['verifier_script_sha256'],
              'python_version':platform.python_version(),
              'matplotlib_version':matplotlib.__version__,
              'resolved_font':font_manager.findfont(font_manager.FontProperties(family=['sans-serif'])),
              'figure_size_inches':[6.27,5.5], 'minimum_font_pt':9.5,'outputs':outputs,
              'method':'All 30 conserved replicates per configuration; no bootstrap or fabricated values. Whiskers use 1.5 IQR; all observed points are shown. Fixed display offsets do not modify measurements.',
              'interpretation':'Configurations perform different semantic tasks. Internal time excludes process startup. RSS is the sum sampled every 100 ms, not instantaneous memory peak. No industrial observations or speed superiority are inferred.'}
    with (args.output_directory/'FIGURA4_7_PROCEDENCIA.json').open('x') as stream:
        stream.write(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':manifest['status'],'outputs':outputs},ensure_ascii=False))


if __name__ == '__main__':
    main()
