"""Serialize the approved alignment mechanisms as OWL-compatible SWRL.

This is a serialization/model builder, not a substitute inference engine.
The worker independently executes the generated rules with SWRLAPI/Drools.
"""
from pathlib import Path
from lxml import etree as E

ROOT = Path(__file__).resolve().parents[2]
NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'owl': 'http://www.w3.org/2002/07/owl#',
    'swrl': 'http://www.w3.org/2003/11/swrl#',
    'swrlb': 'http://www.w3.org/2003/11/swrlb#',
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'i': 'http://w3id.org/protys/ontology/iso15531#',
    'e': 'http://w3id.org/protys/ontology/iso14040#',
    'c': 'http://w3id.org/protys/ontology/core#',
    'a': 'http://w3id.org/protys/ontology/alignment#',
    't': 'http://w3id.org/protys/ontology/research#',
}

def q(prefix, name): return '{' + NS[prefix] + '}' + name
def uri(value):
    if value.startswith('?'): return NS['a'] + 'var_' + value[1:]
    prefix, name = value.split(':', 1)
    return NS[prefix] + name

def C(pred, *args): return ('class', pred, args)
def O(pred, *args): return ('object', pred, args)
def D(pred, *args): return ('data', pred, args)
def B(pred, *args): return ('builtin', pred, args)

