"""Canonical SPARQL catalog and independently derived fixture expectations."""
import json
from decimal import Decimal
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MODEL=ROOT/'research/model'; DATA=ROOT/'research/data'
N={'i':'http://w3id.org/protys/ontology/iso15531#','e':'http://w3id.org/protys/ontology/iso14040#','c':'http://w3id.org/protys/ontology/core#','a':'http://w3id.org/protys/ontology/alignment#','t':'http://w3id.org/protys/ontology/research#','p':'http://w3id.org/protys/ontology/paint-ext#','r':'http://w3id.org/protys/ontology/resource#','f':'http://w3id.org/protys/fixture#','owl':'http://www.w3.org/2002/07/owl#','xsd':'http://www.w3.org/2001/XMLSchema#'}
PREFIX='\n'.join('PREFIX '+k+': <'+v+'>' for k,v in N.items())+'\n'
QUERIES={
'Q01':('Ranking de procesos por electricidad y emisiones acumuladas',[],
'''SELECT ?process (SUM(?energy) AS ?energyKWh) (SUM(?ghg) AS ?ghgKgCO2e)
WHERE { { SELECT DISTINCT ?process ?op ?energy ?ghg WHERE {
?lot a i:Lot ; i:hasProcess ?process ; i:hasActivity ?op .
?op a i:ProcessOperation ; i:partOfProcess ?process ; i:consumesEnergyKWh ?energy ; e:hasGHGImpactKgCO2e ?ghg . } } }
GROUP BY ?process ORDER BY DESC(?ghgKgCO2e)'''),
'Q02':('Trazabilidad lote, plan, producto, recursos requeridos e insumos',['P2','P3','P5'],
'''SELECT DISTINCT ?lot ?plan ?product ?resource
WHERE { ?lot a i:Lot ; t:hasPlan ?plan ; t:producesProduct ?product .
?lot i:hasProcess ?process . ?plan t:planProduces ?product ; i:includesActivity ?op .
?op i:partOfProcess ?process . { ?op i:requiresResource ?resource } UNION { ?op i:hasInput ?resource } }
ORDER BY ?lot ?resource'''),
'Q03':('Electricidad y emisiones por lote, sin fuentes de materias primas',[],
'''SELECT ?lot (SUM(?energy) AS ?energyKWh) (SUM(?ghg) AS ?ghgKgCO2e)
WHERE { { SELECT DISTINCT ?lot ?op ?energy ?ghg WHERE {
?lot a i:Lot ; i:hasProcess ?process ; i:hasActivity ?op .
?op i:partOfProcess ?process ; i:consumesEnergyKWh ?energy ; e:hasGHGImpactKgCO2e ?ghg . } } }
GROUP BY ?lot ORDER BY ?lot'''),
'Q04':('Recursos requeridos e insumos materiales por proceso',['P1'],
'''SELECT DISTINCT ?process ?resource
WHERE { ?op a i:ProcessOperation ; i:partOfProcess ?process . { ?op i:requiresResource ?resource } UNION { ?op i:hasInput ?resource } }
ORDER BY ?process ?resource'''),
'Q05':('Materiales según factores hipotéticos del escenario, excluidos de Q06',[],
'''SELECT ?material ?carbonFactor ?waterFactor ?exceedsDeclaredCarbonThreshold
WHERE { ?material a r:Raw_Material ; t:materialFactorKgCO2ePerKg ?carbonFactor ; t:waterFootprintLPerKg ?waterFactor .
BIND((?carbonFactor > 5.0) AS ?exceedsDeclaredCarbonThreshold)
FILTER(?carbonFactor > 3.0 || ?waterFactor > 100) } ORDER BY DESC(?carbonFactor)'''),
'Q06':('Intensidad de emisiones eléctricas del proceso productivo en kg CO2e/L',[],
'''SELECT ?product ?lot ?liters (SUM(?ghg) AS ?ghgKgCO2e)
((SUM(?ghg) / ?liters) AS ?kgCO2ePerL)
WHERE { { SELECT DISTINCT ?product ?lot ?liters ?op ?ghg WHERE {
?lot a i:Lot ; t:producesProduct ?product ; i:functionalUnit "L" ; i:producesQuantity ?liters ; t:hasGHGContribution ?op ; i:hasProcess ?process ; i:hasActivity ?op .
?op i:partOfProcess ?process ; e:hasGHGImpactKgCO2e ?ghg . FILTER(?liters > 0) } } }
GROUP BY ?product ?lot ?liters ORDER BY ?lot'''),
'Q07':('Residuo de balance después de pérdidas registradas, tolerancia máxima 1%',[],
'''SELECT ?operation ?inputMassKg ?outputMassKg ?registeredLossKg ?residueKg ?status
WHERE { ?operation a i:ProcessOperation ; t:inputMassKg ?inputMassKg ; t:outputMassKg ?outputMassKg ; t:registeredLossKg ?registeredLossKg .
BIND(ABS(?inputMassKg - ?outputMassKg - ?registeredLossKg) AS ?residueKg)
BIND(IF(?inputMassKg <= 0 || ?outputMassKg < 0 || ?registeredLossKg < 0, "INVALID_INPUT",
IF(?residueKg <= 0.01 * ?inputMassKg, "OK", "REVISAR")) AS ?status) } ORDER BY ?operation'''),
'Q08':('Rendimiento másico, electricidad e intensidad de emisiones por operación',[],
'''SELECT ?operation ?stageName ?yieldPercent ?energyKWh ?ghgKgCO2e ?outputVolumeL (?ghgKgCO2e / ?outputVolumeL AS ?kgCO2ePerL)
WHERE { ?operation a p:Manufacturing_Stage ; c:hasName ?stageName ; t:yieldPercent ?yieldPercent ; i:consumesEnergyKWh ?energyKWh ; e:hasGHGImpactKgCO2e ?ghgKgCO2e ; t:outputVolumeL ?outputVolumeL . FILTER(?outputVolumeL > 0) }
ORDER BY ?operation'''),
'Q09':('Dependencias de indicadores respecto de factores y versiones',[],
'''SELECT ?factor ?version ?process ?indicator ?value
WHERE { ?factor a t:EmissionFactor ; c:hasVersion ?version .
?indicator a e:ImpactIndicator ; t:usesEmissionFactor ?factor ; e:hasIndicatorValue ?value ; e:computedForProcess ?lci . ?process a:hasInventoryRepresentation ?lci . }
ORDER BY ?process'''),
'Q10':('Proveedores y factores hipotéticos de materiales, separados del inventario eléctrico',[],
'''SELECT ?supplier ?material ?factor ?value
WHERE { ?material a r:Raw_Material ; t:providedBy ?supplier ; t:hasEmissionFactor ?factor . ?factor t:factorValue ?value . }
ORDER BY ?material'''),
'Q11':('Emisiones eléctricas y cantidad de no conformidades por lote',[],
'''SELECT ?lot ?ghgKgCO2e ?numNC WHERE {
{ SELECT ?lot (SUM(?v) AS ?ghgKgCO2e) WHERE {
{ SELECT DISTINCT ?lot ?op ?v WHERE { ?lot a i:Lot ; i:hasProcess ?process ; i:hasActivity ?op ; t:hasGHGContribution ?op .
?op i:partOfProcess ?process ; e:hasGHGImpactKgCO2e ?v . } } } GROUP BY ?lot }
{ SELECT ?lot (COUNT(DISTINCT ?nc) AS ?numNC) WHERE { ?lot a i:Lot . OPTIONAL { ?lot i:hasNonConformity ?nc } } GROUP BY ?lot }
} ORDER BY ?lot'''),
'Q12':('Trazabilidad indicador, proceso, operación y recurso vinculados',[],
'''SELECT ?indicator ?process ?operation ?resource ?value
WHERE { ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:hasIndicatorValue ?value . ?process a:hasInventoryRepresentation ?lci .
?operation i:partOfProcess ?process ; i:usesResource ?resource . } ORDER BY ?indicator ?operation ?resource'''),
'Q13':('Inventario explícito de flujos materiales por proceso',[],
'''SELECT ?process ?flow ?material ?quantityKg ?unit
WHERE { ?flow a e:ProductFlow ; t:belongsToProcess ?process ; e:refersToMaterial ?material ; t:materialQuantityKg ?quantityKg ; t:unit ?unit . }
ORDER BY ?process ?material'''),
'Q14':('Falta de registro de limpieza del mismo equipo entre lotes consecutivos',[],
'''SELECT ?lotA ?lotB ?equipment WHERE {
?lotA i:requiresCleaningBetween ?lotB ; i:processedOn ?equipment ; t:endTimeSeconds ?endA .
?lotB i:processedOn ?equipment ; t:startTimeSeconds ?startB .
FILTER NOT EXISTS { ?clean a i:CleaningOperation ; t:cleanedResource ?equipment ; t:startTimeSeconds ?cleanStart ; t:endTimeSeconds ?cleanEnd .
FILTER(?cleanStart >= ?endA && ?cleanEnd <= ?startB && ?cleanEnd >= ?cleanStart) }
} ORDER BY ?lotA ?lotB'''),
'Q15':('Agua registrada por operación auxiliar de limpieza',[],
'''SELECT ?clean ?waterL WHERE { ?clean a i:CleaningOperation ; i:consumesWaterL ?waterL . } ORDER BY ?clean'''),
'Q16':('Tiempo de uso y OEE registrado por recurso y plan',['P2'],
'''SELECT ?plan ?resource (SUM(?duration) AS ?usedTimeMinutes) ?oeePercent
WHERE { { SELECT DISTINCT ?plan ?operation ?resource ?duration WHERE {
?lot t:hasPlan ?plan ; i:hasProcess ?process . ?plan a i:ProcessPlan ; i:includesActivity ?operation .
?operation i:partOfProcess ?process ; i:usesResource ?resource ; i:duration ?duration . } }
OPTIONAL { ?resource t:oeePercent ?oeePercent } } GROUP BY ?plan ?resource ?oeePercent ORDER BY ?plan ?resource'''),
'Q17':('Emisiones eléctricas por unidad funcional de salida, litros positivos',[],
'''SELECT ?lot ?functionalUnit ?outputLiters (SUM(?v)/?outputLiters AS ?impactPerFU)
WHERE { { SELECT DISTINCT ?lot ?functionalUnit ?outputLiters ?operation ?v WHERE {
?lot a i:Lot ; i:functionalUnit ?functionalUnit ; i:producesQuantity ?outputLiters ; t:hasGHGContribution ?operation ; i:hasProcess ?process ; i:hasActivity ?operation .
?operation i:partOfProcess ?process ; e:hasGHGImpactKgCO2e ?v . FILTER(?outputLiters > 0 && STR(?functionalUnit) = "L") } } }
GROUP BY ?lot ?functionalUnit ?outputLiters ORDER BY ?lot'''),
'Q18':('Actividades del plan, orden explícito y duración de cadena lineal',['P4'],
'''SELECT DISTINCT ?plan ?activity ?stageIndex ?stageName ?lifecycleStage ?totalDurationMinutes WHERE {
?plan a i:ProcessPlan ; i:includesActivity ?activity ; t:lifecycleStage ?lifecycleStage .
?lot t:hasPlan ?plan ; i:hasProcess ?process .
?activity i:partOfProcess ?process ; t:stageIndex ?stageIndex ; c:hasName ?stageName .
{ SELECT ?plan (SUM(?d) AS ?totalDurationMinutes) WHERE {
{ SELECT DISTINCT ?plan ?op ?d WHERE { ?owner t:hasPlan ?plan ; i:hasProcess ?ownedProcess .
?plan i:includesActivity ?op . ?op i:partOfProcess ?ownedProcess ; i:duration ?d . } } } GROUP BY ?plan }
} ORDER BY ?plan ?stageIndex'''),
'Q19':('Ranking de lotes por emisiones eléctricas acumuladas',[],
'''SELECT ?lot (SUM(?v) AS ?ghgKgCO2e) WHERE {
{ SELECT DISTINCT ?lot ?operation ?v WHERE { ?lot a i:Lot ; i:hasProcess ?process ; i:hasActivity ?operation ; t:hasGHGContribution ?operation .
?operation i:partOfProcess ?process ; e:hasGHGImpactKgCO2e ?v . } } }
GROUP BY ?lot ORDER BY DESC(?ghgKgCO2e)'''),
'Q20':('Datos críticos ausentes por tipo, con criterio explícito',[],
'''SELECT ?entity ?kind ?missingProperty WHERE {
{ ?entity a i:ManufacturingProcess . BIND("ManufacturingProcess" AS ?kind) BIND(e:computedForProcess AS ?missingProperty)
FILTER NOT EXISTS { ?entity t:hasInventoryRepresentation ?lci . ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:indicatorValue ?value . } }
UNION { ?entity a i:ProcessOperation . BIND("ProcessOperation" AS ?kind) BIND(i:hasInput AS ?missingProperty)
FILTER NOT EXISTS { ?entity i:hasInput ?input . } }
UNION { ?entity a i:Quality_Control_Activity . BIND("QualityControl" AS ?kind) BIND(i:hasMeasurement AS ?missingProperty)
FILTER NOT EXISTS { ?entity i:hasMeasurement ?measurement . } }
} ORDER BY ?entity ?kind'''),
'Q21':('Términos con versión modificada respecto de la registrada',[],
'''SELECT ?entity ?currentVersion ?priorVersion WHERE { ?entity c:hasVersion ?currentVersion ; c:hasPriorVersion ?priorVersion .
FILTER(?currentVersion != ?priorVersion) } ORDER BY ?entity'''),
}

