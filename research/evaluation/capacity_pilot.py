#!/usr/bin/env python3
"""Confirm the chosen 100-lot load; never counted among the 120 benchmark replicas."""
import hashlib,json,os
from pathlib import Path
from run_evaluation import ROOT,execute,save,specification,execution_runtime_hashes,artifact_hashes

dest=ROOT/'research/evaluation'/'provisional'/'capacity' if os.environ.get('PROTYS_PROVISIONAL')=='1' else ROOT/'research/evaluation/current/capacity'
cat=json.loads((ROOT/'research/catalog.json').read_text());cfg=next(c for c in cat['configurations'] if c['id']=='integrated')
specs={"medium":specification(cat,cfg,"research/data/load-medium.ttl")}
inputs=sorted({path for spec in specs.values() for path in spec['files']+spec['pre_inference_constructs']+[q['path'] for q in spec['queries']]})
hashes={path:hashlib.sha256((ROOT/path).read_bytes()).hexdigest() for path in inputs}
snapshot=dest/'snapshot'
for path in inputs:
 file=snapshot/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes((ROOT/path).read_bytes())
if any(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()!=hashes[path] or hashlib.sha256((snapshot/path).read_bytes()).hexdigest()!=hashes[path] for path in inputs):raise RuntimeError('Inputs changed while making pilot snapshot')
save(dest/'input-hashes.json',hashes)
source_hashes=artifact_hashes();save(dest/'source-hashes-before.json',source_hashes)
runtime_hashes=execution_runtime_hashes();save(dest/'runtime-hashes.json',runtime_hashes)
report={'status':'PASS','provisional':os.environ.get('PROTYS_PROVISIONAL')=='1','scope':'Technical capacity selection on complete synthetic lots; separate from statistical evaluation.','criterion':'Confirm the selected 100 complete lots finish all21SELECT/fourASK and consistency checks within240seconds, parent/worker heaps2GiB each. Historical200-lot timeout is preserved separately; it is not rerun or included among accepted metrics.','pilots':[]}
for name,spec in specs.items():
 spec['files']=[str(snapshot/path) for path in spec['files']]
 spec['pre_inference_constructs']=[str(snapshot/path) for path in spec['pre_inference_constructs']]
 for query in spec['queries']:query['path']=str(snapshot/query['path'])
 try:
  result=execute(spec,dest/name)
  passed=(result.get('status')=='CONSISTENT' and result.get('owl_validation_status')=='CONSISTENT'
          and result.get('numeric_evaluation',{}).get('status')=='VALID'
          and result.get('profile_valid') is True and len(result.get('queries',[]))==25
          and result.get('swrl',{}).get('imported_rule_count')==22
          and result.get('exchange_hashes_verified') is True)
 except Exception as error:result={'status':'NOT_EVALUATED','error':str(error)};passed=False
 report['pilots'].append({'dataset':name,'lots':100,'pass':passed,'result':result})
 save(dest/'capacity.json',report)
 print(name+': '+str(result.get('status'))+' '+str(round(result.get('wall_process_ms',0),1))+'ms',flush=True)
eligible=[p for p in report['pilots'] if p['pass']]
report['selected_dataset']=eligible[-1]['dataset'] if eligible else None
source_after=artifact_hashes();runtime_after=execution_runtime_hashes()
save(dest/'source-hashes-after.json',source_after);save(dest/'runtime-hashes-after.json',runtime_after)
report['source_hashes_unchanged']=source_hashes==source_after and all(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==value for path,value in hashes.items())
report['runtime_hashes_unchanged']=runtime_hashes==runtime_after
if not report['source_hashes_unchanged'] or not report['runtime_hashes_unchanged']:report['status']='FAIL'
if not eligible:report['status']='FAIL'
save(dest/'capacity.json',report)
print(json.dumps({key:report[key] for key in ['status','provisional','selected_dataset']},indent=2))
raise SystemExit(0 if report['status']=='PASS' else 1)
