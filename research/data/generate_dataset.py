#!/usr/bin/env python3
"""Portable, deterministic test scenario derived from the thesis generator.

The original generator is preserved outside this repository. Its seed, seven
stages and 50 L/30 kW mill are retained; fixed quality percentages are NOT used
to manufacture evaluation outcomes. Expected outputs are derived from declared
fixture records independently of the SPARQL or SWRL execution engines.
"""
import argparse
import csv
import hashlib
import json
import random
from math import ceil
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
NS = {
 'i':'http://w3id.org/protys/ontology/iso15531#',
 'e':'http://w3id.org/protys/ontology/iso14040#',
 'c':'http://w3id.org/protys/ontology/core#',
 'a':'http://w3id.org/protys/ontology/alignment#',
 't':'http://w3id.org/protys/ontology/research#',
 'p':'http://w3id.org/protys/ontology/paint-ext#',
 'r':'http://w3id.org/protys/ontology/resource#',
 'prod':'http://w3id.org/protys/ontology/product#',
 'f':'http://w3id.org/protys/fixture#',
 'ap':'http://w3id.org/protys/ontology/iso10303#',
 'owl':'http://www.w3.org/2002/07/owl#',
 'xsd':'http://www.w3.org/2001/XMLSchema#',
}
STAGES = ['Recepción e inspección','Dispersión de pigmentos','Mezcla principal',
 'Ajuste de viscosidad','Filtrado','Control de calidad','Envasado']
DURATIONS = [10,20,30,10,10,20,10]
EQUIPMENT = {
 'PearlMill_01': {'capacityLiters':50,'powerKW':30,'role':'FineGrinding'},
 'MixingReactor_02': {'capacityLiters':5000,'powerKW':150,'role':'MainMixing'},
 'DosingSystem_03': {'capacityLiters':1000,'powerKW':10,'role':'ViscosityAdjustment'},
 'FilterSystem_04': {'capacityLiters':5000,'powerKW':20,'role':'Filtering'},
 'PackagingMachine_05': {'capacityUnitsPerMinute':30,'powerKW':2,'role':'Packaging'},
}
STAGE_EQUIPMENT = [[],['PearlMill_01'],['MixingReactor_02'],
 ['MixingReactor_02','DosingSystem_03'],['FilterSystem_04'],[],['PackagingMachine_05']]
MATERIALS = {
 'TitaniumDioxide':{'factor':5.6,'water':40,'supplier':'Supplier_A','recipe':.2},
 'AcrylicResin':{'factor':3.8,'water':127,'supplier':'Supplier_B','recipe':.3},
 'Water':{'factor':0,'water':1,'supplier':'Supplier_A','recipe':.49},
 'Biocide':{'factor':3.5,'water':15,'supplier':'Supplier_B','recipe':.01},
 'Coalescent':{'factor':4.2,'water':10,'supplier':'Supplier_B','recipe':0},
 'ThresholdMaterial':{'factor':5,'water':50,'supplier':'Supplier_A','recipe':0},
}

def dec(v): return Decimal(str(v))
def number(v): return format(dec(v).quantize(Decimal('.000001'),rounding=ROUND_HALF_UP),'f').rstrip('0').rstrip('.') or '0'
def iri(n):
 prefix,name=n.split(':',1);return NS[prefix]+name
def ref(n): return ('uri',n)
def lit(v,kind='decimal'): return ('literal',str(v),kind)