VALIDATIONS={
'R07':'''ASK { ?process a i:ManufacturingProcess . FILTER NOT EXISTS { ?process t:hasInventoryRepresentation ?lci . ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:indicatorValue ?value . } }''',
'R16':'''ASK { ?operation a i:ProcessOperation . FILTER NOT EXISTS { ?operation i:hasInput ?input . } }''',
'R19':'''ASK { ?operation a i:Quality_Control_Activity . FILTER NOT EXISTS { ?operation i:hasMeasurement ?measurement . } }''',
'R26':'''ASK { ?lotA i:requiresCleaningBetween ?lotB ; i:processedOn ?equipment ; t:endTimeSeconds ?endA .
?lotB i:processedOn ?equipment ; t:startTimeSeconds ?startB .
FILTER NOT EXISTS { ?clean a i:CleaningOperation ; t:cleanedResource ?equipment ; t:startTimeSeconds ?cleanStart ; t:endTimeSeconds ?cleanEnd .
FILTER(?cleanStart >= ?endA && ?cleanEnd <= ?startB && ?cleanEnd >= ?cleanStart) } }''',
}
ADDITIONAL_CONTROLS={'C26':'''ASK { ?lotA i:requiresCleaningBetween ?lotB .
FILTER NOT EXISTS { ?lotA i:processedOn ?equipment ; t:endTimeSeconds ?endA .
?lotB i:processedOn ?equipment ; t:startTimeSeconds ?startB .
FILTER(isNumeric(?endA) && isNumeric(?startB) && ?endA <= ?startB) } }''',
'CCTX':'''ASK {
{ ?lot a i:Lot ; i:hasActivity ?operation .
FILTER NOT EXISTS { ?lot i:hasProcess ?process . ?operation i:partOfProcess ?process . } }
UNION { ?lot t:hasGHGContribution ?operation .
FILTER NOT EXISTS { ?lot a i:Lot ; i:hasActivity ?operation ; i:hasProcess ?process . ?operation i:partOfProcess ?process . } }
UNION { ?lot a i:Lot ; t:hasPlan ?plan . ?plan i:includesActivity ?operation .
FILTER NOT EXISTS { ?lot i:hasProcess ?process . ?operation i:partOfProcess ?process . } }
}'''}


