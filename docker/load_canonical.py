#!/usr/bin/env python3
"""Load a reviewed catalog into a fresh dataset; never overwrite existing graphs."""
import hashlib,json,os,time,urllib.request,urllib.parse,urllib.error
from pathlib import Path
ROOT=Path(os.environ.get('PROTYS_REPOSITORY','/workspace'))
BASE=os.environ.get('FUSEKI_URL','http://localhost:3030').rstrip('/')
DATASET=os.environ.get('FUSEKI_DATASET','protys_canonical')
def call(url,data=None,content=None,method=None):
 headers={'Content-Type':content} if content else {}
 user=os.environ.get('FUSEKI_ADMIN_USER');password=os.environ.get('FUSEKI_ADMIN_PASSWORD')
 if user and password:
  import base64
  headers['Authorization']='Basic '+base64.b64encode((user+':'+password).encode()).decode()
 with urllib.request.urlopen(urllib.request.Request(url,data=data,headers=headers,method=method),timeout=30) as response:return response.read()
def fresh(dataset):
 url=BASE+'/'+dataset+'/query?'+urllib.parse.urlencode({'query':'SELECT (COUNT(*) AS ?n) WHERE { { ?s ?p ?o } UNION { GRAPH ?g { ?s ?p ?o } } }','format':'application/sparql-results+json'})
 try:result=json.loads(call(url))
 except urllib.error.HTTPError as error:
  if error.code!=404:raise
  call(BASE+'/$/datasets',urllib.parse.urlencode({'dbName':dataset,'dbType':'tdb2'}).encode(),'application/x-www-form-urlencoded','POST');result=json.loads(call(url))
 if int(result['results']['bindings'][0]['n']['value'])!=0:raise RuntimeError('Dataset '+dataset+' already contains data; choose a new dataset. No graph was overwritten.')
def load(dataset,files):
 for relative in dict.fromkeys(files):
  file=ROOT/relative
  if not file.is_file():raise FileNotFoundError('Required canonical file absent: '+relative)
  graph='urn:protys:canonical:'+relative.replace('/','_')
  query=urllib.parse.urlencode({'graph':graph})
  call(BASE+'/'+dataset+'/data?'+query,file.read_bytes(),'text/turtle' if file.suffix=='.ttl' else 'application/rdf+xml','PUT')
  print(json.dumps({'dataset':dataset,'graph':graph,'source':relative,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}),flush=True)
cat=json.loads((ROOT/'research/catalog.json').read_text());cfg=next(c for c in cat['configurations'] if c['id']=='integrated')
files=cfg['tbox_paths']+cfg['abox_paths']+cfg['rules_paths']
# Preflight every required file before any write.
for source in files:
 if not (ROOT/source).is_file():raise FileNotFoundError(source)
for attempt in range(60):
 try:call(BASE+'/$/ping');break
 except OSError:
  if attempt==59:raise
  time.sleep(1)
fresh(DATASET);load(DATASET,files)
if os.environ.get('PROTYS_INCLUDE_HISTORICAL')=='1':
 history=DATASET+'_historical';historical=['ontologies/paint-case-study-instances.ttl','ontologies/paint-instances-adempiere.ttl','ontologies/paint-instances-odoo.ttl'];fresh(history);load(history,historical)
print('Canonical assertions loaded. OWL/SWRL evaluation is a separate verified local runtime operation; no inference or industrial validation claimed by this loader.')
