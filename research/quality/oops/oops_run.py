#!/usr/bin/env python3
"""Evaluación de PROTYS(KB) con OOPS! (servicio REST). Reproducible:
   python3 scripts/oops_run.py <ruta_repo> <dir_salida>
Toma los OWL de la etiqueta de cierre con `git show`, cuenta lo cargado, envía cada conjunto a OOPS! y guarda respuesta cruda, CSV y registro."""
import sys,subprocess,datetime,hashlib,urllib.request,csv,os,platform
import rdflib
from rdflib import RDF,RDFS,OWL,URIRef,Graph,Namespace
REPO,OUT=sys.argv[1],sys.argv[2]; os.makedirs(OUT,exist_ok=True)
TAG='tesis-cierre-2026-10-03-v1'; URL='https://oops.linkeddata.es/rest'
OOPS=Namespace('http://oops.linkeddata.es/def#')
SETS=[('red_base','Red base (niveles Principal y de Refinamiento)',['core-concepts.owl','enterprise-module.owl','process-module.owl','product-module.owl','resource-module.owl']),
      ('iso15531','Módulo ISO 15531',['iso15531-module.owl']),
      ('iso14040','Módulo ISO 14040',['iso14040-module.owl']),
      ('piloto_ap242','Módulo piloto ISO 10303-242',['iso10303-ap242-fragment.owl']),
      ('alineamiento','Módulo de Alineamiento (declaraciones, sin reglas SWRL)',['alignment-rules.owl']),
      ('ocho_modulos','Ocho módulos base (Principal, Refinamiento, Estándares y Alineamiento)',['core-concepts.owl','enterprise-module.owl','process-module.owl','product-module.owl','resource-module.owl','iso15531-module.owl','iso14040-module.owl','alignment-rules.owl']),
      ('red_con_piloto','Ocho módulos base más el módulo piloto y sus reglas de vínculo',['core-concepts.owl','enterprise-module.owl','process-module.owl','product-module.owl','resource-module.owl','iso15531-module.owl','iso14040-module.owl','alignment-rules.owl','iso10303-ap242-fragment.owl','iso10303-alignment-rules.owl'])]
SWRL='http://www.w3.org/2003/11/swrl'
def strip_swrl(g):
    """Quita sólo los nodos de reglas SWRL (átomos, variables, listas de argumentos). No toca las listas de OWL (unionOf, etc.)."""
    nodes=set()
    for s_,p_,o_ in g:
        if str(o_).startswith(SWRL) and p_==RDF.type: nodes.add(s_)
        if str(p_).startswith(SWRL):
            nodes.add(s_)
            if isinstance(o_,rdflib.BNode): nodes.add(o_)
    todo=list(nodes)
    while todo:
        x=todo.pop()
        for p_,o_ in g.predicate_objects(x):
            if isinstance(o_,rdflib.BNode) and o_ not in nodes: nodes.add(o_); todo.append(o_)
    k=0
    for x in nodes:
        for t in list(g.triples((x,None,None))): g.remove(t); k+=1
        for t in list(g.triples((None,None,x))): g.remove(t); k+=1
    return k
log=[]
def P(*a):
    s=' '.join(str(x) for x in a); print(s); log.append(s)
def src(f):
    for ref in (TAG,'HEAD'):
        r=subprocess.run(['git','-C',REPO,'show',f'{ref}:ontologies/{f}'],capture_output=True)
        if r.returncode==0: return ref,r.stdout
    raise SystemExit('no encontrado '+f)
