"""The seven approved correspondences are contextual relations, not identity."""
import json
from pathlib import Path
from lxml import etree as E
from build_rules import NS,q
from build_annotations import update_annotations
ROOT=Path(__file__).resolve().parents[2]
I=NS['i'];V=NS['e'];A=NS['a'];T=NS['t'];C=NS['c']
MAPPINGS=[
 ('A1','ProductFlow ≡ Product','flowRefersToProduct',V+'ProductFlow',C+'Product','Flow record refers to a distinct product/material entity; a purchased commodity flow is not the commodity itself.','CE_Product','e:ProductFlow'),
 ('A2','UnitProcess ≡ ManufacturingProcess','hasInventoryRepresentation',I+'ManufacturingProcess',V+'UnitProcess','A manufacturing execution has a separately identified LCI black-box representation, only for the declared production boundary. No universal class inclusion.','CE_Manufacturing','e:UnitProcess'),
 ('A3','ElementaryFlow ≡ ManufacturingResource consumed','inputFlowForResource',V+'ProductFlow',I+'ManufacturingResource','Purchased inputs are technosphere ProductFlow records linked to consumed material resources; consumption never implies an elementary nature exchange.','CE_ConsumedResource','e:ElementaryFlow'),
 ('A4','SystemBoundary ≡ ManufacturingFacility','facilityInBoundary',I+'ManufacturingFacility',V+'SystemBoundary','A separately identified boundary describes the selected scope for an assessed facility; a facility is not the boundary criteria.','CE_Facility','e:SystemBoundary'),
 ('A5','ImpactIndicator ≡ QualityAttribute','indicatorForLot',V+'ImpactIndicator',I+'Lot','Environmental indicator and quality record independently refer to the same lot via indicatorForLot and qualityForLot. Their values and types remain distinct.','CE_Quality','e:ImpactIndicator'),
 ('A6','AllocationProcedure ≡ ProcessOperation','allocationForOperation',V+'AllocationProcedure',I+'ProcessOperation','Optional relation for an explicitly supplied allocation procedure. Painting scenario performs no allocation among coproducts; only a separate illustrative fixture exercises the relation.','CE_Operation','e:AllocationProcedure'),
 ('A7','LifeCycleInventory ≡ ProcessPlan','inventoryForPlan',V+'LifeCycleInventory',I+'ProcessPlan','A partial production inventory record refers to its manufacturing plan; planning and inventory records are separately identified.','CE_Plan','e:LifeCycleInventory'),
]
def declare(root,name,domain,range_,comment):
 nodes=root.xpath('./owl:ObjectProperty[@rdf:about="'+A+name+'"]',namespaces=NS)
 n=nodes[0] if nodes else E.SubElement(root,q('owl','ObjectProperty'));n.set(q('rdf','about'),A+name)
 for child in list(n):n.remove(child)
 E.SubElement(n,q('rdfs','domain')).set(q('rdf','resource'),domain);E.SubElement(n,q('rdfs','range')).set(q('rdf','resource'),range_);E.SubElement(n,q('rdfs','comment')).text=comment
 # Raw source edges have common research predicates. The integrated schema
 # maps those contextual predicates directionally to the typed bridge.
 uri=T+name;nodes=root.xpath('./owl:ObjectProperty[@rdf:about="'+uri+'"]',namespaces=NS)
 source=nodes[0] if nodes else E.SubElement(root,q('owl','ObjectProperty'));source.set(q('rdf','about'),uri)
 for x in list(source):source.remove(x)
 E.SubElement(source,q('rdfs','subPropertyOf')).set(q('rdf','resource'),A+name)