# Predicates and units are explicit in research-profile.owl and the catalog.
# R13 is scoped to the same lot and process by the recorded user decision.
RULES = [
('R01', 'GHG de electricidad por operación',
 [C('i:ProcessOperation','?op'),C('t:NumericInputValid','?op'),D('i:consumesEnergyKWh','?op','?kwh'),O('t:hasEmissionFactor','?op','?factor'),C('t:ElectricityEmissionFactor','?factor'),D('t:factorUnit','?factor',('string','kg CO2e/kWh')),D('t:factorValue','?factor','?f'),B('swrlb:greaterThanOrEqual','?kwh',0),B('swrlb:greaterThanOrEqual','?f',0),B('swrlb:multiply','?ghg','?kwh','?f')],
 [D('e:hasGHGImpactKgCO2e','?op','?ghg')]),
('R02', 'Presencia de inventario de agua del proceso',
 [C('i:ProcessOperation','?op'),D('i:consumesWaterL','?op','?water'),O('i:partOfProcess','?op','?process'),O('a:hasInventoryRepresentation','?process','?lci')],
 [O('e:hasInventoryFlow','?lci','c:WaterFlow')]),
('R03', 'Vincular insumo comprado al lote mediante su representación LCI',
 [C('i:Lot','?lot'),O('i:producesLot','?order','?lot'),O('i:hasProcess','?lot','?process'),O('a:hasInventoryRepresentation','?process','?lci'),C('e:UnitProcess','?lci'),O('e:hasInventoryFlow','?lci','?flow'),C('e:ProductFlow','?flow')],
 [O('a:hasRecordedInputFlowForLot','?lot','?flow')]),
('R04', 'Indicador por umbral explícito de la misma unidad y categoría',
 [C('e:ImpactIndicator','?indicator'),O('t:hasThreshold','?indicator','?record'),C('t:ThresholdRecord','?record'),D('e:hasIndicatorValue','?indicator','?value'),D('t:unit','?indicator','?unit'),D('t:indicatorCategory','?indicator','?category'),D('t:thresholdValue','?record','?threshold'),D('t:thresholdUnit','?record','?unit'),D('t:thresholdCategory','?record','?category'),B('swrlb:greaterThan','?value','?threshold')],
 [C('c:HighImpactIndicator','?indicator')]),
('R05', 'Vincular al lote los indicadores de sus procesos, sin sumar',
 [C('i:Lot','?lot'),O('i:hasProcess','?lot','?process'),O('a:hasInventoryRepresentation','?process','?lci'),O('e:hasImpact','?lci','?indicator')],
 [O('e:hasImpact','?lot','?indicator')]),
('R06', 'Asociar factor al flujo del material correspondiente',
 [C('e:ProductFlow','?flow'),O('e:refersToMaterial','?flow','?material'),O('t:hasEmissionFactor','?material','?factor')],
 [O('t:usesEmissionFactor','?flow','?factor')]),
('R08', 'Cantidad de unidad funcional del lote',
 [C('i:Lot','?lot'),D('i:functionalUnit','?lot','?unit'),D('i:producesQuantity','?lot','?qty')],
 [D('e:hasFunctionalUnitQuantity','?lot','?qty')]),
('R09', 'Uso del recurso requerido y asignado en el mismo plan',
 [C('i:ProcessPlan','?plan'),O('i:assignsResource','?plan','?resource'),O('i:includesActivity','?plan','?op'),O('i:requiresResource','?op','?resource')],
 [O('i:usesResource','?op','?resource')]),
('R10', 'Registrar estado propagable del mismo lote',
 [C('i:Lot','?lot'),D('i:hasQualityStatus','?lot','?status')],
 [D('i:propagatedQualityStatus','?lot','?status')]),
('R11', 'Razón rendimiento sobre duración positiva de operación',
 [C('i:ProcessOperation','?op'),D('i:yield','?op','?yield'),D('i:duration','?op','?duration'),B('swrlb:greaterThan','?duration',0),B('swrlb:multiply','?preciseYield','?yield',('decimal','1.0000000000000000')),B('swrlb:divide','?efficiency','?preciseYield','?duration')],
 [D('a:hasEfficiency','?op','?efficiency')]),
('R12', 'Capacidad FineGrinding por operación de molienda explícita',
 [C('i:EquipmentResource','?resource'),C('t:FineGrindingOperation','?op'),O('i:requiresResource','?op','?resource')],
 [O('i:hasCapability','?resource','a:FineGrinding')]),
('R13', 'Precedencia de eventos MES del mismo lote y proceso',
 [C('i:ManufacturingData','?e1'),C('i:ManufacturingData','?e2'),O('t:forLot','?e1','?lot'),O('t:forLot','?e2','?lot'),O('t:eventForProcess','?e1','?process'),O('t:eventForProcess','?e2','?process'),D('t:eventTimeSeconds','?e1','?t1'),D('t:eventTimeSeconds','?e2','?t2'),B('swrlb:lessThan','?t1','?t2')],
 [O('i:precedesEvent','?e1','?e2')]),
('R14', 'Operador responsable de la operación de su orden',
 [C('i:WorkOrder','?order'),O('i:executedBy','?order','?operator'),C('i:HumanResource','?operator'),O('i:executesActivity','?order','?op')],
 [O('i:responsibleOperator','?op','?operator')]),
('R15', 'Marcar indisponibilidad temporal por mantenimiento ACTIVE en la instantánea',
 [C('i:ManufacturingResource','?resource'),O('i:hasMaintenance','?resource','?maintenance'),D('t:maintenanceStatus','?maintenance',('string','ACTIVE'))],
 [C('i:temporarilyUnavailable','?resource')]),
('R17', 'Requerir limpieza entre lotes consecutivos de familia distinta',
 [C('i:Lot','?l1'),C('i:Lot','?l2'),O('i:processedOn','?l1','?equipment'),O('i:processedOn','?l2','?equipment'),O('t:nextLot','?l1','?l2'),D('t:productFamilyCode','?l1','?f1'),D('t:productFamilyCode','?l2','?f2'),B('swrlb:notEqual','?f1','?f2')],
 [O('i:requiresCleaningBetween','?l1','?l2')]),
('R18', 'Marcar lote candidato a reproceso por no conformidad',
 [C('i:Lot','?lot'),O('i:hasNonConformity','?lot','?nonconformity')],
 [C('i:requiresReprocess','?lot')]),
('R20', 'Puente flujo y operación de consumo del mismo proceso',
 [C('i:ProcessOperation','?op'),O('i:consumesMaterial','?op','?material'),O('i:partOfProcess','?op','?process'),O('a:hasInventoryRepresentation','?process','?lci'),C('e:ProductFlow','?flow'),O('e:hasInventoryFlow','?lci','?flow'),O('e:refersToMaterial','?flow','?material'),O('t:belongsToProcess','?flow','?process')],
 [O('c:bridgesFlowToActivity','?flow','?op')]),
('R21', 'Contribución eléctrica identificable por operación del lote',
 [C('i:Lot','?lot'),C('t:NumericInputValid','?op'),O('i:hasActivity','?lot','?op'),O('i:partOfProcess','?op','?process'),O('i:hasProcess','?lot','?process'),O('t:hasEmissionFactor','?op','?factor'),D('t:factorUnit','?factor',('string','kg CO2e/kWh')),D('e:hasGHGImpactKgCO2e','?op','?ghg')],
 [O('t:hasGHGContribution','?lot','?op')]),
('R22', 'Hotspot vinculado al mismo proceso y recurso cuello de botella',
 [C('c:HighImpactIndicator','?indicator'),O('e:computedForProcess','?indicator','?lci'),O('a:hasInventoryRepresentation','?process','?lci'),O('i:partOfProcess','?op','?process'),O('i:requiresResource','?op','?resource'),C('c:BottleneckResource','?resource')],
 [C('c:Hotspot','?resource')]),
('R23', 'Razón por operación e indicador vinculados, sin normalización',
 [C('i:Lot','?lot'),C('i:WorkOrder','?order'),O('i:producesLot','?order','?lot'),O('i:hasActivity','?lot','?op'),O('i:hasProcess','?lot','?process'),O('i:partOfProcess','?op','?process'),O('a:hasInventoryRepresentation','?process','?lci'),D('i:cycleTime','?op','?time'),O('e:computedForProcess','?indicator','?lci'),D('e:indicatorValue','?indicator','?value'),O('t:forOperation','?ratio','?op'),O('t:forIndicator','?ratio','?indicator'),B('swrlb:greaterThan','?value',0),B('swrlb:multiply','?preciseTime','?time',('decimal','1.0000000000000000')),B('swrlb:divide','?result','?preciseTime','?value')],
 [D('t:operationIndicatorRatio','?ratio','?result')]),
('R24', 'Marcar brecha de cobertura desde faltante ambiental detectado',
 [C('c:MissingEnvironmentalData','?process')],
 [C('c:CoverageGap','?process')]),
('R25', 'Marcar factor modificado que requiere recálculo',
 [C('t:EmissionFactor','?factor'),D('c:hasVersion','?factor','?version'),D('c:modifiedInVersion','?factor','?version')],
 [C('c:RequiresRecomputeIndicators','?factor')]),
]