def n(g,t): return len({s for s in g.subjects(RDF.type,t) if isinstance(s,URIRef)})
P('EVALUACIÓN CON OOPS! (OntOlogy Pitfall Scanner!)'); P('Fecha (UTC):',datetime.datetime.utcnow().isoformat(timespec='seconds'))
P('Servicio:',URL); P('Repositorio: protys-ws-repo, etiqueta',TAG,'=',subprocess.run(['git','-C',REPO,'rev-parse',TAG+'^{commit}'],capture_output=True,text=True).stdout.strip())
P('Python',platform.python_version(),'| rdflib',rdflib.__version__); P('')
rows=[]
for key,name,files in SETS:
    g=Graph(); P('='*78); P(name)
    for f in files:
        ref,data=src(f); h=Graph(); h.parse(data=data,format='xml')
        P(f'  {f:32s} origen={ref:28s} sha256={hashlib.sha256(data).hexdigest()[:16]} tripletas={len(h)}')
        for t in h: g.add(t)
    k=strip_swrl(g)
    if k: P(f'  Se apartaron {k} tripletas de reglas SWRL (OOPS! no evalúa reglas; las reglas se comprueban con las pruebas de la Sección 4.5)')
    imp=list(g.triples((None,OWL.imports,None)))
    for t in imp: g.remove(t)
    ax=sum(len(list(g.triples((None,p,None)))) for p in (RDFS.subClassOf,OWL.equivalentClass,OWL.disjointWith,RDFS.domain,RDFS.range,RDFS.subPropertyOf,OWL.inverseOf))
    restr=len(list(g.subjects(RDF.type,OWL.Restriction)))
    P(f'  CARGADO: tripletas={len(g)} (se quitaron {len(imp)} owl:imports, ya resueltos al unir los archivos)')
    P(f'  clases={n(g,OWL.Class)} propiedades de objeto={n(g,OWL.ObjectProperty)} propiedades de datos={n(g,OWL.DatatypeProperty)} individuos={n(g,OWL.NamedIndividual)}')
    P(f'  subClassOf={len(list(g.triples((None,RDFS.subClassOf,None))))} dominios={len(list(g.triples((None,RDFS.domain,None))))} rangos={len(list(g.triples((None,RDFS.range,None))))} disjuntas={len(list(g.triples((None,OWL.disjointWith,None))))} restricciones={restr} | axiomas estructurales={ax}')
    body=g.serialize(format='xml')
    req=('<?xml version="1.0" encoding="UTF-8"?><OOPSRequest><OntologyUrl></OntologyUrl><OntologyContent><![CDATA['+body+']]></OntologyContent><Pitfalls></Pitfalls><OutputFormat>RDF/XML</OutputFormat></OOPSRequest>').encode('utf-8')
    resp=None
    for intento in (1,2,3):
        try:
            resp=urllib.request.urlopen(urllib.request.Request(URL,data=req,headers={'Content-Type':'application/xml'}),timeout=150).read()
        except Exception as e:
            P(f'  intento {intento}: error de red {e}'); resp=None; continue
        if b'hasCode' in resp or b'unexpected' not in resp.lower(): break
        P(f'  intento {intento}: el servicio devolvió error, se reintenta')
    if resp is None: continue
    open(f'{OUT}/respuesta_oops_{key}.rdf','wb').write(resp)
    P(f'  ENVIADO: {len(req)} bytes | RESPUESTA: {len(resp)} bytes -> respuesta_oops_{key}.rdf')
    r=Graph()
    try: r.parse(data=resp,format='xml')
    except Exception as e: P('  respuesta no RDF:',resp[:200]); continue
    found=[]
    for pf in set(r.subjects(OOPS.hasCode,None)):
        code=str(r.value(pf,OOPS.hasCode)); imp_=str(r.value(pf,OOPS.hasImportanceLevel)); nm=str(r.value(pf,OOPS.hasName))
        cnt=r.value(pf,OOPS.hasNumberAffectedElements); els=sorted(str(e).split('#')[-1].split('/')[-1] for e in r.objects(pf,OOPS.hasAffectedElement))
        found.append((code,imp_,nm,int(cnt) if cnt is not None else len(els),els))
    found.sort()
    crit=[f for f in found if f[1]=='Critical']
    P(f'  RESULTADO: {len(found)} tipos de problema | críticos={len(crit)} importantes={sum(f[1]=="Important" for f in found)} menores={sum(f[1]=="Minor" for f in found)}')
    for code,imp_,nm,cnt,els in found:
        P(f'    {code} [{imp_:9s}] {nm} — {cnt} caso(s)'+(': '+', '.join(els) if els else ''))
        rows.append([name,code,imp_,nm,cnt,'; '.join(els)])
    P('')
with open(f'{OUT}/OOPS_resumen.csv','w',newline='') as f:
    w=csv.writer(f); w.writerow(['conjunto','codigo','importancia','nombre','casos','elementos']); w.writerows(rows)
open(f'{OUT}/EVIDENCIA_OOPS.log','w').write('\n'.join(log)+'\n')
