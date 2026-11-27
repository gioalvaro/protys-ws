"""Static census, scientific contracts and independent fixture arithmetic."""
import hashlib,json
from decimal import Decimal
from pathlib import Path
from lxml import etree as E
from build_rules import RULES, NS
from build_catalog import VALIDATIONS, PREFIX

ROOT=Path(__file__).resolve().parents[2]
BASE=['core-concepts.owl','product-module.owl','process-module.owl','resource-module.owl','enterprise-module.owl','iso15531-module.owl','iso14040-module.owl','alignment-rules.owl']
EXT=['paint-case-extension.owl','iso10303-ap242-fragment.owl','iso10303-alignment-rules.owl']
KINDS=['Class','ObjectProperty','DatatypeProperty']
RANGES={'R01':'kg CO2e =kWh×kg CO2e/kWh; entrada numérica única y factor seleccionado único, preparados por CNUM','R02':'Presencia de flujo de agua; sin suma cuantitativa','R03':'IRI del flujo y del lote','R04':'Indicador y ThresholdRecord explícito con la misma unidad y categoría; valor, unidad y categoría del mismo registro; frontera estricta >','R05':'IRI del indicador del proceso; asociación sin suma','R06':'IRI del factor del material','R08':'Cantidad en unidad funcional declarada; no conversión automática','R09':'IRI de recurso/operación/plan','R10':'Estado textual del mismo lote','R11':'%/min; razón de rendimiento másico sobre duración','R12':'Capacidad de molienda explícita; no umbral numérico','R13':'Segundos desde origen común, mismo lote/proceso','R14':'IRI de operador responsable','R15':'Indisponibilidad temporal sólo con mantenimiento ACTIVE de la instantánea; no registro histórico/programado','R17':'Código entero de categoría, mismo equipo y par consecutivo','R18':'Clase candidato a reproceso; sin ejecución de operación nueva','R20':'IRI de flujo/material/operación del mismo proceso','R21':'Contribución identificada por IRI de operación, evitando colapso de literales iguales','R22':'Clase hotspot; cuello de botella declarado como entrada','R23':'min/unidad del indicador; razón por pareja, sin normalización','R24':'Clase brecha de cobertura desde core:MissingEnvironmentalData','R25':'Clase requiere recálculo; sin recalcular el valor'}

