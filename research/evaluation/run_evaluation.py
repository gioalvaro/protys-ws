#!/usr/bin/env python3
"""Reproducible academic evaluation. Golden outputs are authored independently."""
import argparse,collections,csv,hashlib,json,math,os,platform,random,signal,statistics,subprocess,time
from decimal import Decimal, DecimalException
from pathlib import Path
from output_paths import evaluation_dir,check_phases,reserve_phases
ROOT=Path(__file__).resolve().parents[2]
OUT=evaluation_dir()
JAVA=None
CP=None
def configure_runtime():
 global JAVA,CP
 JAVA=Path(os.environ['JAVA_HOME'])/'bin/java'
 CP=str(ROOT/'research/runtime/validator/target/classes')+os.pathsep+(ROOT/'research/runtime/validator/target/classpath.txt').read_text().strip()
def save(path,value):
 path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('x') as stream:stream.write(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
def execute(spec,target):
 if not target.resolve().is_relative_to((ROOT/'research/evaluation').resolve()):raise RuntimeError('Output must belong to evaluation directory')
 target.mkdir(parents=True,exist_ok=False);save(target/'request.json',spec)
 if JAVA is None:configure_runtime()
 cmd=[str(JAVA),'-Xmx2g','-Djava.awt.headless=true','-cp',CP,'org.protys.research.AcademicRunner',str(ROOT),str(target/'request.json'),str(target)]
 start=time.monotonic();peak=0;timed_out=False
 with (target/'stdout.txt').open('x') as stdout,(target/'stderr.txt').open('x') as stderr:
  proc=subprocess.Popen(cmd,stdout=stdout,stderr=stderr,start_new_session=True)
  try:
   while proc.poll() is None:
    try:
     rows=[list(map(int,line.split())) for line in subprocess.check_output(['ps','-axo','pid,ppid,rss'],text=True).splitlines()[1:] if len(line.split())==3]
     family={proc.pid}
     for _ in range(3):family|={pid for pid,ppid,rss in rows if ppid in family}
     peak=max(peak,sum(rss*1024 for pid,ppid,rss in rows if pid in family))
    except (ValueError,subprocess.SubprocessError):pass
    if time.monotonic()-start>240:timed_out=True;os.killpg(proc.pid,signal.SIGKILL);proc.wait();break
    time.sleep(.1)
  except BaseException:
   # The parent JVM owns a fresh process group, including its SWRL child.
   # An interrupted evaluation must leave neither running nor certified output.
   if proc.poll() is None:
    os.killpg(proc.pid,signal.SIGTERM)
    try:proc.wait(timeout=5)
    except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
   raise
 code=proc.wait();validation_file=target/'validation-after-swrl.json' if (target/'validation-after-swrl.json').exists() else target/'validation.json'
 if (target/'run.json').exists() and code==0:result=json.loads((target/'run.json').read_text())
 else:
  validation=json.loads(validation_file.read_text()) if validation_file.exists() else {}
  failure_status=validation.get('status') if validation.get('status') in ['INCONSISTENT','NOT_EVALUATED'] else 'NOT_EVALUATED'
  result={'status':failure_status,'failure_kind':validation.get('failure_kind','PROCESS_ERROR'),'last_completed_validation':dict(validation),'validation':dict(validation,status=failure_status),'stderr':(target/'stderr.txt').read_text()[-5000:]}
  for key in ['owl_validation_status','numeric_evaluation','cleaning_evaluation','context_evaluation','error']:
   if key in validation:result[key]=validation[key]
 if timed_out:result={'status':'NOT_EVALUATED','failure_kind':'TIMEOUT','timeout_seconds':240,'error':'Process exceeded configured time limit; no completed timing/result accepted.','last_completed_validation':result.get('validation',{}),'validation':{'status':'NOT_EVALUATED'}}
 result.update(exit_code=code,wall_process_ms=(time.monotonic()-start)*1000,observed_peak_process_tree_rss_bytes=peak,rss_sampling_interval_ms=100)
 if result.get('status')=='CONSISTENT':
  expected_ids=[q['id'] for q in spec.get('queries',[])];actual_ids=[q.get('id') for q in result.get('queries',[])]
  missing_outputs=[ident for ident in expected_ids if not (target/(ident+'.json')).is_file()]
  if collections.Counter(expected_ids)!=collections.Counter(actual_ids) or missing_outputs:
   result.update(status='NOT_EVALUATED',failure_kind='INCOMPLETE_QUERIES',error='Required query execution/output missing',expected_query_ids=expected_ids,actual_query_ids=actual_ids,missing_query_outputs=missing_outputs)
 save(target/'execution.json',result);return result

def specification(cat,cfg,load=None):
 if not isinstance(cfg.get('numeric_controls_enabled'),bool):raise RuntimeError('Configuration must explicitly declare numeric_controls_enabled boolean: '+str(cfg.get('id')))
 files=cfg.get('files',cfg.get('tbox_paths',[])+cfg.get('abox_paths',[])+cfg.get('rules_paths',[]))
 if load:files=[x for x in files if x not in cfg.get('abox_paths',[])]+(load if isinstance(load,list) else [load])
 ruleids=cfg.get('enabled_rule_ids',[]);enabled=cfg.get('enabled_rules','ALL' if ruleids=='ALL' else ','.join(ruleids) if ruleids else 'NONE')
 queries=[q for q in cat['queries'] if q['id'] in cfg.get('query_ids',[q['id'] for q in cat['queries']])]
 queries+=[q for q in cat.get('validations',[]) if q['id'] in cfg.get('validation_ids',[q['id'] for q in cat.get('validations',[])])]
 return {'files':list(dict.fromkeys(files)),'enabled_rules':enabled_rules_text(enabled),'numeric_controls_enabled':cfg['numeric_controls_enabled'],'queries':[{'id':q['id'],'path':q['path']} for q in queries],'pre_inference_constructs':cat.get('pre_inference_constructs',[]) if cfg.get('run_pre_inference_constructs') else []}

def enabled_rules_text(value):
 if isinstance(value,list):
  if not all(isinstance(ident,str) and ident and ',' not in ident for ident in value):raise RuntimeError('Rule selection must contain nonempty textual identifiers')
  return ','.join(value) or 'NONE'
 if not isinstance(value,str) or not value:raise RuntimeError('Rule selection must be a string or list of textual identifiers')
 return value

def fixture_specification(cat,assertion):
 cfg=next(x for x in cat['configurations'] if x['id']==assertion['configuration']);spec=specification(cat,cfg)
 spec['files']+=assertion.get('additional_files',[])
 if assertion.get('files'):spec['files']=assertion['files']
 if assertion.get('enabled_rule_ids') is not None:spec['enabled_rules']=assertion['enabled_rule_ids']
 if assertion.get('enabled_rules') is not None:spec['enabled_rules']=assertion['enabled_rules']
 # Fixture overrides may use JSON arrays; the Java interchange contract is
 # textual selection (ALL/NONE/OWL_ONLY or comma-separated rule identifiers).
 spec['enabled_rules']=enabled_rules_text(spec['enabled_rules'])
 spec['queries']=[]
 return spec

def equivalent(actual,expected):
 if 'boolean' in expected:return isinstance(actual.get('boolean'),bool) and isinstance(expected['boolean'],bool) and actual['boolean'] is expected['boolean']
 if set(actual.get('head',{}).get('vars',[]))!=set(expected.get('head',{}).get('vars',[])):return False
 xsd='http://www.w3.org/2001/XMLSchema#'
 numerics={xsd+name for name in ['integer','decimal','double','float','int','long','short','nonNegativeInteger']}
 def value_eq(a,b):
  if a.get('type')!=b.get('type'):return False
  if a.get('type')=='literal' and a.get('datatype') in numerics and b.get('datatype') in numerics:
   if 'xml:lang' in a or 'xml:lang' in b:return False
   try:
    x,y=Decimal(a['value']),Decimal(b['value'])
    return x.is_finite() and y.is_finite() and abs(x-y)<=max(Decimal('1e-9'),Decimal('1e-9')*max(abs(x),abs(y)))
   except (DecimalException,ValueError,TypeError):return False
  if a.get('type')=='literal' and 'xml:lang' not in a and 'xml:lang' not in b:
   string=xsd+'string'
   if a.get('datatype',string)==string and b.get('datatype',string)==string:return a.get('value')==b.get('value')
  return a==b
 obtained=list(actual.get('results',{}).get('bindings',[]));wanted=list(expected.get('results',{}).get('bindings',[]))
 if len(obtained)!=len(wanted):return False
 # Approximate numeric equality can overlap. Require a complete one-to-one
 # matching, rather than committing to the first compatible row greedily.
 edges=[[i for i,got in enumerate(obtained) if row.keys()==got.keys() and all(value_eq(got[k],v) for k,v in row.items())] for row in wanted]
 matched={}
 def assign(index,seen):
  for candidate in edges[index]:
   if candidate in seen:continue
   seen.add(candidate)
   if candidate not in matched or assign(matched[candidate],seen):
    matched[candidate]=index;return True
  return False
 return all(assign(index,set()) for index in range(len(wanted)))

def functional(cat):
 hashes_before=artifact_hashes();runtime_before=execution_runtime_hashes()
 if cat!=json.loads((ROOT/'research/catalog.json').read_text()):raise RuntimeError('Catalog changed before functional execution')
 report={'status':'PASS','configurations':[],'golden_checks':[],'assertions':[],'input_hashes_before':hashes_before,'runtime_hashes_before':runtime_before}
 for cfg in cat['configurations']:
  if cfg.get('benchmark_only'):continue
  dest=OUT/'functional'/cfg['id'];run=execute(specification(cat,cfg),dest);report['configurations'].append({'id':cfg['id'],'result':run})
  if run.get('status')!='CONSISTENT':report['status']='FAIL';continue
  expected_count=len(cfg.get('enabled_rule_ids',[]))
  actual_count=run.get('swrl',{}).get('imported_rule_count',0)
  report.setdefault('rule_import_checks',[]).append({'configuration':cfg['id'],'expected':expected_count,'imported':actual_count,'pass':expected_count==actual_count})
  if expected_count!=actual_count:report['status']='FAIL'
  for q in cat['queries']:
   configuration=cfg.get('expected_results_configuration',cfg['id'])
   expected_path=q.get('expected_paths',{}).get(configuration,q.get('expected_path') if configuration=='integrated' else None)
   if not expected_path or not (ROOT/expected_path).exists() or not (dest/(q['id']+'.json')).exists():
    report['golden_checks'].append({'configuration':cfg['id'],'id':q['id'],'pass':False,'error':'Required expected or obtained result is absent'});report['status']='FAIL';continue
   expected=json.loads((ROOT/expected_path).read_text());actual=json.loads((dest/(q['id']+'.json')).read_text());passed=equivalent(actual,expected)
   report['golden_checks'].append({'configuration':cfg['id'],'id':q['id'],'pass':passed,'expected_path':expected_path,'expected_rows':len(expected.get('results',{}).get('bindings',[])),'actual_rows':len(actual.get('results',{}).get('bindings',[]))})
   if not passed:report['status']='FAIL'
 tests=ROOT/'research/data/fixtures/tests.json'
 if not tests.exists():raise RuntimeError('Required authored fixtures/tests.json is absent')
 if tests.exists():
  fixture=json.loads(tests.read_text())
  assertion_ids=[a['id'] for a in fixture.get('assertions',[])]
  integrated=next(x for x in cat['configurations'] if x['id']=='integrated')
  required={rule+'_'+side for rule in integrated['enabled_rule_ids'] for side in ['positive','negative']}
  required|={q['id']+'_row_'+side for q in cat['queries'] for side in ['positive','negative']}
  required|={'R07_missing','R07_complete','R16_missing','R16_complete','R19_missing','R19_complete','R26_missing','R26_valid','H3_components_same_identity','H3_source_classes_preserved','H3_same_named_individual_set','H3_pre_extension_no_classification','Failure_load_error','Failure_profile_invalid','Failure_inconsistent'}
  required|={'CCTX_complete','CCTX_foreign_link'}
  required|={rule+'_fractional_'+str(index) for rule in ['R11','R23'] for index in [1,2,3]}
  if len(assertion_ids)!=len(set(assertion_ids)) or not required.issubset(assertion_ids):
   raise RuntimeError('Required independent fixture coverage missing or duplicated: '+str(sorted(required-set(assertion_ids))))
  report['expected_assertion_count']=len(assertion_ids)
  groups={}
  for assertion in fixture.get('assertions',[]):
   spec=fixture_specification(cat,assertion)
   key=json.dumps(spec,sort_keys=True);groups.setdefault(key,{'spec':spec,'assertions':[]})['assertions'].append(assertion)
  for number,group in enumerate(groups.values(),1):
   spec=group['spec'];spec['queries']=[{'id':a['id'],'path':a['query_path']} for a in group['assertions'] if a.get('query_path')]
   dest=OUT/'assertion-groups'/f'g{number:02d}';run=execute(spec,dest)
   for assertion in group['assertions']:
    if 'expected_status' in assertion:passed=run.get('status')==assertion['expected_status']
    else:passed=run.get('status')=='CONSISTENT' and (dest/(assertion['id']+'.json')).exists() and equivalent(json.loads((dest/(assertion['id']+'.json')).read_text()),{'boolean':assertion['expected_boolean']})
    cleaning=run.get('cleaning_evaluation',run.get('validation',{}).get('cleaning_evaluation',{})).get('status')
    if assertion.get('expected_cleaning_status'):passed=passed and cleaning==assertion['expected_cleaning_status']
    context=run.get('context_evaluation',run.get('validation',{}).get('context_evaluation',{})).get('status')
    if assertion.get('expected_context_status'):passed=passed and context==assertion['expected_context_status']
    numeric=run.get('numeric_evaluation',run.get('validation',{}).get('numeric_evaluation',{})).get('status')
    owl=run.get('owl_validation_status',run.get('validation',{}).get('owl_validation_status'))
    if assertion.get('expected_numeric_status'):passed=passed and numeric==assertion['expected_numeric_status']
    if assertion.get('expected_owl_status'):passed=passed and owl==assertion['expected_owl_status']
    if assertion.get('expected_failure_kind'):passed=passed and run.get('failure_kind')==assertion['expected_failure_kind']
    report['assertions'].append({'id':assertion['id'],'pass':passed,'group':str(dest.relative_to(ROOT)),'execution_status':run.get('status'),'expected_status':assertion.get('expected_status'),'owl_status':owl,'expected_owl_status':assertion.get('expected_owl_status'),'failure_kind':run.get('failure_kind'),'expected_failure_kind':assertion.get('expected_failure_kind'),'numeric_status':numeric,'expected_numeric_status':assertion.get('expected_numeric_status'),'cleaning_status':cleaning,'expected_cleaning_status':assertion.get('expected_cleaning_status'),'context_status':context,'expected_context_status':assertion.get('expected_context_status')})
    if not passed:report['status']='FAIL'
   print(f'Fixture group{number}/{len(groups)}: {run.get("status")}',flush=True)
 for cfg in cat['configurations']:
  if not cfg.get('benchmark_only'):
   checks=[x for x in report['golden_checks'] if x['configuration']==cfg['id']]
   if len(checks)!=21:report['status']='FAIL';report.setdefault('errors',[]).append('Required21goldenchecks absent in '+cfg['id'])
 hashes_after=artifact_hashes();runtime_after=execution_runtime_hashes()
 report.update(input_hashes=hashes_before,runtime_hashes=runtime_before,input_hashes_after=hashes_after,runtime_hashes_after=runtime_after,
               source_hashes_unchanged=hashes_before==hashes_after,runtime_hashes_unchanged=runtime_before==runtime_after)
 if not report['source_hashes_unchanged'] or not report['runtime_hashes_unchanged']:
  report['status']='FAIL';report.setdefault('errors',[]).append('Sources or executed runtime binaries changed during functional validation')
 if len(report['assertions'])!=report.get('expected_assertion_count'):
  report['status']='FAIL';report.setdefault('errors',[]).append('Expected fixture assertions were not all evaluated')
 save(OUT/'functional.json',report)
 return report

def benchmark(cat):
 functional_report=json.loads((OUT/'functional.json').read_text())
 if functional_report['status']!='PASS':raise RuntimeError('Benchmark blocked: functional validation failed')
 if not functional_report.get('source_hashes_unchanged') or not functional_report.get('runtime_hashes_unchanged'):raise RuntimeError('Benchmark blocked: functional before/after source/runtime verification missing or failed')
 if functional_report.get('input_hashes')!=artifact_hashes():raise RuntimeError('Benchmark blocked: inputs/runtime changed since functional validation')
 if functional_report.get('runtime_hashes')!=execution_runtime_hashes():raise RuntimeError('Benchmark blocked: executed classes/dependency binaries changed since functional validation')
 configs=[x for x in cat['configurations'] if x.get('benchmark',False)]
 if len(configs)!=4:raise RuntimeError('Exactly four configurations required')
 save(OUT/'benchmark-environment.json',environment_data())
 hashes_before=artifact_hashes();save(OUT/'benchmark-input-hashes.json',hashes_before)
 runtime_before=execution_runtime_hashes();save(OUT/'benchmark-runtime-hashes.json',runtime_before)
 seed=20261003;order=[(i,c['id']) for i in range(30) for c in configs];random.Random(seed).shuffle(order);save(OUT/'replicate-order.json',{'seed':seed,'pairs':order,'new_process_each_run':True})
 runs=[]
 with (OUT/'runs.jsonl').open('x'):pass
 for n,(replica,id) in enumerate(order,1):
  cfg=next(c for c in configs if c['id']==id);dest=OUT/'runs'/f'{n:03d}-{id}-r{replica:02d}'
  result=execute(specification(cat,cfg,cfg.get('benchmark_abox')),dest);result.update(configuration=id,replica=replica,order=n)
  if result.get('status')!='CONSISTENT':raise RuntimeError('Replica failed '+str(dest))
  runs.append(result)
  with (OUT/'runs.jsonl').open('a') as stream:stream.write(json.dumps(result)+'\n')
  print(f'{n}/120 {id} {result["total_ms"]:.1f}ms',flush=True)
 hashes_after=artifact_hashes();save(OUT/'benchmark-input-hashes-after.json',hashes_after)
 runtime_after=execution_runtime_hashes();save(OUT/'benchmark-runtime-hashes-after.json',runtime_after)
 if hashes_before!=hashes_after:raise RuntimeError('Inputs/runtime changed during benchmark; results cannot be certified')
 if runtime_before!=runtime_after:raise RuntimeError('Executed runtime binaries changed during benchmark; results cannot be certified')
 summary=[]
 for cfg in configs:
  sample=[r for r in runs if r['configuration']==cfg['id']];row={'configuration':cfg['id'],'n':len(sample)}
  for key in ['total_ms','load_ms','reasoning_stage_ms','query_stage_ms','wall_process_ms','observed_peak_process_tree_rss_bytes','raw_input_triples','input_triples','materialized_triples','input_named_individuals','materialized_named_individuals','dl_ms','post_swrl_dl_ms','dl_materialized_triples','post_swrl_dl_materialized_triples','construct_added_triples']:
   vals=[r.get(key,0) for r in sample];row[key]={'mean':statistics.mean(vals),'sd':statistics.stdev(vals),'median':statistics.median(vals),'min':min(vals),'max':max(vals)}
  for key in ['swrl_ms','new_axiom_count','engine_inferred_axiom_count','imported_rule_count']:
   vals=[r.get('swrl',{}).get(key,0) for r in sample];row['worker_'+key]={'mean':statistics.mean(vals),'sd':statistics.stdev(vals),'median':statistics.median(vals),'min':min(vals),'max':max(vals)}
  summary.append(row)
 save(OUT/'summary.json',{'replicates':120,'dataset_seed':42,'order_seed':seed,'descriptive_statistics':summary,'memory_method':'Maximum observed sum of RSS for parent JVM and SWRL subprocess sampled every100ms; not instantaneous peak or live heap. Parent peak_heap_bytes is separate.','cache_control':'Each run starts a fresh parent JVM and, where enabled, a fresh SWRL worker. Application caches are not reused; operating-system disk caches, thermal state and CPU scheduling are not controlled.','inference_counts':'DL pre/post counts are named RDF consequences exported by HermiT; worker new_axiom_count combines OWL2RL and active SWRL consequences. Only the same-input OWL_ONLY ablation attributes additional consequences to SWRL. CONSTRUCT counts are recorded separately.','interpretation':'Observed computation on synthetic case; configurations perform different semantic tasks; no automatic industrial benefit or normalized percentage.'})
 with (OUT/'summary.csv').open('x',newline='') as stream:
  writer=csv.DictWriter(stream,fieldnames=['configuration','n','mean_ms','sd_ms','median_ms','mean_rss_bytes']);writer.writeheader()
  for x in summary:writer.writerow(dict(configuration=x['configuration'],n=x['n'],mean_ms=x['total_ms']['mean'],sd_ms=x['total_ms']['sd'],median_ms=x['total_ms']['median'],mean_rss_bytes=x['observed_peak_process_tree_rss_bytes']['mean']))

def artifact_hashes():
 hashes={}
 for directory in ['ontologies','research/model','research/data','research/runtime']:
  for p in (ROOT/directory).rglob('*'):
   if p.is_file() and 'target' not in p.parts and '__pycache__' not in p.parts:hashes[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
 for p in [ROOT/'research/catalog.json',ROOT/'research/reproduce.sh']+sorted((ROOT/'research/evaluation').glob('*.py')):
  hashes[str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
 return hashes

def execution_runtime_hashes():
 hashes={}
 for module in ['validator','swrl-worker']:
  directory=ROOT/'research/runtime'/module/'target'
  for file in sorted((directory/'classes').rglob('*.class')):hashes[str(file.relative_to(ROOT))]=hashlib.sha256(file.read_bytes()).hexdigest()
  classpath=directory/'classpath.txt'
  if not classpath.exists():raise RuntimeError('Required runtime classpath missing: '+module)
  for entry in classpath.read_text().strip().split(os.pathsep):
   jar=Path(entry)
   if not jar.is_file():raise RuntimeError('Required runtime dependency missing: '+jar.name)
   normalized=str(jar).split('/repository/',1)[-1]
   hashes['maven/'+normalized]=hashlib.sha256(jar.read_bytes()).hexdigest()
 return hashes

def environment_data():
 data={'timestamp_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'platform':platform.platform(),'machine':platform.machine(),'cpu_count':os.cpu_count(),'java_version':subprocess.check_output([str(JAVA),'-version'],stderr=subprocess.STDOUT,text=True),'python_version':platform.python_version(),'maven_version':subprocess.check_output([str(ROOT/'backend/mvnw'),'-v'],stderr=subprocess.STDOUT,text=True),'versions':{'OWLAPI-validator':'5.1.20','HermiT':'1.4.5.519','Jena':'4.10.0','SWRLAPI':'2.1.3','SWRLAPI-Drools-Engine':'2.1.3','Drools':'7.74.1.Final','OWLAPI-worker':'4.5.27'},'jvm_heap_limit':'2g parent +2g worker','headless':True}
 for key in ['hw.model','hw.memsize','machdep.cpu.brand_string']:
  try:data[key]=subprocess.check_output(['sysctl','-n',key],text=True,stderr=subprocess.DEVNULL).strip()
  except (subprocess.SubprocessError,FileNotFoundError):pass
 return data
def environment():
 save(OUT/'environment.json',environment_data())
 hashes=artifact_hashes()
 save(OUT/'artifacts.json',hashes)
 save(OUT/'runtime-binaries.json',execution_runtime_hashes())
 runtime=OUT/'runtime';runtime.mkdir()
 for module in ['swrl-worker','validator']:
  with (runtime/(module+'-dependencies.txt')).open('xb') as stream:stream.write((ROOT/'research/runtime'/module/'target/dependencies.txt').read_bytes())
 with (runtime/'smoke.json').open('xb') as stream:stream.write((ROOT/'research/runtime/swrl-worker/target/smoke.json').read_bytes())
 probe=runtime/'validation-failure-probe.json'
 subprocess.run([str(JAVA),'-Djava.awt.headless=true','-cp',CP,'org.protys.research.ValidationFailureProbe',str(probe)],check=True)
def main(argv=None):
 global OUT
 parser=argparse.ArgumentParser()
 parser.add_argument('--functional',action='store_true');parser.add_argument('--benchmark',action='store_true')
 parser.add_argument('--output',help='Evaluation directory, relative to repository; overrides PROTYS_EVALUATION_OUT')
 parser.add_argument('--select-output',action='store_true',help='Validate requested phases and print destination, without execution or writes')
 args=parser.parse_args(argv)
 if not (args.functional or args.benchmark):parser.error('Select --functional and/or --benchmark')
 OUT=evaluation_dir(args.output,fresh=True)
 phases=[name for name in ['functional','benchmark'] if getattr(args,name)]
 check_phases(OUT,phases)
 if args.select_output:
  print(OUT);raise SystemExit(0)
 print('Evaluation output: '+str(OUT),flush=True)
 with reserve_phases(OUT,phases):
  configure_runtime();cat=json.loads((ROOT/'research/catalog.json').read_text())
  if args.functional:
   environment();report=functional(cat)
   if report['status']!='PASS':raise SystemExit(1)
  if args.benchmark:benchmark(cat)

if __name__=='__main__':main()