# C030-C033: coupled thresholds, active maintenance and strict snapshot numerics.
from numeric_contract import (single_numeric, valid_output, complete_lot, scope,
 INPUT_INVALID, OUTPUT_INVALID, AMBIGUOUS, APPLICABLE, PREPARE, C26, R26,
 CLEAN_PAIR, CLEAN_RECORD)
VALIDATIONS['R26']=R26
ADDITIONAL_CONTROLS['C26']=C26
ADDITIONAL_CONTROLS['CNUM']=INPUT_INVALID

def guarded_operation_rows(contributions=False,by_process=False):
 link='?lot t:hasGHGContribution ?op .' if contributions else ''
 keys='?lot ?process ?op' if by_process else '?lot ?op'
 return '''{ SELECT '''+keys+''' (MIN(?inputEnergy) AS ?energy)
(MIN(?inputGHG) AS ?ghg) WHERE {
'''+scope()+ '\n'+link+'\n'+valid_output('?op','row','?inputEnergy','?inputGHG')+'\n'+complete_lot()+'''\n}
GROUP BY '''+keys+''' }'''

def replace_query(ident,body):
 title,competencies,_=QUERIES[ident];QUERIES[ident]=(title,competencies,body)

replace_query('Q01', '''SELECT ?process (SUM(?energy) AS ?energyKWh)
(SUM(?ghg) AS ?ghgKgCO2e) WHERE {
{ SELECT DISTINCT ?process ?op ?energy ?ghg WHERE {
'''+guarded_operation_rows(by_process=True)+''' } }
FILTER NOT EXISTS { ?badLot a i:Lot ; i:hasProcess ?process .
FILTER NOT EXISTS { '''+complete_lot('?badLot','processCheck')+''' } }
} GROUP BY ?process ORDER BY DESC(?ghgKgCO2e)''')
# The process-wide guard is expressed directly: any invalid matching operation
# suppresses a process total, rather than allowing a partial sum.
QUERIES['Q01']=(QUERIES['Q01'][0],QUERIES['Q01'][1],QUERIES['Q01'][2].replace(
 'FILTER NOT EXISTS { ?badLot a i:Lot ; i:hasProcess ?process .\nFILTER NOT EXISTS { '+complete_lot('?badLot','processCheck')+' } }',
 'FILTER NOT EXISTS { '+scope('?badLot','?process','?badOp')+'\nFILTER NOT EXISTS { '+valid_output('?badOp','processCheck','?badEnergy','?badGHG')+' } }'))