def main():
 files=[ROOT/'ontologies'/p for p in BASE+EXT]+sorted((ROOT/'research/model').glob('*profile.owl'))
 seen={k:set() for k in KINDS};components=[];base_union={k:set() for k in KINDS}
 for p in files:
  tree=E.parse(str(p));item={'path':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'scope':'base eight modules' if p.name in BASE else 'case/research/AP242 extension','declared_iris':{},'counts':{}}
  for kind in KINDS:
   values=set(tree.xpath('//owl:'+kind+'/@rdf:about',namespaces=NS));item['declared_iris'][kind]=sorted(values);item['counts'][kind]={'local_declared_unique':len(values),'new_unique_in_this_order':len(values-seen[kind]),'duplicates_of_previous':len(values&seen[kind])};seen[kind]|=values
   if p.name in BASE:base_union[kind]|=values
  item['equivalentClass_axioms']=len(tree.xpath('//owl:equivalentClass',namespaces=NS))
  item['SWRL_Imp']=len(tree.xpath('//rdf:type[@rdf:resource="'+NS['swrl']+'Imp"]',namespaces=NS))
  components.append(item)
 catalog=json.loads((ROOT/'research/catalog.json').read_text());manifest=json.loads((ROOT/'research/data/manifest.json').read_text())
 foreign=[]
 for p in (ROOT/'research/data').glob('*-manufacturing.ttl'):
  for line in p.read_text().splitlines():
   if line.startswith('@prefix'):continue
   if ' e:' in line or line.startswith('e:'):foreign.append(str(p.relative_to(ROOT)))
 for p in (ROOT/'research/data').glob('*-environmental.ttl'):
  for line in p.read_text().splitlines():
   if line.startswith('@prefix'):continue
   if ' i:' in line or line.startswith('i:'):foreign.append(str(p.relative_to(ROOT)))
 scenario=json.loads((ROOT/'research/data/functional-scenario.json').read_text());arithmetic=[]
 for b in scenario['batches']:
  for o in b['operations']:
   if not o['subloads']:continue
   s=o['subloads'];D=Decimal
   duration=sum(D(x['durationMinutes']) for x in s);volume=sum(D(x['volumeL']) for x in s)
   checks={'volume_each_at_most50L':all(D(x['volumeL'])<=50 for x in s),'sequential_nonoverlap':all(D(s[i]['endSeconds'])<=D(s[i+1]['startSeconds']) for i in range(len(s)-1)),'duration_matches_operation':duration==D(str(o['duration'])),'volume_matches_input_density':volume==D(o['input'])/D('1.25'),'energy_matches30kW':D(o['energy'])==duration*D(30)/D(60)}
   arithmetic.append({'operation':o['id'],'subload_count':len(s),'checks':checks})
 def atom(spec):return spec[1]+'('+', '.join(('"'+v[1]+'"^^xsd:'+v[0]) if isinstance(v,tuple) else str(v) for v in spec[2])+')'
 registry=[]
 for ident,title,body,head in RULES:
  entry={'id':ident,'kind':'SWRL','title':title,'source':'ontologies/alignment-rules.owl','code':' ^\n'.join(atom(a) for a in body)+'\n→\n'+' ^\n'.join(atom(a) for a in head),'body':body,'head':head,'units_and_effect':RANGES[ident],'test_positive':ident+'_positive','test_negative':ident+'_negative','operational_semantics':'SWRLAPI/Drools over named fixture individuals; class assertions accumulate and individuals are not generated'}
  if ident in ['R11','R23']:entry['arithmetic_precision']='Dividend multiplied by decimal1.0000000000000000 before guarded division to retain at least16 fractional places in SWRLAPI2.1.3; mathematical formula and units unchanged. This is computational precision, not physical measurement accuracy.'
  registry.append(entry)
 titles={'R07':'Datos ambientales obligatorios ausentes del proceso','R16':'Entrada material ausente de la operación','R19':'Medición ausente del control de calidad','R26':'Registro de limpieza ausente en el intervalo y equipo correspondientes'}
 for ident,code in VALIDATIONS.items():registry.append({'id':ident,'kind':'SPARQL ASK','title':titles[ident],'source':'research/model/validations/'+ident+'.sparql','code':PREFIX+code,'units_and_effect':'true means missing required record in the closed snapshot, not OWL inconsistency or physical contamination','test_positive':ident+('_missing'),'test_negative':ident+('_complete' if ident!='R26' else '_valid')})
 registry.sort(key=lambda x:x['id'])
 report={'status':'PASS' if not foreign and all(all(r['checks'].values()) for r in arithmetic) else 'FAIL','method':'Explicit OWL declarations deduplicated by full IRI; assertions counted from generator without target padding; arithmetic independently checked from fixture specification, not SPARQL outputs','components':components,'base_eight_unique_counts':{k:len(v) for k,v in base_union.items()},'complete_explicit_union_counts':{k:len(v) for k,v in seen.items()},'duplicate_declarations_base':{k:sum(c['counts'][k]['local_declared_unique'] for c in components if c['scope']=='base eight modules')-len(base_union[k]) for k in KINDS},'base_alignment_equivalences':next(c['equivalentClass_axioms'] for c in components if c['path']=='ontologies/alignment-rules.owl'),'contextual_correspondences':7,'mechanisms':{'SWRL':22,'ASK':4,'H3_SWRL':3,'additional_temporal_checks':2},'semantic_projection_foreign_vocabulary_errors':foreign,'functional_subload_checks':arithmetic,'catalog_sha256':hashlib.sha256((ROOT/'research/catalog.json').read_bytes()).hexdigest(),'configuration_sources':catalog['configurations'],'dataset_manifest':manifest,'scope':'Static census is distinct from HermiT consistency, SWRL execution, functional checks and new timing measurements. Seven contextual correspondences replace universal cross-standard class equivalences. No claim of industrial validation, full LCA, optimized eco-index, or formal DL-safety. Temporal precedes/follows remain simple; no new transitivity axiom.'}
 # Auxiliary snapshot controls are separate from the canonical mechanisms.
 # Count their declarations from the catalog instead of silently omitting CCTX.
 auxiliary=catalog.get('additional_controls',[])
 report['mechanisms']['SWRL']=len(RULES)
 report['mechanisms']['ASK']=len(VALIDATIONS)
 report['mechanisms']['SELECT']=len(catalog['queries'])
 report['mechanisms']['additional_cleaning_context_checks']=sum(c['id']=='C26' for c in auxiliary)
 report['mechanisms']['additional_context_integrity_checks']=sum(c['id']=='CCTX' for c in auxiliary)
 report['mechanisms']['additional_numeric_contract_checks']=sum(c['id']=='CNUM' for c in auxiliary)
 report['mechanisms']['additional_control_count']=len(auxiliary)
 report['mechanisms']['additional_ASK_artifact_count']=sum(1+len(c.get('stage_paths',{})) for c in auxiliary)
 report['mechanisms']['pre_inference_construct_count']=len(catalog.get('pre_inference_constructs',[]))
 report['mechanisms']['additional_controls']=[dict(c,kind='SPARQL ASK',included_in_canonical_counts=False) for c in auxiliary]
 report['mechanisms']['counting_scope']='Canonical base: 22 SWRL, four ASK and 21 SELECT. C26 (cleaning context evaluability) and CCTX (lot-operation-process/plan integrity) and CNUM (numeric input/output contract) are separate auxiliary snapshot controls; they do not increase canonical counts or assert OWL inconsistency.'
 dest=ROOT/'research/model';(dest/'census.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');(dest/'mechanisms.json').write_text(json.dumps({'prefixes':NS,'mechanisms':registry},ensure_ascii=False,indent=2)+'\n')
 readable=['# Catálogo canónico de mecanismos','22 reglas SWRL y cuatro validaciones ASK. Las clases se añaden sobre los mismos individuos; no se generan individuos nuevos. La ejecución y sus resultados se documentan por separado.','']
 for item in registry:readable+=['## '+item['id']+' — '+item['title'],'Tipo: '+item['kind']+'. '+item['units_and_effect']+'.','```\n'+item['code']+'\n```','']
 (dest/'mechanisms.md').write_text('\n'.join(readable))
 counts=report['base_eight_unique_counts'];print(json.dumps({'status':report['status'],'base8':counts,'complete':report['complete_explicit_union_counts'],'equivalences':report['base_alignment_equivalences'],'mechanisms':len(registry)},ensure_ascii=False))

if __name__=='__main__':main()