def main():
 path=ROOT/'ontologies/alignment-rules.owl';tree=E.parse(str(path));root=tree.getroot()
 for n in root.xpath('./rdf:Description[owl:equivalentClass]',namespaces=NS):root.remove(n)
 for ident,before,prop,domain,range_,why,ce,notclass in MAPPINGS:declare(root,prop,domain,range_,why)
 declare(root,'qualityForLot',I+'QualityAttribute',I+'Lot','Quality and environmental records share lot context without class equivalence.')
 declare(root,'hasRecordedInputFlowForLot',I+'Lot',V+'ProductFlow','R03 associates recorded purchased input flow with the lot through the manufacturing execution and its separately identified LCI representation; no emissions are asserted.')
 if not root.xpath('./owl:DatatypeProperty[@rdf:about="'+A+'hasEfficiency"]',namespaces=NS):
  n=E.SubElement(root,q('owl','DatatypeProperty'));n.set(q('rdf','about'),A+'hasEfficiency');E.SubElement(n,q('rdfs','range')).set(q('rdf','resource'),NS['xsd']+'decimal')
 # R20 now applies to purchased material input records, not nature emissions.
 for n in root.xpath('./owl:ObjectProperty[@rdf:about="'+C+'bridgesFlowToActivity"]/rdfs:domain',namespaces=NS):n.set(q('rdf','resource'),V+'ProductFlow')
 for n in root.xpath('./owl:NamedIndividual[@rdf:about="'+C+'WaterFlow"]/rdf:type',namespaces=NS):n.set(q('rdf','resource'),V+'ProductFlow')
 tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 # Source comments describe current axioms and remain distinct from any
 # historical claim of consistency; runtime evidence is stored separately.
 corrections={
  'iso14040-module.owl':{V[:-1]:'Formalización parcial propuesta a partir de conceptos de ISO14040 para interoperabilidad. El cálculo ejecutado es un inventario parcial de emisiones asociadas a electricidad, con supuestos declarados; no es un ACV completo. La consistencia OWL y ejecución SWRL se verifican por separado en research/evaluation.',V+'hasFunctionalUnit':'Propiedad funcional: como máximo una unidad funcional por sujeto. La clase LCAStudy incorpora además una cardinalidad exacta1; bajo mundo abierto eso no prueba que exista un registro nombrado completo en la instantánea.',V+'emits':'Salida elemental de un proceso unitario hacia la naturaleza; no se utiliza para representar compras de materias primas del escenario.',V+'hasEmissionFactor':'Propiedad histórica del vocabulario que refiere a CharacterizationFactor; los factores eléctricos/materiales del escenario se representan por research:hasEmissionFactor sin reclasificarlos como factoresLCIA.',V+'hasInventoryFlow':'Flujo registrado del inventario: puede ser intercambio elemental con naturaleza o ProductFlow dentro de la tecnosfera, con tipo y dirección explicitados en cada caso.'},
  'iso15531-module.owl':{I[:-1]:'Formalización parcial propuesta a partir de conceptos de ISO15531 MANDATE para interoperabilidad. Las restricciones y ejemplos de manufactura se evalúan en el escenario construido; no constituyen formalización exhaustiva del estándar ni validación industrial.',I+'resourceCurrentState':'Estado registrado de un recurso de manufactura. El estado consumido no convierte el recurso en ElementaryFlow ni lo identifica con un registro de flujo.'},
  'alignment-rules.owl':{C+'Hotspot':'Recurso declarado cuello de botella y vinculado al mismo proceso que un indicador por encima del umbral del escenario (R22). No prueba baja eficiencia, contaminación, causalidad o superioridad industrial.',C+'hasLotGHGImpact':'IRI histórica conservada; no es la salida activa de R21. Las contribuciones se identifican por research:hasGHGContribution y se suman en consultas.',C+'hasEfficiency':'IRI histórica conservada; la regla R11 activa produce alignment:hasEfficiency, razón de rendimiento porcentual sobre duración positiva.',C+'modifiedInVersion':'Versión de modificación de un término o factor, usada porR25; la preguntaP5 corresponde al producto generado por un plan.',A+'hasEmittedFlow':'IRI histórica conservada para salidas elementales; no se utiliza para las entradas compradas del escenario. R03 produce alignment:hasRecordedInputFlowForLot.',A+'Consumed':'Estado consumido de un recurso; no implica pertenencia a ElementaryFlow.'}
 }
 for filename,values in corrections.items():
  p=ROOT/'ontologies'/filename;tree=E.parse(str(p));root=tree.getroot()
  for uri_,comment in values.items():
   for n in root.xpath('./*[@rdf:about="'+uri_+'"]',namespaces=NS):
    nodes=n.findall(q('rdfs','comment'))
    if not nodes:nodes=[E.SubElement(n,q('rdfs','comment'))]
    nodes[0].text=comment
  for n in root.xpath('//rdfs:comment',namespaces=NS):
   if n.text:n.text=n.text.replace('Listado ','Fragmento de código ').replace('sin alterar el conteo de 18 clases del módulo ISO 15531 (Fragmento de código 3.2)','como concepto auxiliar del perfil de manufactura')
  for n in root.xpath('//comment()'):
   if 'LOS 7 AXIOMAS owl:equivalentClass' in (n.text or ''):n.text=' SIETE CORRESPONDENCIAS CONTEXTUALES; SIN EQUIVALENCIAS UNIVERSALES '
   if 'CLASES (Listado' in (n.text or ''):n.text=' CLASES: declaraciones explícitas del módulo '
  tree.write(str(p),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 # Inventory records may be elementary exchanges or technosphere products.
 path=ROOT/'ontologies/iso14040-module.owl';tree=E.parse(str(path));root=tree.getroot()
 for name,tag in [('hasInventoryFlow','range'),('refersToMaterial','domain')]:
  n=root.xpath('./owl:ObjectProperty[@rdf:about="'+V+name+'"]',namespaces=NS)[0]
  for x in n.findall(q('rdfs',tag)):n.remove(x)
  union=E.SubElement(E.SubElement(n,q('rdfs',tag)),q('owl','Class'));members=E.SubElement(union,q('owl','unionOf'));members.set(q('rdf','parseType'),'Collection')
  for name_ in ['ElementaryFlow','ProductFlow']:E.SubElement(members,q('owl','Class')).set(q('rdf','about'),V+name_)
 tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 fiches=[]
 for ident,before,prop,domain,range_,why,ce,notclass in MAPPINGS:fiches.append({'id':ident,'before':before,'after':A+prop,'domain':domain,'range':range_,'scientific_scope':why,'counterexample_entity':'f:'+ce,'must_not_infer_type':notclass,'policy':'Distinct named endpoints, no equivalentClass/owl:sameAs or universal subclass implication','source':'ontologies/alignment-rules.owl','status':'approved human correction; execution results recorded separately'})
 (ROOT/'research/model/contextual-correspondences.json').write_text(json.dumps({'sources':['https://eplca.pages.code.europa.eu/ilcd_docs/common/enumerations/FlowType.html','https://eplca.jrc.ec.europa.eu/LCDN/downloads/ILCD_Format_1.1_Documentation/ILCD_ProcessDataSet.html'],'correspondences':fiches},ensure_ascii=False,indent=2)+'\n')
 update_annotations(ROOT)
 print('Seven global equivalences removed; seven contextual correspondence contracts written')
if __name__=='__main__':main()