replace_query('Q03','''SELECT ?lot (SUM(?energy) AS ?energyKWh)
(SUM(?ghg) AS ?ghgKgCO2e) WHERE { '''+guarded_operation_rows()+'''
} GROUP BY ?lot ORDER BY ?lot''')
quantity=single_numeric('?lot','i:producesQuantity','?liters','volume',positive=True)
replace_query('Q06','''SELECT ?product ?lot ?liters (SUM(?ghg) AS ?ghgKgCO2e)
((SUM(?ghg) / ?liters) AS ?kgCO2ePerL) WHERE {
'''+guarded_operation_rows(True)+'''
?lot t:producesProduct ?product ; i:functionalUnit "L" .
'''+quantity+'''
FILTER NOT EXISTS { ?lot i:functionalUnit ?otherUnit . FILTER(STR(?otherUnit)!="L") }
} GROUP BY ?product ?lot ?liters ORDER BY ?lot''')
# Value-equivalent output volumes are collapsed in the grouping of Q06/Q17.
# MIN normalizes the representative volume before grouping, not afterward.
replace_query('Q06',QUERIES['Q06'][2].replace(quantity,
 '{ SELECT ?lot (MIN(?rawLiters) AS ?liters) WHERE { '+single_numeric('?lot','i:producesQuantity','?rawLiters','volume',positive=True)+' } GROUP BY ?lot }'))