def add_value(parent, tag, value):
    n = E.SubElement(parent, q('rdf', tag))
    if isinstance(value,tuple):
        n.set(q('rdf','datatype'),NS['xsd']+value[0]);n.text=value[1]
    elif isinstance(value, (int, float)):
        n.set(q('rdf','datatype'), NS['xsd']+'decimal'); n.text = str(value)
    else: n.set(q('rdf','resource'), uri(value))
    return n

def atom_element(spec):
    kind, pred, args = spec
    n = E.Element(q('rdf','Description'))
    atom_type = {'class':'ClassAtom','object':'IndividualPropertyAtom','data':'DatavaluedPropertyAtom','builtin':'BuiltinAtom'}[kind]
    E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['swrl']+atom_type)
    key = {'class':'classPredicate','object':'propertyPredicate','data':'propertyPredicate','builtin':'builtin'}[kind]
    E.SubElement(n,q('swrl',key)).set(q('rdf','resource'),uri(pred))
    if kind == 'builtin':
        c = E.SubElement(n,q('swrl','arguments'))
        append_list(c,args,False)
    else:
        for i,value in enumerate(args,1):
            v=E.SubElement(n,q('swrl','argument'+str(i)))
            if isinstance(value,tuple):v.set(q('rdf','datatype'),NS['xsd']+value[0]);v.text=value[1]
            else:v.set(q('rdf','resource'),uri(value))
    return n

