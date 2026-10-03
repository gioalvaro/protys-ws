#!/usr/bin/env python3
"""Exercise actual PROTYS-WS REST against owned, isolated PostgreSQL and Fuseki."""
import hashlib,json,os,shutil,signal,socket,subprocess,tempfile,time,urllib.request,urllib.error,uuid
from pathlib import Path
from run_evaluation import ROOT,JAVA,CP,equivalent,save,artifact_hashes,execution_runtime_hashes
OUT=Path(os.environ.get('PROTYS_DEMONSTRATION_OUTPUT',str(ROOT/'research/evaluation/current/demonstration')))
if OUT.exists() and any(OUT.iterdir()):raise RuntimeError('Demonstration requires a fresh output directory; preserve previous evidence first')
OUT.mkdir(parents=True,exist_ok=True)
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def record_http_json(base,path,method,status,value,request_details):
 number=len(report.setdefault('http_exchanges',[]))+1
 file=OUT/'http-responses'/f'{number:03d}.json';save(file,value)
 report['http_exchanges'].append({'sequence':number,'base_url':base,'path':path,'method':method,'observed_http_status':status,'request':request_details,'response_file':str(file.relative_to(OUT)),'response_sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'encoding':'Decoded HTTP JSON; transport metadata stored separately from the response payload.'})
 return value
def request(base,path,body=None,method=None,allow_failure=False,expected_http_status=None):
 payload=json.dumps(body).encode() if body is not None else None
 verb=method or ('POST' if body is not None else 'GET')
 req=urllib.request.Request(base+path,data=payload,method=verb,headers={'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=150) as result:
   value=json.load(result)
   record_http_json(base,path,verb,result.status,value,body)
   if expected_http_status is not None and result.status!=expected_http_status:raise RuntimeError('Unexpected HTTP status '+str(result.status)+' for '+path)
   if expected_http_status is not None:value['observed_http_status']=result.status
   return value
 except urllib.error.HTTPError as error:
  value=json.load(error)
  record_http_json(base,path,verb,error.code,value,body)
  if allow_failure:
   if expected_http_status is not None and error.code!=expected_http_status:raise RuntimeError('Unexpected HTTP error '+str(error.code)+' for '+path) from error
   if expected_http_status is not None:value['observed_http_status']=error.code
   return value
  raise
def upload(base,path,file,name=None,name_field="name"):
 boundary='protys-'+uuid.uuid4().hex;parts=[]
 if name:parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name_field}"\r\n\r\n{name}\r\n'.encode())
 parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file.name}"\r\nContent-Type: application/rdf+xml\r\n\r\n'.encode()+file.read_bytes()+b'\r\n');parts.append(f'--{boundary}--\r\n'.encode())
 req=urllib.request.Request(base+path,data=b''.join(parts),headers={'Content-Type':'multipart/form-data; boundary='+boundary})
 with urllib.request.urlopen(req,timeout=150) as result:
  value=json.load(result)
  return record_http_json(base,path,'POST',result.status,value,{'kind':'multipart upload','filename':file.name,'file_sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'name':name,'name_field':name_field})
def wait(predicate,seconds=90):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  try:
   value=predicate()
   if value:return value
  except (OSError,ValueError,subprocess.SubprocessError):pass
  time.sleep(.3)
 raise RuntimeError('Owned service readiness timed out')
owned='protys-academic-'+uuid.uuid4().hex[:10];fuseki=None;backend=None;store=Path(tempfile.mkdtemp(prefix='protys-fuseki-owned-'));report={'provisional':os.environ.get('PROTYS_PROVISIONAL')=='1','status':'FAIL','scope':'Controlled REST demonstration on synthetic data. No industrial ERP or production plant validated.','checks':[]}
report['owned_resources']={'container_name':owned,'private_tdb2_path':str(store)}
sources=artifact_hashes();runtime=execution_runtime_hashes();backend_files=[ROOT/'backend/pom.xml',ROOT/'backend/target/protys-ws-1.0.0-SNAPSHOT.jar']+[path for path in (ROOT/'backend/src').rglob('*') if path.is_file()]
backend_hashes={str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest() for path in backend_files}
report.update(source_hashes=sources,runtime_binary_hashes=runtime,backend_artifact_hashes=backend_hashes)
try:
 subprocess.run(['docker','run','-d','--name',owned,'-e','POSTGRES_DB=academic','-e','POSTGRES_USER=academic','-e','POSTGRES_PASSWORD=academic-local-only','-p','127.0.0.1::5432','postgres@sha256:0dda651c259bfe50e2bcc28ca23d1fcca772fa90b0210803aa7b97379ccf4e85'],check=True,stdout=subprocess.DEVNULL)
 inspection=json.loads(subprocess.check_output(['docker','inspect',owned]))[0];pg=inspection['NetworkSettings']['Ports']['5432/tcp'][0]['HostPort'];report['owned_resources']['container_id']=inspection['Id']
 wait(lambda:subprocess.run(['docker','exec',owned,'pg_isready','-U','academic','-d','academic'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0)
 (OUT/'fuseki.port').unlink(missing_ok=True)
 fuseki=subprocess.Popen([str(JAVA),'-Xmx2g','-cp',CP,'org.protys.research.IsolatedFuseki',str(OUT/'fuseki.port'),str(store)],stdout=(OUT/'fuseki.log').open('w'),stderr=subprocess.STDOUT,start_new_session=True)
 report['owned_resources'].update(fuseki_pid=fuseki.pid,fuseki_process_group=fuseki.pid)
 fp=wait(lambda:int((OUT/'fuseki.port').read_text()) if (OUT/'fuseki.port').exists() else None);fuseki_url=f'http://127.0.0.1:{fp}/academic';bp=port();base=f'http://127.0.0.1:{bp}'
 env=os.environ.copy();env.update(SPRING_DATASOURCE_URL=f'jdbc:postgresql://127.0.0.1:{pg}/academic',SPRING_DATASOURCE_USERNAME='academic',SPRING_DATASOURCE_PASSWORD='academic-local-only',FUSEKI_ENDPOINT=fuseki_url,FUSEKI_QUERY_ENDPOINT=fuseki_url+'/query',FUSEKI_UPDATE_ENDPOINT=fuseki_url+'/update',FUSEKI_GSP_ENDPOINT=fuseki_url+'/data',PROTYS_RESEARCH_ROOT=str(ROOT))
 backend=subprocess.Popen([str(JAVA),'-Xmx2g','-Djava.awt.headless=true','-jar',str(ROOT/'backend/target/protys-ws-1.0.0-SNAPSHOT.jar'),f'--server.port={bp}','--server.address=127.0.0.1','--server.servlet.context-path=/','--spring.jpa.hibernate.ddl-auto=create-drop','--spring.datasource.hikari.maximum-pool-size=3','--spring.datasource.hikari.minimum-idle=1','--logging.level.root=WARN','--logging.level.org.protys.ws=INFO','--logging.level.org.springframework.web=WARN','--logging.level.org.springframework.security=WARN','--logging.level.org.hibernate.SQL=WARN',f'--logging.file.name={OUT}/backend-file.log'],cwd=ROOT/'backend',env=env,stdout=(OUT/'backend.log').open('w'),stderr=subprocess.STDOUT,start_new_session=True)
 report['owned_resources'].update(backend_pid=backend.pid,backend_process_group=backend.pid)
 def health_ready():
  value=request(base,'/actuator/health');return value if value.get('status')=='UP' else None
 save(OUT/'health.json',wait(health_ready))
 report['services']={'backend':base,'fuseki':fuseki_url,'postgres_port':int(pg),'java':subprocess.check_output([str(JAVA),'-version'],stderr=subprocess.STDOUT,text=True),'postgres_version':subprocess.check_output(['docker','exec',owned,'postgres','--version'],text=True).strip(),'fuseki_version':'4.10.0','union_default_graph':True,'private_tdb2':True}
 case=Path(os.environ.get('PROTYS_DEMONSTRATION_CASE',str(ROOT/'research/evaluation/current/functional/integrated/raw-input.owl')))
 if not case.exists():raise RuntimeError('Raw canonical functional input absent; run current functional validation first')
 report['raw_input']={'path':str(case.relative_to(ROOT)),'sha256':hashlib.sha256(case.read_bytes()).hexdigest(),'precomputed_inferences':False}
 module=upload(base,'/api/ontology/modules/upload',case,'AcademicCase');save(OUT/'upload.json',module)
 validation=request(base,f'/api/ontology/modules/{module["id"]}/validate',{});save(OUT/'validation.json',validation);report['checks'].append({'id':'REST_VALIDATION','pass':validation['status']=='CONSISTENT'})
 rules=upload(base,'/api/alignment/rules/upload',ROOT/'ontologies/alignment-rules.owl');save(OUT/'rules.json',rules);report['checks'].append({'id':'IMPORT22','pass':len(rules)==22})
 first=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'inference-on.json',first);report['checks'].append({'id':'REAL_ENGINE22','pass':first['validationStatus']=='CONSISTENT' and first['activeRules']==22 and not first['cacheHit']})
 report['checks'].append({'id':'CONTEXT_LINK_STATUS','pass':first.get('contextEvaluation',{}).get('status')=='CONTEXT_VALID'})
 report['checks'].append({'id':'CLEANING_RECORD_STATUS','pass':first.get('cleaningEvaluation',{}).get('status')=='MISSING_CLEANING_RECORD'})
 report['checks'].append({'id':'NUMERIC_RECORD_STATUS','pass':first.get('numericEvaluation',{}).get('status')=='VALID' and first.get('owlValidationStatus')=='CONSISTENT'})
 repeat=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'inference-cached.json',repeat);report['checks'].append({'id':'CACHE_TIME_NOT_COUNTED','pass':repeat['cacheHit'] and repeat['reasoningTimeMs']==0})
 cat=json.loads((ROOT/'research/catalog.json').read_text())
 for q in cat['queries']:
  result=request(base,'/api/sparql/execute',{'query':(ROOT/q['path']).read_text()});save(OUT/(q['id']+'.json'),result)
  expected=json.loads((ROOT/q['expected_path']).read_text());report['checks'].append({'id':'REST_'+q['id'],'pass':equivalent(result.get('sparqlJson',{}),expected)})
 r03=next(x for x in rules if x['name'].startswith('R03'))
 ask='PREFIX a: <http://w3id.org/protys/ontology/alignment#> ASK { ?lot a:hasRecordedInputFlowForLot ?flow }'
 positive=request(base,'/api/sparql/execute',{'query':ask});save(OUT/'R03-active-ask.json',positive);report['checks'].append({'id':'R03_ACTIVE','pass':positive.get('askResult') is True,'evidence_files':['R03-active-ask.json','inference-on.json']})
 toggled_off=request(base,f'/api/alignment/rules/{r03["id"]}/toggle?active=false',method='PUT');save(OUT/'R03-toggle-off.json',toggled_off);off=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'inference-off.json',off)
 negative=request(base,'/api/sparql/execute',{'query':ask});save(OUT/'R03-disabled-ask.json',negative);report['checks'].append({'id':'R03_DISABLED','pass':off['activeRules']==21 and not off['cacheHit'] and negative.get('askResult') is False,'evidence_files':['R03-toggle-off.json','inference-off.json','R03-disabled-ask.json']})
 toggled_on=request(base,f'/api/alignment/rules/{r03["id"]}/toggle?active=true',method='PUT');save(OUT/'R03-toggle-on.json',toggled_on);restored=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'inference-restored.json',restored)
 again=request(base,'/api/sparql/execute',{'query':ask});save(OUT/'R03-restored-ask.json',again);report['checks'].append({'id':'R03_RESTORED','pass':restored['activeRules']==22 and again.get('askResult') is True,'evidence_files':['R03-toggle-on.json','inference-restored.json','R03-restored-ask.json']})
 templates=request(base,'/api/sparql/templates');save(OUT/'templates.json',templates);report['checks'].append({'id':'21_CANONICAL_TEMPLATES','pass':len(templates)==21})
 incomplete=OUT/'cleaning-context-incomplete.owl';incomplete.write_text('<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:i="http://w3id.org/protys/ontology/iso15531#"><owl:NamedIndividual rdf:about="https://example.org/ContextLotA"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#Lot"/><i:requiresCleaningBetween rdf:resource="https://example.org/ContextLotB"/></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/ContextLotB"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#Lot"/></owl:NamedIndividual></rdf:RDF>')
 save(OUT/'cleaning-context-upload.json',upload(base,'/api/ontology/modules/upload',incomplete,'CleaningContextIncomplete'))
 context_result=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'cleaning-not-evaluable.json',context_result)
 report['checks'].append({'id':'C26_NO_FALSE_CLEANING_APPROVAL','pass':context_result['validationStatus']=='CONSISTENT' and context_result.get('cleaningEvaluation',{}).get('status')=='NOT_EVALUABLE'})
 foreign=OUT/'foreign-context.owl';foreign.write_text('<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:i="http://w3id.org/protys/ontology/iso15531#" xmlns:t="http://w3id.org/protys/ontology/research#" xmlns:e="http://w3id.org/protys/ontology/iso14040#"><owl:NamedIndividual rdf:about="https://example.org/ForeignContextLot"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#Lot"/><i:hasProcess rdf:resource="https://example.org/LotProcess"/><i:hasActivity rdf:resource="https://example.org/ForeignOperation"/><t:hasGHGContribution rdf:resource="https://example.org/ForeignOperation"/></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/ForeignOperation"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#ProcessOperation"/><i:partOfProcess rdf:resource="https://example.org/OtherProcess"/><i:consumesEnergyKWh rdf:datatype="http://www.w3.org/2001/XMLSchema#decimal">999</i:consumesEnergyKWh><e:hasGHGImpactKgCO2e rdf:datatype="http://www.w3.org/2001/XMLSchema#decimal">999</e:hasGHGImpactKgCO2e></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/LotProcess"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#ManufacturingProcess"/></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/OtherProcess"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#ManufacturingProcess"/></owl:NamedIndividual></rdf:RDF>')
 save(OUT/'foreign-context-upload.json',upload(base,'/api/ontology/modules/upload',foreign,'ForeignContext'))
 foreign_result=request(base,'/api/alignment/reasoning/execute',{});save(OUT/'foreign-context.json',foreign_result)
 report['checks'].append({'id':'CCTX_VISIBLE_WITH_OWL_CONSISTENCY','pass':foreign_result['validationStatus']=='CONSISTENT' and foreign_result.get('contextEvaluation',{}).get('status')=='CONTEXT_INTEGRITY_ERROR'})
 q03=next(q for q in cat['queries'] if q['id']=='Q03');guarded=request(base,'/api/sparql/execute',{'query':(ROOT/q03['path']).read_text()});save(OUT/'foreign-context-Q03.json',guarded)
 report['checks'].append({'id':'CCTX_FOREIGN_OPERATION_EXCLUDED','pass':equivalent(guarded.get('sparqlJson',{}),json.loads((ROOT/q03['expected_path']).read_text()))})
 ambiguous=OUT/'numeric-ambiguous.owl';ambiguous.write_text('<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:i="http://w3id.org/protys/ontology/iso15531#"><owl:NamedIndividual rdf:about="https://example.org/NumericLot"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#Lot"/><i:hasProcess rdf:resource="https://example.org/NumericProcess"/><i:hasActivity rdf:resource="https://example.org/NumericOperation"/></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/NumericOperation"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#ProcessOperation"/><i:partOfProcess rdf:resource="https://example.org/NumericProcess"/><i:consumesEnergyKWh rdf:datatype="http://www.w3.org/2001/XMLSchema#decimal">10</i:consumesEnergyKWh><i:consumesEnergyKWh rdf:datatype="http://www.w3.org/2001/XMLSchema#decimal">20</i:consumesEnergyKWh></owl:NamedIndividual><owl:NamedIndividual rdf:about="https://example.org/NumericProcess"><rdf:type rdf:resource="http://w3id.org/protys/ontology/iso15531#ManufacturingProcess"/></owl:NamedIndividual></rdf:RDF>')
 save(OUT/'numeric-ambiguity-upload.json',upload(base,'/api/ontology/modules/upload',ambiguous,'NumericAmbiguous'))
 numerical_failure=request(base,'/api/alignment/reasoning/execute',{},allow_failure=True,expected_http_status=422);save(OUT/'numeric-ambiguity-blocked.json',numerical_failure)
 report['checks'].append({'id':'CNUM_REJECTS_AMBIGUITY','pass':numerical_failure.get('observed_http_status')==422 and numerical_failure.get('validationStatus')=='NOT_EVALUATED' and numerical_failure.get('owlValidationStatus')=='CONSISTENT' and numerical_failure.get('numericEvaluation',{}).get('status')=='AMBIGUOUS_INPUT' and numerical_failure.get('status')=='FAILED'})
 no_stale=request(base,'/api/sparql/execute',{'query':ask});save(OUT/'numeric-ambiguity-no-stale-inferences.json',no_stale)
 report['checks'].append({'id':'CNUM_INVALIDATES_OLD_INFERENCES','pass':no_stale.get('askResult') is False})
 numerical_wizard=upload(base,'/api/wizard/step1/upload',ambiguous,'NumericAmbiguity','standardName');save(OUT/'wizard-numeric-upload.json',numerical_wizard)
 numerical_validation=request(base,f'/api/wizard/step2/validate/{numerical_wizard["tempModuleId"]}',{});save(OUT/'wizard-numeric-blocked.json',numerical_validation)
 report['checks'].append({'id':'WIZARD_REJECTS_NUMERIC_AMBIGUITY','pass':numerical_validation.get('status')=='NOT_EVALUATED' and numerical_validation.get('owlValidationStatus')=='CONSISTENT' and numerical_validation.get('numericEvaluation',{}).get('status')=='AMBIGUOUS_INPUT' and numerical_validation.get('step2_consistent') is not True})
 numerical_complete=request(base,f'/api/wizard/complete/{numerical_wizard["tempModuleId"]}',{},allow_failure=True,expected_http_status=422);save(OUT/'wizard-numeric-completion-blocked.json',numerical_complete)
 report['checks'].append({'id':'WIZARD_BLOCKS_NUMERIC_COMPLETION','pass':numerical_complete.get('status')=='NOT_EVALUATED' and numerical_complete.get('finalModuleId') is None})
 bad=OUT/'contradiction.owl';bad.write_text('<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#"><owl:Class rdf:about="https://example.org/ContradictoryA"><owl:disjointWith rdf:resource="https://example.org/ContradictoryB"/></owl:Class><owl:Class rdf:about="https://example.org/ContradictoryB"/><owl:NamedIndividual rdf:about="https://example.org/contradictoryX"><rdf:type rdf:resource="https://example.org/ContradictoryA"/><rdf:type rdf:resource="https://example.org/ContradictoryB"/></owl:NamedIndividual></rdf:RDF>')
 wizard=upload(base,'/api/wizard/step1/upload',bad,'Contradiction','standardName');save(OUT/'wizard-upload.json',wizard)
 failed=request(base,f'/api/wizard/step2/validate/{wizard["tempModuleId"]}',{});save(OUT/'wizard-inconsistent.json',failed);report['checks'].append({'id':'WIZARD_REJECTS_INCONSISTENCY','pass':failed['status']=='INCONSISTENT' and failed['step2_consistent'] is False})
 blocked=request(base,f'/api/wizard/complete/{wizard["tempModuleId"]}',{},allow_failure=True,expected_http_status=422);save(OUT/'wizard-completion-blocked.json',blocked);report['checks'].append({'id':'WIZARD_BLOCKS_PREMATURE_COMPLETION','pass':blocked['status']=='NOT_EVALUATED' and blocked.get('finalModuleId') is None})
 report['provenance_unchanged']=sources==artifact_hashes() and runtime==execution_runtime_hashes() and backend_hashes=={str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest() for path in backend_files} and report['raw_input']['sha256']==hashlib.sha256(case.read_bytes()).hexdigest()
 report['status']='PASS' if all(x['pass'] for x in report['checks']) and report.get('provenance_unchanged') else 'FAIL'
except Exception as e:report['error']=str(e)
finally:
 for process in [backend,fuseki]:
  if process:
   try:os.killpg(process.pid,signal.SIGTERM)
   except ProcessLookupError:pass
   try:process.wait(timeout=20)
   except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
 removal=subprocess.run(['docker','rm','-f',owned],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 inspection=subprocess.run(['docker','ps','-a','--filter','name=^/'+owned+'$','--format','{{.Names}}'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 shutil.rmtree(store,ignore_errors=True)
 groups={process.pid for process in [backend,fuseki] if process};remaining=[]
 for line in subprocess.check_output(['ps','-axo','pid,pgid,state'],text=True).splitlines()[1:]:
  fields=line.split()
  if len(fields)==3 and int(fields[1]) in groups and not fields[2].startswith('Z'):remaining.append(int(fields[0]))
 report['cleanup_checks']={'backend_exited':backend is None or backend.poll() is not None,'fuseki_exited':fuseki is None or fuseki.poll() is not None,'remaining_owned_group_pids':remaining,'container_absence_confirmed':inspection.returncode==0 and not inspection.stdout.strip(),'docker_removal_exit_code':removal.returncode,'private_tdb2_absent':not store.exists()}
 cleanup_ok=report['cleanup_checks']['backend_exited'] and report['cleanup_checks']['fuseki_exited'] and not remaining and report['cleanup_checks']['container_absence_confirmed'] and report['cleanup_checks']['private_tdb2_absent']
 if not cleanup_ok:report['status']='FAIL';report['cleanup_error']='Owned resource cleanup could not be confirmed'
 report['cleanup']='Owned processes/container/private TDB2 removal checked; unrelated services untouched.';save(OUT.parent/'demonstration.json',report)
print(json.dumps({key:report.get(key) for key in ['status','provisional','provenance_unchanged','services','checks','error','cleanup']},indent=2));raise SystemExit(0 if report['status']=='PASS' else 1)