replace_query('Q11','''SELECT ?lot ?ghgKgCO2e ?numNC WHERE {
{ SELECT ?lot (SUM(?ghg) AS ?ghgKgCO2e) WHERE {
'''+guarded_operation_rows(True)+''' } GROUP BY ?lot }
{ SELECT ?lot (COUNT(DISTINCT ?nc) AS ?numNC) WHERE {
?lot a i:Lot . OPTIONAL { ?lot i:hasNonConformity ?nc }
} GROUP BY ?lot } } ORDER BY ?lot''')
replace_query('Q17','''SELECT ?lot ?functionalUnit ?outputLiters
(SUM(?ghg)/?outputLiters AS ?impactPerFU) WHERE {
'''+guarded_operation_rows(True)+'''
?lot i:functionalUnit ?functionalUnit . FILTER(STR(?functionalUnit)="L")
FILTER NOT EXISTS { ?lot i:functionalUnit ?otherUnit . FILTER(STR(?otherUnit)!="L") }
{ SELECT ?lot (MIN(?rawLiters) AS ?outputLiters) WHERE {
'''+single_numeric('?lot','i:producesQuantity','?rawLiters','fuVolume',positive=True)+'''
} GROUP BY ?lot }
} GROUP BY ?lot ?functionalUnit ?outputLiters ORDER BY ?lot''')
replace_query('Q19','''SELECT ?lot (SUM(?ghg) AS ?ghgKgCO2e) WHERE {
'''+guarded_operation_rows(True)+'''
} GROUP BY ?lot ORDER BY DESC(?ghgKgCO2e)''')
replace_query('Q08','''SELECT ?operation ?stageName ?yieldPercent ?energyKWh
?ghgKgCO2e ?outputVolumeL (?ghgKgCO2e/?outputVolumeL AS ?kgCO2ePerL)
WHERE {
?operation a p:Manufacturing_Stage ; c:hasName ?stageName ;
t:yieldPercent ?yieldPercent .
{ SELECT ?operation (MIN(?rawEnergy) AS ?energyKWh)
(MIN(?rawGHG) AS ?ghgKgCO2e) (MIN(?rawVolume) AS ?outputVolumeL)
WHERE {
'''+valid_output('?operation','stage','?rawEnergy','?rawGHG')+'\n'+single_numeric('?operation','t:outputVolumeL','?rawVolume','stageVolume',positive=True)+'''
} GROUP BY ?operation }
} ORDER BY ?operation''')
replace_query('Q14','''SELECT DISTINCT ?lotA ?lotB ?equipment WHERE {
'''+CLEAN_PAIR+'\nFILTER NOT EXISTS { '+CLEAN_RECORD+''' }
} ORDER BY ?lotA ?lotB ?equipment''')

def U(value):
 p,name=value.split(':',1);return {'type':'uri','value':N[p]+name}
def L(value,typ='decimal'): return {'type':'literal','datatype':N['xsd']+typ,'value':str(value)}
def row(**values):return values
def save_expected(ident,rows,vars,configuration='integrated'):
 d=MODEL/'expected';d.mkdir(exist_ok=True)
 if configuration!='integrated':d=d/configuration;d.mkdir(exist_ok=True)
 (d/(ident+'.json')).write_text(json.dumps({'head':{'vars':vars},'results':{'bindings':rows}},ensure_ascii=False,indent=2)+'\n')

def configuration_expectations(integrated,scenario):
 """Authored projection matrix, independently reviewed without solver outputs.

 Empty rows are explicit fixture expectations, not general incapacity claims.
 Common rows are copied from the manually derived integrated tabular records.
 """
 common={'iso15531':['Q02','Q04','Q07','Q15','Q18','Q21'],
         'iso14040':['Q05','Q10','Q13','Q21'],
         'union':['Q02','Q04','Q05','Q07','Q10','Q13','Q15','Q18','Q20','Q21']}
 result={name:{ident:integrated[ident] if ident in same else [] for ident in QUERIES}
         for name,same in common.items()}
 result['iso15531']['Q20']=list(integrated['Q20'])+[
  row(entity=U('f:'+b['id']+'_Process'),kind=L('ManufacturingProcess','string'),missingProperty=U('e:computedForProcess'))
  for b in scenario['batches']]
 result['integrated']=integrated
 return result