class Turtle:
 def __init__(self): self.lines=[];self.individuals=set();self.triples=0
 def node(self,name,types=()):
  self.individuals.add(name)
  self.add(name,'a',ref('owl:NamedIndividual'))
  for typ in types:self.add(name,'a',ref(typ))
 def add(self,s,p,obj):
  self.triples+=1
  if obj[0]=='uri':v=obj[1]
  else:
   v=json.dumps(obj[1],ensure_ascii=False)+'^^xsd:'+obj[2]
  self.lines.append(s+' '+p+' '+v+' .')
 def write(self,path):
  path.parent.mkdir(parents=True,exist_ok=True)
  prefix='\n'.join('@prefix '+p+': <'+v+'> .' for p,v in NS.items())
  path.write_text(prefix+'\n\n'+'\n'.join(self.lines)+'\n')
  return {'path':str(path.relative_to(ROOT)),'individuals':len(self.individuals),
   'asserted_triples':len(set(self.lines)),'serialized_statements':self.triples,
   'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
 def projection(self,domain):
  """Project source records; do not include the other standard's vocabulary."""
  projected=Turtle();kept=[]
  for line in self.lines:
   subject,predicate,obj=line[:-2].split(' ',2)
   if subject.startswith('a:'):continue
   if predicate=='a' and obj=='owl:NamedIndividual':continue
   if predicate.startswith('a:') or (predicate=='a' and obj.startswith('a:')):continue
   if domain=='manufacturing':
    if predicate.startswith(('e:','p:')) or (predicate=='a' and obj.startswith(('e:','p:'))):continue
    if predicate=='a' and obj=='t:EnvironmentalEnergyRecord':continue
    if predicate.startswith('t:') and predicate in ['t:materialFactorKgCO2ePerKg','t:waterFootprintLPerKg','t:providedBy']:continue
   elif domain=='environmental':
    if predicate.startswith(('i:','p:')) or (predicate=='a' and obj.startswith(('i:','p:','c:'))):continue
    if predicate.startswith('t:') and predicate not in ['t:materialFactorKgCO2ePerKg','t:waterFootprintLPerKg','t:providedBy','t:belongsToProcess','t:materialQuantityKg','t:unit','t:scenarioRole','t:hasEmissionFactor','t:usesEmissionFactor','t:factorValue','t:factorUnit','t:hasThreshold','t:indicatorCategory','t:thresholdValue','t:thresholdUnit','t:thresholdCategory']:continue
    if predicate=='a' and obj in ['t:FineGrindingOperation','t:RatioObservation','t:Measurement','t:Subload']:continue
   kept.append(line)
  active={l.split(' ',1)[0] for l in kept}
  for line in kept:
   obj=line[:-2].split(' ',2)[2]
   if not obj.startswith('"') and obj in self.individuals:active.add(obj)
  projected.lines=[s+' a owl:NamedIndividual .' for s in sorted(active)]+kept
  projected.individuals=active;projected.triples=len(projected.lines)
  return projected

def global_records(g):
 g.node('f:ElectricityGHGThreshold',['t:ThresholdRecord'])
 for pred,value,kind in [('t:thresholdValue',5,'decimal'),('t:thresholdUnit','kg CO2e','string'),('t:thresholdCategory','GHG_ELECTRICITY_PRODUCTION','string')]:g.add('f:ElectricityGHGThreshold',pred,lit(value,kind))
 g.add('f:ElectricityGHGThreshold','t:scenarioRole',lit('Umbral hipotético del escenario, no límite normativo ni industrial','string'))
 g.node('a:FineGrinding',['i:ManufacturingCapability'])
 g.node('c:WaterFlow',['e:ProductFlow']);g.add('c:WaterFlow','t:scenarioRole',lit('Presencia de suministro comprado de agua; no extracción directa de naturaleza ni suma cuantitativa','string'))
 g.node('f:AssessedFacility',['i:ManufacturingFacility']);g.node('f:ElectricityOnlyBoundary',['e:SystemBoundary'])
 g.add('f:AssessedFacility','t:facilityInBoundary',ref('f:ElectricityOnlyBoundary'));g.add('f:AssessedFacility','owl:differentFrom',ref('f:ElectricityOnlyBoundary'))
 g.add('f:ElectricityOnlyBoundary','t:scenarioRole',lit('Alcance de producción: emisiones asociadas a electricidad; excluye fabricación de materias primas y otras fuentes sin datos','string'))
 g.node('f:WhitePaint',['p:Final_Product','c:Product']);g.add('f:WhitePaint','c:hasName',lit('Pintura blanca','string'))
 for name,v in EQUIPMENT.items():
  g.node('f:'+name,['i:EquipmentResource','r:Equip'])
  for key in ['powerKW','capacityLiters','capacityUnitsPerMinute']:
   if key in v:g.add('f:'+name,'t:'+key,lit(v[key]))
  g.add('f:'+name,'t:availabilityPercent',lit(90));g.add('f:'+name,'t:oeePercent',lit(82))
 g.node('f:PearlMill_01',['c:BottleneckResource'])
 g.add('f:PearlMill_01','t:scenarioRole',lit('Entrada explícita de cuello de botella del escenario; no resultado de medición industrial','string'))
 g.node('f:Operator_01',['i:HumanResource']);g.add('f:Operator_01','i:skillCode',lit('PAINT_OPERATOR','string'))
 for supplier in ['Supplier_A','Supplier_B']:g.node('f:'+supplier,['t:Supplier'])
 for name,v in MATERIALS.items():
  m='f:'+name;factor=m+'_Factor';g.node(m,['r:Raw_Material'])
  g.add(m,'t:materialFactorKgCO2ePerKg',lit(v['factor']));g.add(m,'t:waterFootprintLPerKg',lit(v['water']))
  g.add(m,'t:providedBy',ref('f:'+v['supplier']));g.add(m,'t:hasEmissionFactor',ref(factor))
  g.node(factor,['t:MaterialEmissionFactor']);g.add(factor,'t:factorValue',lit(v['factor']));g.add(factor,'t:factorUnit',lit('kg CO2e/kg','string'));g.add(factor,'t:scenarioRole',lit('Intensidad hipotética de material, sólo prueba funcional y excluida de Q06; no factor LCIA','string'))
 g.node('f:GridFactor',['t:ElectricityEmissionFactor']);g.add('f:GridFactor','t:factorValue',lit(.5))
 g.add('f:GridFactor','t:factorUnit',lit('kg CO2e/kWh','string'))
 g.add('f:GridFactor','c:hasVersion',lit('2','string'));g.add('f:GridFactor','c:hasPriorVersion',lit('1','string'))
 g.add('f:GridFactor','c:modifiedInVersion',lit('2','string'))
 g.add('f:GridFactor','t:scenarioRole',lit('Factor declarado del escenario, no factor medido de planta','string'))

def add_batch(g,record):
 name=record['id'];lot='f:'+name;process=lot+'_Process';order=lot+'_Order';plan=lot+'_Plan';indicator=lot+'_Indicator';lci=lot+'_LCIProcess';inventory=lot+'_Inventory';quality=lot+'_QualityRecord'
 # Indicator is a separately recorded, generated input with declared
 # provenance. SWRL computes operation contributions; queries sum them and
 # tests check equality with this independent process record.
 energies=[dec(number(dec(sum(EQUIPMENT[e]['powerKW'] for e in eq))*dec(minutes*record['scale'])/60)) for eq,minutes in zip(STAGE_EQUIPMENT,DURATIONS)]
 if record['liters']==0:energies=[dec(0)]*7
 record['indicator']=str(sum(energies)*dec('.5'))
 g.node(lot,['i:Lot','p:ProductBatch'])
 if record['status'] in ['REJECTED','INVALID_TEST_RECORD']:
  g.add(lot,'t:scenarioRole',lit('Registro deliberadamente anómalo para pruebas negativas; no secuencia productiva válida','string'))
 for pred,value in [('i:hasProcess',process),('t:hasPlan',plan),('t:producesProduct','f:WhitePaint'),('i:processedOn','f:PearlMill_01')]:g.add(lot,pred,ref(value))
 for pred,value,kind in [('i:productFamily',record['family'],'string'),('i:hasQualityStatus',record['status'],'string'),('i:functionalUnit','L','string'),('i:producesQuantity',record['liters'],'decimal'),('t:startTimeSeconds',record['start'],'decimal'),('t:endTimeSeconds',record['start']+6600*record['scale'],'decimal')]:g.add(lot,pred,lit(value,kind))
 g.add(lot,'t:productFamilyCode',lit(1 if record['family']=='White_Matte' else 2,'integer'))
 g.node(order,['i:WorkOrder']);g.add(order,'i:producesLot',ref(lot));g.add(order,'i:executedBy',ref('f:Operator_01'))
 g.node(process,['i:ManufacturingProcess']);g.node(lci,['e:UnitProcess']);g.add(process,'t:hasInventoryRepresentation',ref(lci));g.add(process,'owl:differentFrom',ref(lci))
 g.add(lci,'t:scenarioRole',lit('Representación de inventario parcial tipo black-box del proceso productivo; instancia distinta de ejecución manufacturera','string'))
 g.node(plan,['i:ProcessPlan']);g.add(plan,'t:planProduces',ref('f:WhitePaint'));g.add(plan,'t:lifecycleStage',lit('Producción','string'))
 g.node(inventory,['e:LifeCycleInventory']);g.add(inventory,'t:inventoryForPlan',ref(plan));g.add(inventory,'t:inventoryForProcess',ref(lci));g.add(inventory,'owl:differentFrom',ref(plan));g.add(inventory,'t:scenarioRole',lit('Registro parcial de inventario de producción; no inventario completo del ciclo de vida ni plan','string'))
 g.node(quality,['i:QualityAttribute']);g.add(quality,'t:qualityForLot',ref(lot));g.add(quality,'t:qualityStatus',lit(record['status'],'string'));g.add(quality,'owl:differentFrom',ref(indicator))
 for equip in EQUIPMENT:g.add(plan,'i:assignsResource',ref('f:'+equip))
 g.add(plan,'i:assignsResource',ref('f:Operator_01'))
 g.node(indicator,['e:ImpactIndicator']);g.add(indicator,'e:computedForProcess',ref(lci));g.add(indicator,'t:usesEmissionFactor',ref('f:GridFactor'));g.add(indicator,'t:indicatorForLot',ref(lot))
 g.add(indicator,'t:indicatorCategory',lit('GHG_ELECTRICITY_PRODUCTION','string'));g.add(indicator,'t:hasThreshold',ref('f:ElectricityGHGThreshold'))
 for pred in ['e:indicatorValue','e:hasIndicatorValue']:g.add(indicator,pred,lit(record['indicator']))
 g.add(lci,'e:hasImpact',ref(indicator));g.add(indicator,'t:unit',lit('kg CO2e','string'));g.add(indicator,'t:scenarioRole',lit('Valor fuente calculado por generador como suma eléctrica de operaciones; cotejado con agregaciónSPARQL de contribucionesSWRL, no suma inferida porSWRL','string'))
 if record['status']=='REJECTED':
  g.node(lot+'_NC',['i:QualityAttribute']);g.add(lot,'i:hasNonConformity',ref(lot+'_NC'))
 ops=[]
 for index,stage in enumerate(STAGES,1):
  op=lot+'_Op'+str(index);duration=DURATIONS[index-1]*record['scale']
  types=['i:ProcessOperation','p:Manufacturing_Stage','t:EnvironmentalEnergyRecord']
  if index==2:types.append('t:FineGrindingOperation')
  if index==6:types+=['i:Quality_Control_Activity','p:QualityControl']
  g.node(op,types);g.add(lot,'i:hasActivity',ref(op));g.add(plan,'i:includesActivity',ref(op));g.add(order,'i:executesActivity',ref(op));g.add(op,'i:partOfProcess',ref(process))
  g.add(op,'i:requiresResource',ref('f:Operator_01'))
  for pred,value,kind in [('c:hasName',stage,'string'),('t:stageIndex',index,'integer'),('i:duration',duration,'decimal'),('i:cycleTime',duration,'decimal'),('p:durationMinutes',duration,'decimal')]:g.add(op,pred,lit(value,kind))
  for equip in STAGE_EQUIPMENT[index-1]:g.add(op,'i:requiresResource',ref('f:'+equip));g.add(op,'t:hasStageResource',ref('f:'+equip))
  power=sum(EQUIPMENT[e]['powerKW'] for e in STAGE_EQUIPMENT[index-1])
  energy=dec(number(dec(power)*dec(duration)/60))
  if record['liters']==0:energy=dec(0)
  g.add(op,'i:consumesEnergyKWh',lit(number(energy)));g.add(op,'t:hasEmissionFactor',ref('f:GridFactor'))
  g.add(op,'e:energyInputKWh',lit(number(energy)))
  g.add(op,'i:consumesWaterL',lit(1))
  input_mass=dec(record['input']);output_mass=input_mass if index<7 else dec(record['output']);loss=dec(0) if index<7 else dec(record['loss'])
  output_volume=output_mass/dec('1.25');mass_yield=output_mass/input_mass*100 if input_mass>0 else None
  g.add(op,'t:densityKgPerL',lit('1.25'));g.add(op,'t:outputVolumeL',lit(number(output_volume)))
  if mass_yield is not None:
   g.add(op,'i:yield',lit(number(mass_yield)));g.add(op,'t:yieldPercent',lit(number(mass_yield)))
  for pred,value in [('t:inputMassKg',input_mass),('t:outputMassKg',output_mass),('t:registeredLossKg',loss)]:g.add(op,pred,lit(number(value)))
  for mat,v in MATERIALS.items():
   if v['recipe']>0:g.add(op,'i:hasInput',ref('f:'+mat));g.add(op,'i:consumesMaterial',ref('f:'+mat))
  ratio=op+'_Ratio';g.node(ratio,['t:RatioObservation']);g.add(ratio,'t:forOperation',ref(op));g.add(ratio,'t:forIndicator',ref(indicator))
  if index==6:
   meas=op+'_Measurement';g.node(meas,['t:Measurement']);g.add(op,'i:hasMeasurement',ref(meas))
  if index<7:g.add(op,'c:precedes',ref(lot+'_Op'+str(index+1)))
  subloads=[]
  if index==2 and input_mass>0:
   total_volume=input_mass/dec('1.25');count=ceil(total_volume/50);remaining=total_volume
   stage_start=dec(record['start'])+dec(DURATIONS[0]*record['scale'])*60
   per_time=(dec(duration)/count).quantize(Decimal('.000001'),rounding=ROUND_HALF_UP)
   elapsed=dec(0)
   for j in range(count):
    sub=op+'_Subload'+str(j+1);volume=min(dec(50),remaining);remaining-=volume
    subduration=per_time if j<count-1 else dec(duration)-elapsed
    start=stage_start+elapsed*60;elapsed+=subduration;end=stage_start+elapsed*60
    g.node(sub,['t:Subload']);g.add(sub,'t:subloadOfOperation',ref(op))
    for pred,val in [('t:subloadVolumeL',volume),('t:subloadDurationMinutes',subduration),('t:startTimeSeconds',start),('t:endTimeSeconds',end)]:g.add(sub,pred,lit(number(val)))
    subloads.append({'id':sub,'volumeL':str(volume),'durationMinutes':str(subduration),'startSeconds':str(start),'endSeconds':str(end)})
  ops.append({'id':op,'stage':stage,'index':index,'duration':duration,'energy':str(energy), 'ghg':str(energy*dec('.5')),'resources':['f:'+e for e in STAGE_EQUIPMENT[index-1]],'input':str(input_mass),'output':str(output_mass),'loss':str(loss),'outputVolumeL':str(output_volume),'yieldPercent':str(mass_yield) if mass_yield is not None else None,'subloads':subloads})
 for mat,v in MATERIALS.items():
  if v['recipe']<=0:continue
  flow=lot+'_Flow_'+mat;g.node(flow,['e:ProductFlow']);g.add(lci,'e:hasInventoryFlow',ref(flow));g.add(flow,'t:belongsToProcess',ref(process));g.add(flow,'e:refersToMaterial',ref('f:'+mat));g.add(flow,'t:flowRefersToProduct',ref('f:'+mat));g.add(flow,'t:inputFlowForResource',ref('f:'+mat));g.add(flow,'owl:differentFrom',ref('f:'+mat));g.add(flow,'t:materialQuantityKg',lit(number(dec(record['input'])*dec(v['recipe']))));g.add(flow,'t:unit',lit('kg','string'))
 # Two contextual events; numeric seconds use one declared scenario origin.
 for suffix,t in [('Start',record['start']),('End',record['start']+6600*record['scale'])]:
  event=lot+'_Event'+suffix;g.node(event,['i:ManufacturingData']);g.add(event,'t:forLot',ref(lot));g.add(event,'t:eventForProcess',ref(process));g.add(event,'t:eventTimeSeconds',lit(t))
 record['operations']=ops
 return record

def functional():
 records=[
  {'id':'BatchA','family':'White_Matte','status':'APPROVED','input':125,'output':123,'loss':2,'liters':98.4,'start':0,'scale':1,'indicator':10},
  {'id':'BatchB','family':'White_Satin','status':'APPROVED','input':250,'output':244,'loss':3.5,'liters':195.2,'start':20000,'scale':2,'indicator':20},
  {'id':'BatchC','family':'White_Matte','status':'REJECTED','input':125,'output':121.74,'loss':2,'liters':97.392,'start':40000,'scale':1,'indicator':10},
  {'id':'BatchZero','family':'White_Matte','status':'INVALID_TEST_RECORD','input':0,'output':0,'loss':0,'liters':0,'start':60000,'scale':1,'indicator':0},
 ]
 g=Turtle();global_records(g)
 for record in records:add_batch(g,record)
 g.add('f:BatchA','t:nextLot',ref('f:BatchB'));g.add('f:BatchB','t:nextLot',ref('f:BatchC'))
 # Valid A->B cleaning; B->C has only a wrong-equipment and historical cleaning.
 for name,equip,start,end in [('ValidCleaning','PearlMill_01',7000,8000),('WrongEquipmentCleaning','FilterSystem_04',35000,36000),('HistoricalCleaning','PearlMill_01',100,200)]:
  g.node('f:'+name,['i:CleaningOperation']);g.add('f:'+name,'t:cleanedResource',ref('f:'+equip));g.add('f:'+name,'t:startTimeSeconds',lit(start));g.add('f:'+name,'t:endTimeSeconds',lit(end));g.add('f:'+name,'i:consumesWaterL',lit(5))
  g.add('f:'+name,'i:hasInput',ref('f:Water'))
 # Deliberately incomplete records test completeness, not ontology inconsistency.
 g.node('f:MissingProcess',['i:ManufacturingProcess'])
 g.node('f:MissingOperation',['i:ProcessOperation'])
 g.node('f:MissingQC',['i:ProcessOperation','i:Quality_Control_Activity'])
 g.node('f:UnrelatedBottleneck',['i:EquipmentResource','c:BottleneckResource'])
 g.add('f:UnrelatedBottleneck','t:powerKW',lit(1));g.add('f:UnrelatedBottleneck','t:availabilityPercent',lit(90));g.add('f:UnrelatedBottleneck','t:oeePercent',lit(80))
 return g,records

def load_scenario(lots,seed):
 rng=random.Random(seed);g=Turtle();global_records(g);records=[]
 for n in range(lots):
  mass=rng.randint(100,200);loss=dec(mass)*dec('.02');output=dec(mass)-loss
  record={'id':'Load'+str(n).zfill(5),'family':'White_Matte','status':'APPROVED','input':mass,'output':str(output),'loss':str(loss),'liters':str(output/dec('1.25')),'start':n*10000,'scale':1,'indicator':10}
  add_batch(g,record);records.append(record)
 return g,records

def automotive():
 g=Turtle();g.node('f:AlternatorAssembly',['ap:Product_Definition','t:PilotProductRecord']);g.node('f:AssemblyShape',['ap:Shape_Representation'])
 g.add('f:AlternatorAssembly','ap:hasShapeRepresentation',ref('f:AssemblyShape'))
 for n in range(47):
  name='f:Component'+str(n).zfill(2);g.node(name,['ap:Configuration_Item','t:PilotComponentRecord']);g.add(name,'t:componentOf',ref('f:AlternatorAssembly'))
 g.node('f:UnmappedComponent',['owl:Thing'])
 g.node('f:UnmappedConfigurationRecord',['ap:Configuration_Item']);g.add('f:UnmappedConfigurationRecord','t:componentOf',ref('f:AlternatorAssembly'))
 g.node('f:UnmappedProductDefinition',['ap:Product_Definition']);g.node('f:UnmappedShapeRecord',['ap:Shape_Representation'])
 return g


def export_csv(out,dataset,records):
 """Companion simulated source records, not an executed CSV-to-ERP connector.

 Rows are projections of the same authored scenario. Different source tables
 can refer to the same IRI and their row counts must not be called individuals
 or triples. Emissions are intentionally absent: R01 computes them at runtime.
 """
 base=out/'csv'/dataset;rows={name:[] for name in ['mes_operations','mes_events','mes_subloads','erp_lots','erp_recipe_inputs','erp_equipment','lci_electricity_inputs','lci_material_flow_inputs','lci_electricity_factors']}
 for b in records:
  lot=iri('f:'+b['id']);process=iri('f:'+b['id']+'_Process');lci=iri('f:'+b['id']+'_LCIProcess');plan=iri('f:'+b['id']+'_Plan')
  role='DELIBERATE_NEGATIVE_TEST_RECORD' if b['status'] in ['REJECTED','INVALID_TEST_RECORD'] else 'SIMULATED_PRODUCTION_CASE'
  rows['erp_lots'].append(dict(lot_iri=lot,process_iri=process,plan_iri=plan,work_order_iri=iri('f:'+b['id']+'_Order'),product_iri=iri('f:WhitePaint'),family=b['family'],quality_status=b['status'],output_quantity=b['liters'],quantity_unit='L',scenario_role=role))
  for name,time in [('Start',b['start']),('End',b['start']+6600*b['scale'])]:
   rows['mes_events'].append(dict(event_iri=iri('f:'+b['id']+'_Event'+name),lot_iri=lot,process_iri=process,event_kind=name,event_time_seconds=number(time),time_reference='COMMON_ARBITRARY_ORIGIN_SECONDS',scenario_role=role))
  for o in b['operations']:
   operation=iri(o['id']);rows['mes_operations'].append(dict(operation_iri=operation,lot_iri=lot,process_iri=process,stage_index=o['index'],stage_name=o['stage'],duration_minutes=number(o['duration']),energy_kwh=o['energy'],energy_unit='kWh',input_mass_kg=o['input'],output_mass_kg=o['output'],registered_loss_kg=o['loss'],mass_unit='kg',scenario_role=role))
   rows['lci_electricity_inputs'].append(dict(operation_iri=operation,lot_iri=lot,manufacturing_process_iri=process,lci_representation_iri=lci,energy_value=o['energy'],energy_unit='kWh',factor_iri=iri('f:GridFactor'),inventory_scope='ELECTRICITY_CONSUMED_IN_PRODUCTION_ONLY',scenario_role=role))
   for sub in o['subloads']:
    rows['mes_subloads'].append(dict(subload_iri=iri(sub['id']),operation_iri=operation,lot_iri=lot,volume_l=sub['volumeL'],volume_unit='L',duration_minutes=sub['durationMinutes'],start_seconds=sub['startSeconds'],end_seconds=sub['endSeconds'],time_reference='COMMON_ARBITRARY_ORIGIN_SECONDS',scenario_role=role))
  for name,v in MATERIALS.items():
   if v['recipe']<=0:continue
   flow=iri('f:'+b['id']+'_Flow_'+name);material=iri('f:'+name);quantity=number(dec(b['input'])*dec(v['recipe']))
   rows['erp_recipe_inputs'].append(dict(flow_iri=flow,lot_iri=lot,process_iri=process,material_iri=material,supplier_iri=iri('f:'+v['supplier']),quantity=quantity,unit='kg',accounting='RECIPE_ONCE_PER_PROCESS',scenario_role=role))
   rows['lci_material_flow_inputs'].append(dict(flow_iri=flow,lci_representation_iri=lci,manufacturing_process_iri=process,material_iri=material,quantity=quantity,unit='kg',flow_type='ProductFlow',exchange_scope='PURCHASED_TECHNOSPHERE_INPUT',included_in_electricity_ghg_total='NO',scenario_role=role))
 for name,v in EQUIPMENT.items():
  rows['erp_equipment'].append(dict(equipment_iri=iri('f:'+name),role=v['role'],power_kw=number(v['powerKW']),power_unit='kW',capacity_l=number(v['capacityLiters']) if 'capacityLiters' in v else '',capacity_unit='L' if 'capacityLiters' in v else '',capacity_units_per_minute=number(v['capacityUnitsPerMinute']) if 'capacityUnitsPerMinute' in v else '',scenario_role='DECLARED_SCENARIO_EQUIPMENT_NO_PLANT_OBSERVATION'))
 rows['lci_electricity_factors'].append(dict(factor_iri=iri('f:GridFactor'),factor_value='0.5',factor_unit='kg CO2e/kWh',current_version='2',prior_version='1',scenario_role='DECLARED_SCENARIO_FACTOR_NO_PLANT_MEASUREMENT'))
 semantics={
 'mes_operations':'One row per operation and lot; recorded simulated electricity/mass/duration inputs, not inferred GHG.',
 'mes_events':'One start/end event record per process execution, with common temporal origin; R13 derives precedence.',
 'mes_subloads':'One sequential mill subload record, <=50L, belonging to operation2; durations sum to its duration.',
 'erp_lots':'One simulated lot/order/plan/product record; quantities inL. Negative functional records explicitly identified.',
 'erp_recipe_inputs':'One purchased recipe ingredient per process, inventoried once; not fresh addition at each stage.',
 'erp_equipment':'Five reusable scenario equipment records; not actualERP master data.',
 'lci_electricity_inputs':'One operation electricity input linked to separately identified inventory representation and factor; NO emissions precalculated.',
 'lci_material_flow_inputs':'One purchased product flow per recipe ingredient/process; outside the electricity GHG sum, not ElementaryFlow.',
 'lci_electricity_factors':'One declared electricity factor reused by operations, not LCIA CharacterizationFactor or measured plant value.'}
 domain={'mes':'MES','erp':'ERP','lci':'LCI'};files=[];counts={key:0 for key in domain.values()}
 for name,values in rows.items():
  path=base/(name+'.csv');path.parent.mkdir(parents=True,exist_ok=True)
  with path.open('w',newline='',encoding='utf-8') as stream:
   writer=csv.DictWriter(stream,fieldnames=list(values[0]));writer.writeheader();writer.writerows(values)
  source=domain[name.split('_',1)[0]];counts[source]+=len(values)
  files.append({'dataset':dataset,'path':str(path.relative_to(ROOT)),'domain':source,'table':name,'rows':len(values),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'row_semantics':semantics[name]})
 return files,counts

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--output-dir',type=Path,default=HERE);parser.add_argument('--seed',type=int,default=42);args=parser.parse_args()
 out=args.output_dir.resolve();out.mkdir(parents=True,exist_ok=True)
 if not out.is_relative_to(ROOT):raise SystemExit('Output must be inside this research repository to preserve relative manifest paths')
 g,records=functional();files=[g.write(out/'functional.ttl')];csv_files,csv_counts=export_csv(out,'functional',records);record_counts={'functional':csv_counts}
 for domain in ['manufacturing','environmental','union']:files.append(g.projection(domain).write(out/('functional-'+domain+'.ttl')))
 scenario={'seed':args.seed,'nature':'constructed computational test scenario, not plant observations','electricity_factor_kgCO2e_per_kWh':.5,'declared_indicator_threshold':{'record':'f:ElectricityGHGThreshold','value':5,'unit':'kg CO2e','category':'GHG_ELECTRICITY_PRODUCTION','scope':'Hypothetical scenario threshold, not regulatory or industrial'},'numeric_contract':'One numeric energy and one selected electricity factor IRI per operation. Numerically equivalent literals count once; contradictory values block numeric evaluation. Aggregates require all selected same-process operations.','system_boundary':'electricity consumed by operations only; raw material manufacturing excluded','density_assumption_kg_per_L':1.25,'mass_balance':'abs(input-output-registered_loss)/input <= 0.01; input must be positive','material_accounting':'Recipe is inventoried once per process; hasInput/consumesMaterial at multiple stages denotes the same circulating materials, not fresh additions. Constant circulating mass before final registered losses is a deliberate functional simplification, not a physical mass-flow simulation.','human_resource_scope':'One named operator is assigned throughout the executable functional fixture; management and specialized quality roles in contextual diagrams are not additional executed resource instances.','stages':STAGES,'equipment':EQUIPMENT,'materials':MATERIALS,'batches':records,'derivation':'energy_kWh=sum(power_kW of assigned equipment)*duration_minutes/60, rounded6 decimals; GHG=energy*0.5; kgCO2e/L=sum distinct operation contributions/litres; package outputMass/1.25=litres; BatchB residue2.5kg/250kg=1%, BatchC1.26/125=1.008%, BatchZero invalid input','original_generator':'source artifact preserved outside repository; inherited seed42, stage order and mill50L30kW; no internal path required'}
 (out/'functional-scenario.json').write_text(json.dumps(scenario,ensure_ascii=False,indent=2)+'\n')
 for label,lots in [('small',20),('medium',100),('large',200)]:
  g,rs=load_scenario(lots,args.seed);meta=g.write(out/('load-'+label+'.ttl'));meta['batches']=lots;files.append(meta)
  exported,counts=export_csv(out,'load-'+label,rs);csv_files+=exported;record_counts['load-'+label]=counts
  for domain in ['manufacturing','environmental','union']:
   meta=g.projection(domain).write(out/('load-'+label+'-'+domain+'.ttl'));meta['batches']=lots;files.append(meta)
 files.append(automotive().write(out/'automotive.ttl'))
 (out/'manifest.json').write_text(json.dumps({'seed':args.seed,'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'files':files,'csv_exports':csv_files,'simulated_source_record_counts':record_counts,'csv_scope':'Companion source representations of the same simulated scenario. Runtime loadsTTL; no CSV-to-ERP/MES industrial integration is asserted. Row counts may repeatIRIs across representations and are distinct from individuals/triples. No inferred operationGHG is exported as input.','counts_policy':'asserted named fixture individuals and asserted triples; no padding to historical target counts'},ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'generated':files},ensure_ascii=False))

if __name__=='__main__':main()