def append_list(parent,items,atoms=True):
    n = E.SubElement(parent,q('rdf','Description'))
    E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['rdf']+'List')
    first = E.SubElement(n,q('rdf','first'))
    if atoms:first.append(atom_element(items[0]))
    elif isinstance(items[0],tuple):
        first.set(q('rdf','datatype'),NS['xsd']+items[0][0]);first.text=items[0][1]
    elif isinstance(items[0],(int,float)):
        first.set(q('rdf','datatype'),NS['xsd']+'decimal');first.text=str(items[0])
    else:first.set(q('rdf','resource'),uri(items[0]))
    rest=E.SubElement(n,q('rdf','rest'))
    if len(items)==1:rest.set(q('rdf','resource'),NS['rdf']+'nil')
    else:append_list(rest,items[1:],atoms)

def main():
    path=ROOT/'ontologies/alignment-rules.owl'
    tree=E.parse(str(path)); root=tree.getroot()
    comments={
      NS['a'][:-1]:'Capa de alineamiento del modelo evaluado: siete correspondencias relacionales contextuales (sin equivalencias universales de clases), 22 reglas SWRL Imp y cuatro validaciones SPARQL ASK (R07, R16, R19 y R26). La ejecución usa individuos nombrados con SWRLAPI/Drools; no se afirma decidibilidad o DL-safety general. El catálogo y los fixtures fijan entradas, unidades y alcance.',
      NS['c']+'BottleneckResource':'Recurso declarado como cuello de botella en los datos del escenario; R22 relaciona esta entrada con el indicador del mismo proceso. No constituye una medición de capacidad ni una inferencia de R12.',
      NS['a']+'hasEcoScore':'IRI histórica conservada para compatibilidad; no es salida de las reglas activas. R23 registra una razón por operación e indicador en research:operationIndicatorRatio, sin normalización.',
      NS['a']+'hasEfficiency':'Razón de rendimiento másico porcentual sobre duración positiva en minutos (R11). No es un índice ambiental normalizado ni una prueba de optimización.',
      NS['a']+'R26':'Validación SPARQL ASK de ausencia de registro de limpieza del mismo equipo dentro del intervalo entre dos lotes consecutivos que requieren limpieza. No infiere contaminación física ni ejecuta una limpieza. R24 identifica brechas de cobertura y conserva numeración propia.',
    }
    for uri_,comment in comments.items():
        for n in root.xpath('./*[@rdf:about="'+uri_+'"]',namespaces=NS):
            nodes=n.findall(q('rdfs','comment'))
            if not nodes:nodes=[E.SubElement(n,q('rdfs','comment'))]
            nodes[0].text=comment
    for n in root.xpath('//comment()'):
        if 'DL-SAFE' in (n.text or ''):n.text=' 22 REGLAS SWRL; CUATRO VALIDACIONES ASK EN ARCHIVO SEPARADO '
    for n in root.xpath('./rdf:Description[swrl:body]',namespaces=NS):root.remove(n)
    for n in root.xpath('./rdf:Description[rdf:type/@rdf:resource="'+NS['swrl']+'Variable"]',namespaces=NS):root.remove(n)
    for n in root.xpath('//owl:imports[@rdf:resource="'+NS['swrl'][:-1]+'"]|//owl:imports[@rdf:resource="'+NS['swrl']+'"]',namespaces=NS):n.getparent().remove(n)
    variables=set()
    for ident,title,body,head in RULES:
        n=E.SubElement(root,q('rdf','Description'));n.set(q('rdf','about'),NS['a']+ident)
        E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['swrl']+'Imp')
        E.SubElement(n,q('rdfs','label')).text=ident+': '+title
        for side,atoms in [('body',body),('head',head)]:
            append_list(E.SubElement(n,q('swrl',side)),atoms)
            variables.update(a for _,_,args in atoms for a in args if isinstance(a,str) and a.startswith('?'))
    for variable in sorted(variables):
        n=E.SubElement(root,q('rdf','Description'));n.set(q('rdf','about'),uri(variable))
        E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['swrl']+'Variable')
    tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
    print('Serialized',len(RULES),'SWRL implications with',len(variables),'declared variables')

if __name__=='__main__':main()