def expectations(scenario):
 """Independent tabular derivation, never executes/exports a SPARQL query."""
 batches=scenario['batches'];materials=scenario['materials'];results={k:[] for k in QUERIES}
 # Authored by hand from the fixed functional fixture: assigned kW × minutes
 # /60, six decimal places. These are independent of generator energy/ghg
 # fields and of the SWRL/SPARQL implementations. Full table, not a sample.
 manual_energy={'BatchA':['0','10','75','26.666667','3.333333','0','0.333333'],
 'BatchB':['0','20','150','53.333333','6.666667','0','0.666667'],
 'BatchC':['0','10','75','26.666667','3.333333','0','0.333333'],
 'BatchZero':['0']*7}
 for b in batches:
  lot='f:'+b['id'];plan=lot+'_Plan';process=lot+'_Process';indicator=lot+'_Indicator'
  ops=b['operations'];energies=[Decimal(x) for x in manual_energy[b['id']]];contributions=[x*Decimal('.5') for x in energies];ghg=sum(contributions);energy=sum(energies)
  result=results
  result['Q01'].append(row(process=U(process),energyKWh=L(energy),ghgKgCO2e=L(ghg)))
  resources=sorted({r for o in ops for r in o['resources']}|{'f:Operator_01'})
  for r in resources:
   result['Q02'].append(row(lot=U(lot),plan=U(plan),product=U('f:WhitePaint'),resource=U(r)))
   result['Q04'].append(row(process=U(process),resource=U(r)))
   used=sum(o['duration'] for o in ops if r=='f:Operator_01' or r in o['resources'])
   v=row(plan=U(plan),resource=U(r),usedTimeMinutes=L(used))
   if r!='f:Operator_01':v['oeePercent']=L(82)
   result['Q16'].append(v)
  for r in ['f:'+m for m,v in materials.items() if v['recipe']>0]:
   result['Q02'].append(row(lot=U(lot),plan=U(plan),product=U('f:WhitePaint'),resource=U(r)))
   result['Q04'].append(row(process=U(process),resource=U(r)))
  result['Q03'].append(row(lot=U(lot),energyKWh=L(energy),ghgKgCO2e=L(ghg)))
  if Decimal(str(b['liters']))>0:
   intensity=ghg/Decimal(str(b['liters']))
   result['Q06'].append(row(product=U('f:WhitePaint'),lot=U(lot),liters=L(b['liters']),ghgKgCO2e=L(ghg),kgCO2ePerL=L(intensity)))
   result['Q17'].append(row(lot=U(lot),functionalUnit=L('L','string'),outputLiters=L(b['liters']),impactPerFU=L(intensity)))
  result['Q09'].append(row(factor=U('f:GridFactor'),version=L('2','string'),process=U(process),indicator=U(indicator),value=L(ghg)))
  result['Q11'].append(row(lot=U(lot),ghgKgCO2e=L(ghg),numNC=L(1 if b['status']=='REJECTED' else 0,'integer')))
  result['Q19'].append(row(lot=U(lot),ghgKgCO2e=L(ghg)))
  for idx,o in enumerate(ops):
   input_mass=Decimal(o['input']);output=Decimal(o['output']);loss=Decimal(o['loss']);residue=abs(input_mass-output-loss)
   status='INVALID_INPUT' if input_mass<=0 or output<0 or loss<0 else ('OK' if residue<=Decimal('.01')*input_mass else 'REVISAR')
   result['Q07'].append(row(operation=U(o['id']),inputMassKg=L(input_mass),outputMassKg=L(output),registeredLossKg=L(loss),residueKg=L(residue),status=L(status,'string')))
   volume=output/Decimal('1.25')
   if input_mass>0 and volume>0:
    result['Q08'].append(row(operation=U(o['id']),stageName=L(o['stage'],'string'),yieldPercent=L(output/input_mass*100),energyKWh=L(energies[idx]),ghgKgCO2e=L(contributions[idx]),outputVolumeL=L(volume),kgCO2ePerL=L(contributions[idx]/volume)))
   for r in sorted(set(o['resources'])|{'f:Operator_01'}):result['Q12'].append(row(indicator=U(indicator),process=U(process),operation=U(o['id']),resource=U(r),value=L(ghg)))
   result['Q18'].append(row(plan=U(plan),activity=U(o['id']),stageIndex=L(o['index'],'integer'),stageName=L(o['stage'],'string'),lifecycleStage=L('Producción','string'),totalDurationMinutes=L(sum(p['duration'] for p in ops))))
  for mat,v in materials.items():
   if v['recipe']>0:result['Q13'].append(row(process=U(process),flow=U(lot+'_Flow_'+mat),material=U('f:'+mat),quantityKg=L(Decimal(str(b['input']))*Decimal(str(v['recipe']))),unit=L('kg','string')))
 for name,v in materials.items():
  if v['factor']>3 or v['water']>100:results['Q05'].append(row(material=U('f:'+name),carbonFactor=L(v['factor']),waterFactor=L(v['water']),exceedsDeclaredCarbonThreshold=L(str(v['factor']>5).lower(),'boolean')))
  results['Q10'].append(row(supplier=U('f:'+v['supplier']),material=U('f:'+name),factor=U('f:'+name+'_Factor'),value=L(v['factor'])))
 results['Q14']=[row(lotA=U('f:BatchB'),lotB=U('f:BatchC'),equipment=U('f:PearlMill_01'))]
 results['Q15']=[row(clean=U('f:'+n),waterL=L(5)) for n in ['ValidCleaning','WrongEquipmentCleaning','HistoricalCleaning']]
 results['Q20']=[row(entity=U('f:MissingProcess'),kind=L('ManufacturingProcess','string'),missingProperty=U('e:computedForProcess')),row(entity=U('f:MissingOperation'),kind=L('ProcessOperation','string'),missingProperty=U('i:hasInput')),row(entity=U('f:MissingQC'),kind=L('ProcessOperation','string'),missingProperty=U('i:hasInput')),row(entity=U('f:MissingQC'),kind=L('QualityControl','string'),missingProperty=U('i:hasMeasurement'))]
 results['Q21']=[row(entity=U('f:GridFactor'),currentVersion=L('2','string'),priorVersion=L('1','string'))]
 return results

def main():
 queries_dir=MODEL/'queries';queries_dir.mkdir(exist_ok=True)
 validation_dir=MODEL/'validations';validation_dir.mkdir(exist_ok=True)
 scenario=json.loads((DATA/'functional-scenario.json').read_text());expected=expectations(scenario)
 expected_configurations=configuration_expectations(expected,scenario)
 variables={
 'Q01':'process energyKWh ghgKgCO2e','Q02':'lot plan product resource','Q03':'lot energyKWh ghgKgCO2e','Q04':'process resource','Q05':'material carbonFactor waterFactor exceedsDeclaredCarbonThreshold','Q06':'product lot liters ghgKgCO2e kgCO2ePerL','Q07':'operation inputMassKg outputMassKg registeredLossKg residueKg status','Q08':'operation stageName yieldPercent energyKWh ghgKgCO2e outputVolumeL kgCO2ePerL','Q09':'factor version process indicator value','Q10':'supplier material factor value','Q11':'lot ghgKgCO2e numNC','Q12':'indicator process operation resource value','Q13':'process flow material quantityKg unit','Q14':'lotA lotB equipment','Q15':'clean waterL','Q16':'plan resource usedTimeMinutes oeePercent','Q17':'lot functionalUnit outputLiters impactPerFU','Q18':'plan activity stageIndex stageName lifecycleStage totalDurationMinutes','Q19':'lot ghgKgCO2e','Q20':'entity kind missingProperty','Q21':'entity currentVersion priorVersion'}
 catalog={'schema_version':'1.0','boundary':'Electricity consumed by production operations only, factors declared as scenario assumptions; no upstream manufacturing footprint or industrial validation','queries':[],'validations':[],'configurations':[]}
 annex=[]
 for ident,(title,ps,body) in QUERIES.items():
  path=queries_dir/(ident+'.sparql');path.write_text(PREFIX+'# '+ident+' '+title+'\n'+body+'\n')
  expected_paths={}
  for configuration,table in expected_configurations.items():
   save_expected(ident,table[ident],variables[ident].split(),configuration)
   expected_paths[configuration]='research/model/expected/'+('' if configuration=='integrated' else configuration+'/')+ident+'.json'
  catalog['queries'].append({'id':ident,'title':title,'path':str(path.relative_to(ROOT)),'competency_questions':ps,'expected_path':expected_paths['integrated'],'expected_paths':expected_paths,'inputs':['research/data/functional.ttl'],'units':{'energy':'kWh','GHG':'kg CO2e','volume':'L','duration':'min','mass':'kg'},'output_variables':variables[ident].split(),'test':'Independent fixture-table expectation for each source projection; compare all bindings as multiset, including explicitly expected empty outputs; numeric tolerance1e-9','scope':'formalized production stage only'})
  annex.append('# --- '+ident+'. '+title+'\n'+PREFIX+body)
 for ident,body in VALIDATIONS.items():
  path=validation_dir/(ident+'.sparql');path.write_text(PREFIX+body+'\n');catalog['validations'].append({'id':ident,'path':str(path.relative_to(ROOT)),'expected':True,'meaning':'true = at least one missing required record in the closed snapshot; not physical contamination or OWL inconsistency'})
 catalog['additional_controls']=[]
 for ident,body in ADDITIONAL_CONTROLS.items():
  path=validation_dir/(('CNUM-input-invalid.sparql') if ident=='CNUM' else ident+'-context-completeness.sparql');path.write_text(PREFIX+body+'\n')
  catalog['additional_controls'].append({'id':ident,'path':str(path.relative_to(ROOT)),'scope':('Additional required-pair context check, outside the four canonical ASK; true means cleaning context not evaluable, never OWL inconsistency or observed contamination.' if ident=='C26' else 'Additional lot-operation-process and plan context integrity control, outside the four canonical ASK. True means a declared activity, contribution or plan activity lacks a matching manufacturing process; not OWL inconsistency.')})
 numeric_paths={'applicable':'CNUM-applicable.sparql','ambiguous_input':'CNUM-input-ambiguous.sparql','invalid_output':'CNUM-output-invalid.sparql'}
 for name,body in [('applicable',APPLICABLE),('ambiguous_input',AMBIGUOUS),('invalid_output',OUTPUT_INVALID)]:
  (validation_dir/numeric_paths[name]).write_text(PREFIX+body+'\n')
 numeric=next(item for item in catalog['additional_controls'] if item['id']=='CNUM')
 numeric.update(kind='snapshot numeric contract',states=['VALID','AMBIGUOUS_INPUT','NOT_EVALUABLE','NOT_APPLICABLE'],applicable_configurations=['integrated','h3_extended'],stage_paths={key:'research/model/validations/'+value for key,value in numeric_paths.items()},scope='One numeric energy and one selected factor IRI per matching lot/process operation. Value-equivalent literals count once. Contradictions block numeric inference; missing data is separately not evaluable. Foreign links remain visible through CCTX and are excluded from the numeric scope. Totals require every selected same-process operation, never a partial sum.')
 (validation_dir/'CNUM-materialize.sparql').write_text(PREFIX+PREPARE+'\n')
 construct=PREFIX+'CONSTRUCT { ?process a c:MissingEnvironmentalData . } WHERE { ?process a i:ManufacturingProcess . FILTER NOT EXISTS { ?process t:hasInventoryRepresentation ?lci . ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:indicatorValue ?value . } }\n'
 (validation_dir/'R07-materialize.sparql').write_text(construct)
 catalog['pre_inference_constructs']=['research/model/validations/R07-materialize.sparql','research/model/validations/CNUM-materialize.sparql']
 common=['ontologies/'+p for p in ['core-concepts.owl','product-module.owl','process-module.owl','resource-module.owl','enterprise-module.owl']]+['research/model/research-profile.owl']
 manufacturing=['ontologies/iso15531-module.owl','research/model/manufacturing-profile.owl']
 environmental=['ontologies/iso14040-module.owl','research/model/environmental-profile.owl']
 union=common+manufacturing+environmental+['ontologies/paint-case-extension.owl']
 enabled=[f'R{i:02}' for i in range(1,27) if i not in [7,16,19,26]]
 configs=[('iso15531',common+manufacturing,[],[],['research/data/functional-manufacturing.ttl'],['research/data/load-medium-manufacturing.ttl']),('iso14040',common+environmental,[],[],['research/data/functional-environmental.ttl'],['research/data/load-medium-environmental.ttl']),('union',union,[],[],['research/data/functional-union.ttl'],['research/data/load-medium-union.ttl']),('integrated',union,['ontologies/alignment-rules.owl'],enabled,['research/data/functional.ttl'],['research/data/load-medium.ttl']),('h3_extended',union+['ontologies/iso10303-ap242-fragment.owl'],['ontologies/alignment-rules.owl','ontologies/iso10303-alignment-rules.owl'],enabled+['R27','R28','R29'],['research/data/functional.ttl','research/data/automotive.ttl'],[])]
 for ident,tbox,rulepaths,ruleids,abox,load in configs:
  item={'id':ident,'tbox_paths':tbox,'abox_paths':abox,'rules_paths':rulepaths,'enabled_rule_ids':ruleids,'query_ids':list(QUERIES),'validation_ids':list(VALIDATIONS) if ident in ['integrated','h3_extended'] else [],'expected_results_configuration':'integrated' if ident=='h3_extended' else ident,'numeric_controls_enabled':ident in ['integrated','h3_extended'],'run_pre_inference_constructs':ident in ['integrated','h3_extended'],'skip_golden':False,'benchmark':ident!='h3_extended','benchmark_abox':load,'enabled_rules':ruleids if ruleids else 'NONE','comparison_scope':'Standard-specific vocabularies and projected records, their union without alignment, and integrated union with seven contextual correspondences, 22 SWRL and four ASK; zero universal cross-standard class equivalences. All21 queries are executed and compared with independently authored per-configuration expectations; response presence is fixture-specific, not a general completeness proof or speed superiority.'}
  catalog['configurations'].append(item)
 catalog['competency_questions']={'P1':'Recursos requeridos por un proceso X','P2':'Recursos para ejecutar un plan de proceso X','P3':'Productos que usan un recurso X','P4':'Actividades de un plan de proceso X','P5':'Producto generado por un plan de proceso X'}
 catalog['rule_count']={'base_SWRL':22,'base_ASK':4,'extension_SWRL':3,'policy':'count parsed rules, not labels or RDF annotations'}
 catalog['expected_derivation']={'source':'research/data/functional-scenario.json','method':'Independent table traversal and arithmetic; no execution outputs are used as expected values','worked_examples':{'BatchA_ghg_kgCO2e':'(10+75+26.666667+3.333333+0.333333)*0.5=57.6666665','BatchA_kgCO2e_per_L':'57.6666665/98.4','BatchA_Op2_intensity':'10kWh*0.5kgCO2e/kWh/(125kg/1.25kg/L)=0.05kgCO2e/L','BatchA_Op2_subloads':'100L total;2 sequential subloads50L,10min each;sum20min*30kW/60=10kWh','BatchB_mass_boundary':'abs(250-244-3.5)=2.5;2.5/250=1%;OK','BatchC_mass_above':'abs(125-121.74-2)=1.26;1.26/125=1.008%;REVISAR','Coalescent_handling':'4.2 >5 is false','zero_volume':'excluded fromQ06,Q08,Q17; unit must be L in Q06/Q17; no invented zero or infinite intensity'}}
 matrix={'method':'Independently reviewed source projection rules and SELECT premises, without solver outputs. Shared rows come from manually authored integrated fixture table. ISO15531 Q20 additionally exposes missing environmental records for four named batch processes.',
         'scope':'Explicit empty outputs apply to these projected records and inactive alignment rules, not universal inability of standards. H3 retains the integrated21expectations.',
         'sources':['research/data/generate_dataset.py','research/data/functional-scenario.json','research/model/build_catalog.py'],
         'expected_row_counts':{configuration:{ident:len(rows) for ident,rows in table.items()} for configuration,table in expected_configurations.items()}}
 (MODEL/'configuration-expectation-matrix.json').write_text(json.dumps(matrix,ensure_ascii=False,indent=2)+'\n')
 catalog['expected_derivation']['configuration_matrix']='research/model/configuration-expectation-matrix.json'
 (ROOT/'research/catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
 (ROOT/'ontologies/annexB-queries.sparql').write_text('\n\n'.join(annex)+'\n')
 (ROOT/'ontologies/alignment-queries.sparql').write_text('\n\n'.join('# '+ident+'\n'+PREFIX+body for ident,body in VALIDATIONS.items())+'\n\n# R07 materialization forR24\n'+construct)
 print('21 queries, 4 ASK and independent golden outputs written')

if __name__=='__main__':main()
