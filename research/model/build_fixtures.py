"""Authored positive, negative, boundary and ablation assertions.

Assertions are written from the scenario specification, never from runtime
outputs. The real Java runner executes these SPARQL ASK checks independently.
"""
import json,re
from pathlib import Path
from lxml import etree as E
from build_catalog import PREFIX, QUERIES, ADDITIONAL_CONTROLS

ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'research/data/fixtures'; A=F/'assertions'
TTL=PREFIX.replace('PREFIX ','@prefix ').replace('>\n','> .\n')
# SPARQL PREFIX syntax uses no space between prefix and colon; valid Turtle too.

def main():
 F.mkdir(exist_ok=True);A.mkdir(exist_ok=True)
 cat=json.loads((ROOT/'research/catalog.json').read_text())
 cfg=next(c for c in cat['configurations'] if c['id']=='integrated')
 tbox=cfg['tbox_paths']+cfg['rules_paths']
 tests=[]
 def ttl(name,body):
  p=F/(name+'.ttl');p.write_text(TTL+body+'\n');return str(p.relative_to(ROOT))
 def assertion(ident,body,expect=True,**opts):
  p=A/(ident+'.sparql');p.write_text(PREFIX+'ASK { '+body+' }\n')
  tests.append(dict(id=ident,configuration=opts.pop('configuration','integrated'),query_path=str(p.relative_to(ROOT)),expected_boolean=expect,**opts))
 extra=ttl('rule-boundaries','''
f:Maintenance01 a owl:NamedIndividual ; t:maintenanceStatus "ACTIVE"^^xsd:string .
f:FilterSystem_04 i:hasMaintenance f:Maintenance01 .
f:ZeroDurationOperation a owl:NamedIndividual, i:ProcessOperation ; i:yield "100"^^xsd:decimal ; i:duration "0"^^xsd:decimal ; i:hasInput f:Water .
f:NegativeEnergyOperation a owl:NamedIndividual, i:ProcessOperation ; i:consumesEnergyKWh "-1"^^xsd:decimal ; t:hasEmissionFactor f:GridFactor ; i:hasInput f:Water .
f:ThresholdIndicator a owl:NamedIndividual, e:ImpactIndicator ; e:hasIndicatorValue "5"^^xsd:decimal ; t:unit "kg CO2e"^^xsd:string ; t:indicatorCategory "GHG_ELECTRICITY_PRODUCTION"^^xsd:string ; t:hasThreshold f:ElectricityGHGThreshold .
f:WrongProcessEvent a owl:NamedIndividual, i:ManufacturingData ; t:forLot f:BatchA ; t:eventForProcess f:BatchB_Process ; t:eventTimeSeconds "10000"^^xsd:decimal .
f:WrongRatio a owl:NamedIndividual, t:RatioObservation ; t:forOperation f:BatchA_Op2 ; t:forIndicator f:BatchB_Indicator .
f:IncompatibleKgLot a owl:NamedIndividual, i:Lot ; i:functionalUnit "kg"^^xsd:string ; i:producesQuantity "100"^^xsd:decimal ; t:producesProduct f:WhitePaint ; t:hasGHGContribution f:BatchA_Op2 .
f:ZeroLitersLot a owl:NamedIndividual, i:Lot ; i:functionalUnit "L"^^xsd:string ; i:producesQuantity "0"^^xsd:decimal ; t:producesProduct f:WhitePaint ; t:hasGHGContribution f:BatchA_Op2 .
f:MissingQuantityLot a owl:NamedIndividual, i:Lot ; i:functionalUnit "L"^^xsd:string ; t:producesProduct f:WhitePaint ; t:hasGHGContribution f:BatchA_Op2 .
f:DuplicateContributionProcess a owl:NamedIndividual, i:ManufacturingProcess .
f:DuplicateContributionLot a owl:NamedIndividual, i:Lot ; i:functionalUnit "L"^^xsd:string ; i:producesQuantity "100"^^xsd:decimal ; t:producesProduct f:WhitePaint ; i:hasProcess f:DuplicateContributionProcess ; i:hasActivity f:EqualContribution1, f:EqualContribution2 .
f:EqualContribution1 a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:DuplicateContributionProcess ; i:consumesEnergyKWh "10"^^xsd:decimal ; t:hasEmissionFactor f:GridFactor ; i:hasInput f:Water .
f:EqualContribution2 a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:DuplicateContributionProcess ; i:consumesEnergyKWh "10"^^xsd:decimal ; t:hasEmissionFactor f:GridFactor ; i:hasInput f:Water .
f:MissingMassOperation a owl:NamedIndividual, i:ProcessOperation ; t:outputMassKg "1"^^xsd:decimal ; t:registeredLossKg "0"^^xsd:decimal ; i:hasInput f:Water .
f:UnchangedVersionEntity a owl:NamedIndividual ; c:hasVersion "2"^^xsd:string ; c:hasPriorVersion "2"^^xsd:string .
f:WrongUnitFactor a owl:NamedIndividual, t:ElectricityEmissionFactor ; t:factorValue "0.5"^^xsd:decimal ; t:factorUnit "kg CO2e/kg"^^xsd:string .
f:MissingUnitFactor a owl:NamedIndividual, t:ElectricityEmissionFactor ; t:factorValue "0.5"^^xsd:decimal .
f:WrongUnitOperation a owl:NamedIndividual, i:ProcessOperation ; i:consumesEnergyKWh "10"^^xsd:decimal ; t:hasEmissionFactor f:WrongUnitFactor ; i:hasInput f:Water .
f:MissingUnitOperation a owl:NamedIndividual, i:ProcessOperation ; i:consumesEnergyKWh "10"^^xsd:decimal ; t:hasEmissionFactor f:MissingUnitFactor ; i:hasInput f:Water .
f:PrecisionLot a owl:NamedIndividual, i:Lot ; i:hasProcess f:PrecisionProcess ; i:hasActivity f:PrecisionOperation1, f:PrecisionOperation2, f:PrecisionOperation3 .
f:PrecisionProcess a owl:NamedIndividual, i:ManufacturingProcess ; t:hasInventoryRepresentation f:PrecisionLCI .
f:PrecisionLCI a owl:NamedIndividual, e:UnitProcess .
f:PrecisionOrder a owl:NamedIndividual, i:WorkOrder ; i:producesLot f:PrecisionLot .
f:PrecisionOperation1 a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:PrecisionProcess ; i:cycleTime "100"^^xsd:decimal ; i:yield "100"^^xsd:decimal ; i:duration "40"^^xsd:decimal ; i:hasInput f:Water .
f:PrecisionOperation2 a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:PrecisionProcess ; i:cycleTime "1"^^xsd:decimal ; i:yield "1"^^xsd:decimal ; i:duration "3"^^xsd:decimal ; i:hasInput f:Water .
f:PrecisionOperation3 a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:PrecisionProcess ; i:cycleTime "1.25"^^xsd:decimal ; i:yield "1.25"^^xsd:decimal ; i:duration "2.5"^^xsd:decimal ; i:hasInput f:Water .
f:PrecisionIndicator1 a owl:NamedIndividual, e:ImpactIndicator ; e:computedForProcess f:PrecisionLCI ; e:indicatorValue "40"^^xsd:decimal .
f:PrecisionIndicator2 a owl:NamedIndividual, e:ImpactIndicator ; e:computedForProcess f:PrecisionLCI ; e:indicatorValue "3"^^xsd:decimal .
f:PrecisionIndicator3 a owl:NamedIndividual, e:ImpactIndicator ; e:computedForProcess f:PrecisionLCI ; e:indicatorValue "2.5"^^xsd:decimal .
f:PrecisionRatio1 a owl:NamedIndividual, t:RatioObservation ; t:forOperation f:PrecisionOperation1 ; t:forIndicator f:PrecisionIndicator1 .
f:PrecisionRatio2 a owl:NamedIndividual, t:RatioObservation ; t:forOperation f:PrecisionOperation2 ; t:forIndicator f:PrecisionIndicator2 .
f:PrecisionRatio3 a owl:NamedIndividual, t:RatioObservation ; t:forOperation f:PrecisionOperation3 ; t:forIndicator f:PrecisionIndicator3 .
<https://example.org/mass-unit-test#inputMassG> a owl:DatatypeProperty .
<https://example.org/mass-unit-test#outputMassG> a owl:DatatypeProperty .
<https://example.org/mass-unit-test#registeredLossG> a owl:DatatypeProperty .
f:MassInGramsOperation a owl:NamedIndividual, i:ProcessOperation ; i:hasInput f:Water ; <https://example.org/mass-unit-test#inputMassG> "1000"^^xsd:decimal ; <https://example.org/mass-unit-test#outputMassG> "990"^^xsd:decimal ; <https://example.org/mass-unit-test#registeredLossG> "10"^^xsd:decimal .
''')
 pairs={
 'R01':('f:BatchA_Op2 e:hasGHGImpactKgCO2e ?v . FILTER(?v=5)','f:NegativeEnergyOperation e:hasGHGImpactKgCO2e ?v .'),
 'R02':('f:BatchA_LCIProcess e:hasInventoryFlow c:WaterFlow .','f:MissingProcess e:hasInventoryFlow c:WaterFlow .'),
 'R03':('f:BatchA a:hasRecordedInputFlowForLot f:BatchA_Flow_TitaniumDioxide .','f:BatchA a:hasRecordedInputFlowForLot f:BatchB_Flow_TitaniumDioxide .'),
 'R04':('f:BatchA_Indicator a c:HighImpactIndicator .','f:ThresholdIndicator a c:HighImpactIndicator .'),
 'R05':('f:BatchA e:hasImpact f:BatchA_Indicator .','f:BatchA e:hasImpact f:BatchB_Indicator .'),
 'R06':('f:BatchA_Flow_TitaniumDioxide t:usesEmissionFactor f:TitaniumDioxide_Factor .','f:BatchA_Flow_TitaniumDioxide t:usesEmissionFactor f:AcrylicResin_Factor .'),
 'R08':('f:BatchA e:hasFunctionalUnitQuantity ?v . FILTER(?v=98.4)','f:MissingOperation e:hasFunctionalUnitQuantity ?v .'),
 'R09':('f:BatchA_Op2 i:usesResource f:PearlMill_01 .','f:BatchA_Op1 i:usesResource f:PearlMill_01 .'),
 'R10':('f:BatchA i:propagatedQualityStatus ?v . FILTER(STR(?v)="APPROVED")','f:MissingOperation i:propagatedQualityStatus ?v .'),
 'R11':('f:BatchA_Op2 a:hasEfficiency ?v . FILTER(?v=5)','f:ZeroDurationOperation a:hasEfficiency ?v .'),
 'R12':('f:PearlMill_01 i:hasCapability a:FineGrinding .','f:MixingReactor_02 i:hasCapability a:FineGrinding .'),
 'R13':('f:BatchA_EventStart i:precedesEvent f:BatchA_EventEnd .','f:BatchA_EventStart i:precedesEvent f:WrongProcessEvent .'),
 'R14':('f:BatchA_Op2 i:responsibleOperator f:Operator_01 .','f:MissingOperation i:responsibleOperator f:Operator_01 .'),
 'R15':('f:FilterSystem_04 a i:temporarilyUnavailable .','f:PearlMill_01 a i:temporarilyUnavailable .'),
 'R17':('f:BatchA i:requiresCleaningBetween f:BatchB .','f:BatchA i:requiresCleaningBetween f:BatchC .'),
 'R18':('f:BatchC a i:requiresReprocess .','f:BatchA a i:requiresReprocess .'),
 'R20':('f:BatchA_Flow_TitaniumDioxide c:bridgesFlowToActivity f:BatchA_Op2 .','f:BatchA_Flow_TitaniumDioxide c:bridgesFlowToActivity f:BatchB_Op2 .'),
 'R21':('f:BatchA t:hasGHGContribution f:BatchA_Op2 .','f:BatchA t:hasGHGContribution f:BatchB_Op2 .'),
 'R22':('f:PearlMill_01 a c:Hotspot .','f:UnrelatedBottleneck a c:Hotspot .'),
 'R23':('f:BatchA_Op2_Ratio t:operationIndicatorRatio ?v . FILTER(ABS(?v-(20/57.6666665))<0.000000001)','f:WrongRatio t:operationIndicatorRatio ?v .'),
 'R24':('f:MissingProcess a c:CoverageGap .','f:BatchA_Process a c:CoverageGap .'),
 'R25':('f:GridFactor a c:RequiresRecomputeIndicators .','f:TitaniumDioxide_Factor a c:RequiresRecomputeIndicators .'),
 }
 for ident,(pos,neg) in pairs.items():
  assertion(ident+'_positive',pos,additional_files=[extra],mechanism=ident,case='positive')
  assertion(ident+'_negative',neg,False,additional_files=[extra],mechanism=ident,case='negative')
 assertion('R23_zero_indicator','f:BatchZero_Op2_Ratio t:operationIndicatorRatio ?v .',False,additional_files=[extra],mechanism='R23',case='zero denominator')
 for ident,prop,subject in [('R11','a:hasEfficiency','PrecisionOperation'),('R23','t:operationIndicatorRatio','PrecisionRatio')]:
  for index,expected in [(1,'100/40'),(2,'1/3'),(3,'1.25/2.5')]:
   assertion(ident+'_fractional_'+str(index),'f:'+subject+str(index)+' '+prop+' ?v . FILTER(ABS(?v-('+expected+'))<0.000000001)',additional_files=[extra],mechanism=ident,case='Independent fractional arithmetic; decimal scale avoids integer-numerator rounding in SWRLAPI2.1.3; precision is computational, not physical accuracy')
 assertion('R13_cross_lot','f:BatchA_EventStart i:precedesEvent f:BatchB_EventEnd .',False,additional_files=[extra],mechanism='R13',case='different lot')
 for kind in ['WrongUnit','MissingUnit']:
  assertion('R01_'+kind,'f:'+kind+'Operation e:hasGHGImpactKgCO2e ?v .',False,additional_files=[extra],mechanism='R01',case='Factor class alone is insufficient; explicit electrical unit required')
 unit_import=ttl('incompatible-preasserted-contribution','''f:UnitTestLot a owl:NamedIndividual, i:Lot ; i:hasProcess f:UnitTestProcess ; i:hasActivity f:UnitTestOperation .
f:UnitTestProcess a owl:NamedIndividual, i:ManufacturingProcess .
f:UnitTestOperation a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:UnitTestProcess ; t:hasEmissionFactor f:UnitTestFactor ; e:hasGHGImpactKgCO2e "5"^^xsd:decimal ; i:hasInput f:Water .
f:UnitTestFactor a owl:NamedIndividual, t:ElectricityEmissionFactor ; t:factorValue "0.5"^^xsd:decimal ; t:factorUnit "kg CO2e/kg"^^xsd:string .''')
 assertion('R21_incompatible_unit','f:UnitTestLot t:hasGHGContribution f:UnitTestOperation .',False,additional_files=[unit_import],mechanism='R21',case='Even an imported GHG value is excluded when its factor unit is not electrical')
 context=ttl('contextual-correspondences','''
f:CE_Product a owl:NamedIndividual, c:Product . f:CE_ProductFlow a owl:NamedIndividual, e:ProductFlow ; t:flowRefersToProduct f:CE_Product ; owl:differentFrom f:CE_Product .
f:CE_Manufacturing a owl:NamedIndividual, i:ManufacturingProcess ; t:hasInventoryRepresentation f:CE_LCI ; owl:differentFrom f:CE_LCI . f:CE_LCI a owl:NamedIndividual, e:UnitProcess .
f:CE_ConsumedResource a owl:NamedIndividual, i:ManufacturingResource ; i:resourceCurrentState a:Consumed . f:CE_PurchasedInput a owl:NamedIndividual, e:ProductFlow ; t:inputFlowForResource f:CE_ConsumedResource ; owl:differentFrom f:CE_ConsumedResource .
f:CE_Facility a owl:NamedIndividual, i:ManufacturingFacility ; t:facilityInBoundary f:CE_Boundary ; owl:differentFrom f:CE_Boundary . f:CE_Boundary a owl:NamedIndividual, e:SystemBoundary .
f:CE_Impact a owl:NamedIndividual, e:ImpactIndicator ; t:indicatorForLot f:CE_Lot ; owl:differentFrom f:CE_Quality . f:CE_Quality a owl:NamedIndividual, i:QualityAttribute ; t:qualityForLot f:CE_Lot . f:CE_Lot a owl:NamedIndividual, i:Lot .
f:CE_Allocation a owl:NamedIndividual, e:AllocationProcedure ; t:allocationForOperation f:CE_Operation ; owl:differentFrom f:CE_Operation ; t:scenarioRole "Optional illustrative allocation relation; no allocation algorithm executed in painting"^^xsd:string . f:CE_Operation a owl:NamedIndividual, i:ProcessOperation ; i:hasInput f:Water .
f:CE_Inventory a owl:NamedIndividual, e:LifeCycleInventory ; t:inventoryForPlan f:CE_Plan ; owl:differentFrom f:CE_Plan . f:CE_Plan a owl:NamedIndividual, i:ProcessPlan .
''')
 contextual={
 'A1':('f:CE_ProductFlow a:flowRefersToProduct f:CE_Product ; owl:differentFrom f:CE_Product .','f:CE_Product a e:ProductFlow .'),
 'A2':('f:CE_Manufacturing a:hasInventoryRepresentation f:CE_LCI ; owl:differentFrom f:CE_LCI .','f:CE_Manufacturing a e:UnitProcess .'),
 'A3':('f:CE_PurchasedInput a:inputFlowForResource f:CE_ConsumedResource ; owl:differentFrom f:CE_ConsumedResource .','f:CE_ConsumedResource a e:ElementaryFlow .'),
 'A4':('f:CE_Facility a:facilityInBoundary f:CE_Boundary ; owl:differentFrom f:CE_Boundary .','f:CE_Facility a e:SystemBoundary .'),
 'A5':('f:CE_Impact a:indicatorForLot f:CE_Lot ; owl:differentFrom f:CE_Quality . f:CE_Quality a:qualityForLot f:CE_Lot .','f:CE_Quality a e:ImpactIndicator .'),
 'A6':('f:CE_Allocation a:allocationForOperation f:CE_Operation ; owl:differentFrom f:CE_Operation .','f:CE_Operation a e:AllocationProcedure .'),
 'A7':('f:CE_Inventory a:inventoryForPlan f:CE_Plan ; owl:differentFrom f:CE_Plan .','f:CE_Plan a e:LifeCycleInventory .'),
 }
 for ident,(pos,neg) in contextual.items():
  pos=pos.replace('owl:differentFrom','(owl:differentFrom|^owl:differentFrom)')
  assertion(ident+'_context_positive',pos,additional_files=[context],case='Directional context-property mapping with explicitly distinct endpoint identities')
  assertion(ident+'_no_global_reclassification',neg,False,additional_files=[context],case='Non-entailment of old universal class mapping under open-world semantics; not asserted membership falsity')
 assertion('PurchasedInputs_not_elementary','?flow a e:ElementaryFlow ; t:belongsToProcess f:BatchA_Process .',False,case='Technosphere material inputs must not be reclassified as nature exchanges')
 assertion('No_allocation_in_painting','?procedure a e:AllocationProcedure .',False,case='Main painting fixture has no supplied allocation procedure and executes no allocation algorithm')
 assertion('Scenario_factor_not_LCIA','f:GridFactor a e:CharacterizationFactor .',False,case='Declared electricity emission factor must not be reclassified as a LCIA characterization factor')
 assertion('Indicator_matches_electric_sum','f:BatchA_Indicator e:hasIndicatorValue ?recorded . { SELECT (SUM(?v) AS ?computed) WHERE { f:BatchA t:hasGHGContribution ?op . ?op e:hasGHGImpactKgCO2e ?v } } FILTER(ABS(?recorded-?computed)<0.000000001)',case='Same boundary and magnitude recorded indicator agrees with sum of distinct SWRL operation contributions')
 # Independent closed snapshots for four ASK mechanisms. They contain only
 # the records needed to decide their defined completeness condition.
 snapshots={
 'R07':('f:P a owl:NamedIndividual, i:ManufacturingProcess .','f:P a owl:NamedIndividual, i:ManufacturingProcess ; t:hasInventoryRepresentation f:LCI . f:LCI a owl:NamedIndividual, e:UnitProcess . f:I a owl:NamedIndividual, e:ImpactIndicator ; e:computedForProcess f:LCI ; e:indicatorValue "5"^^xsd:decimal .'),
 'R16':('f:O a owl:NamedIndividual, i:ProcessOperation .','f:O a owl:NamedIndividual, i:ProcessOperation ; i:hasInput f:Water . f:Water a owl:NamedIndividual .'),
 'R19':('f:O a owl:NamedIndividual, i:Quality_Control_Activity .','f:O a owl:NamedIndividual, i:Quality_Control_Activity ; i:hasMeasurement f:M . f:M a owl:NamedIndividual .'),
 }
 from build_catalog import VALIDATIONS
 for ident,(missing,complete) in snapshots.items():
  for name,body,expected in [('missing',missing,True),('complete',complete,False)]:
   path=ttl(ident+'-'+name,body)
   assertion(ident+'_'+name,VALIDATIONS[ident][5:-2],expected,files=tbox+[path],enabled_rules='NONE',mechanism=ident,case=name)
 pair='''f:A a owl:NamedIndividual, i:Lot ; i:requiresCleaningBetween f:B ; i:processedOn f:Machine ; t:endTimeSeconds "100"^^xsd:decimal .
f:B a owl:NamedIndividual, i:Lot ; i:processedOn f:Machine ; t:startTimeSeconds "200"^^xsd:decimal .
f:Machine a owl:NamedIndividual, i:EquipmentResource . f:OtherMachine a owl:NamedIndividual, i:EquipmentResource .'''
 for name,clean,expected in [('missing','',True),('valid','f:Clean a owl:NamedIndividual, i:CleaningOperation ; t:cleanedResource f:Machine ; t:startTimeSeconds "100"^^xsd:decimal ; t:endTimeSeconds "200"^^xsd:decimal .',False),('wrong_equipment','f:Clean a owl:NamedIndividual, i:CleaningOperation ; t:cleanedResource f:OtherMachine ; t:startTimeSeconds "110"^^xsd:decimal ; t:endTimeSeconds "190"^^xsd:decimal .',True),('historical','f:Clean a owl:NamedIndividual, i:CleaningOperation ; t:cleanedResource f:Machine ; t:startTimeSeconds "10"^^xsd:decimal ; t:endTimeSeconds "20"^^xsd:decimal .',True),('overlaps_next_lot','f:Clean a owl:NamedIndividual, i:CleaningOperation ; t:cleanedResource f:Machine ; t:startTimeSeconds "110"^^xsd:decimal ; t:endTimeSeconds "210"^^xsd:decimal .',True)]:
  path=ttl('R26-'+name,pair+'\n'+clean)
  assertion('R26_'+name,VALIDATIONS['R26'][5:-2],expected,files=tbox+[path],enabled_rules='NONE',mechanism='R26',case=name)
  tests[-1]['expected_cleaning_status']='MISSING_CLEANING_RECORD' if expected else 'COMPLETE_CLEANING_RECORD'
 # A false canonical R26 is meaningful only after its required pair is evaluable.
 contexts=[('missing_end',True,True,None,'200'),('missing_start',True,True,'100',None),
           ('missing_equipment_a',False,True,'100','200'),('missing_equipment_b',True,False,'100','200'),
           ('different_equipment',True,True,'100','200'),('reversed_interval',True,True,'300','200')]
 for name,eq_a,eq_b,end_a,start_b in contexts:
  body='f:A a owl:NamedIndividual, i:Lot ; i:requiresCleaningBetween f:B . f:B a owl:NamedIndividual, i:Lot . f:Machine a owl:NamedIndividual . f:OtherMachine a owl:NamedIndividual .\n'
  if eq_a:body+='f:A i:processedOn f:Machine .\n'
  if eq_b:body+='f:B i:processedOn '+('f:OtherMachine' if name=='different_equipment' else 'f:Machine')+' .\n'
  if end_a is not None:body+='f:A t:endTimeSeconds "'+end_a+'"^^xsd:decimal .\n'
  if start_b is not None:body+='f:B t:startTimeSeconds "'+start_b+'"^^xsd:decimal .\n'
  path=ttl('C26-'+name,body)
  assertion('C26_'+name,ADDITIONAL_CONTROLS['C26'][5:-2],True,files=tbox+[path],enabled_rules='NONE',case='Required pair is not evaluable; canonical R26 false cannot approve cleaning')
  tests[-1]['expected_cleaning_status']='NOT_EVALUABLE'
 nonnumeric=ttl('C26-nonnumeric','f:A a owl:NamedIndividual, i:Lot ; i:requiresCleaningBetween f:B ; i:processedOn f:Machine ; t:endTimeSeconds "unknown"^^xsd:string . f:B a owl:NamedIndividual, i:Lot ; i:processedOn f:Machine ; t:startTimeSeconds "200"^^xsd:decimal . f:Machine a owl:NamedIndividual .')
 tests.append(dict(id='C26_nonnumeric',configuration='integrated',files=tbox+[nonnumeric],enabled_rules='NONE',expected_status='INCONSISTENT',expected_cleaning_status='NOT_EVALUABLE',case='Non-numeric endpoint is also incompatible with the declared decimal range; case evaluability is reported separately from OWL inconsistency'))
 negative_origin=ttl('C26-negative-origin','f:A a owl:NamedIndividual, i:Lot ; i:requiresCleaningBetween f:B ; i:processedOn f:Machine ; t:endTimeSeconds "-100"^^xsd:decimal . f:B a owl:NamedIndividual, i:Lot ; i:processedOn f:Machine ; t:startTimeSeconds "-50"^^xsd:decimal . f:Machine a owl:NamedIndividual . f:Clean a owl:NamedIndividual, i:CleaningOperation ; t:cleanedResource f:Machine ; t:startTimeSeconds "-90"^^xsd:decimal ; t:endTimeSeconds "-60"^^xsd:decimal .')
 assertion('C26_negative_origin_valid',VALIDATIONS['R26'][5:-2],False,files=tbox+[negative_origin],enabled_rules='NONE',case='Negative seconds may be valid with an arbitrary common origin; interval ordering and record scope determine evaluability')
 tests[-1]['expected_cleaning_status']='COMPLETE_CLEANING_RECORD'
 # Test query semantics through the actual canonical SELECT body, not a
 # duplicate implementation. Results are decided by independent arithmetic.
 def qbody(q):return '{ '+QUERIES[q][2]+' } '
 # Imported links must not move a foreign operation into a lot or plan.
 # Only ScopeOperationGHG is derived from energy/factor; its contribution
 # is not asserted. The contradictory foreign contribution is deliberate.
 scope_base='''
f:ScopeLot a owl:NamedIndividual, i:Lot ; i:hasProcess f:ScopeProcess ; i:hasActivity f:ScopeOperation ; t:hasPlan f:ScopePlan ; t:producesProduct f:WhitePaint ; i:functionalUnit "L"^^xsd:string ; i:producesQuantity "100"^^xsd:decimal .
f:ScopeProcess a owl:NamedIndividual, i:ManufacturingProcess .
f:ForeignProcess a owl:NamedIndividual, i:ManufacturingProcess .
f:ScopePlan a owl:NamedIndividual, i:ProcessPlan ; i:includesActivity f:ScopeOperation ; i:assignsResource f:PearlMill_01, f:FilterSystem_04 ; t:planProduces f:WhitePaint ; t:lifecycleStage "Producción"^^xsd:string .
f:ScopeOperation a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:ScopeProcess ; i:consumesEnergyKWh "10"^^xsd:decimal ; t:hasEmissionFactor f:GridFactor ; i:duration "20"^^xsd:decimal ; i:requiresResource f:PearlMill_01 ; i:hasInput f:Water ; c:hasName "Operación del proceso propio"^^xsd:string ; t:stageIndex "1"^^xsd:integer .
f:ForeignOperation a owl:NamedIndividual, i:ProcessOperation ; i:partOfProcess f:ForeignProcess ; i:consumesEnergyKWh "200"^^xsd:decimal ; t:hasEmissionFactor f:GridFactor ; i:duration "200"^^xsd:decimal ; i:requiresResource f:FilterSystem_04 ; i:hasInput f:Water ; c:hasName "Operación del proceso ajeno"^^xsd:string ; t:stageIndex "2"^^xsd:integer .
'''
 scope_clean=ttl('scope-context-complete',scope_base)
 scope_wrong=ttl('scope-context-foreign',scope_base+'''f:ScopeLot i:hasActivity f:ForeignOperation ; t:hasGHGContribution f:ForeignOperation .
f:ScopePlan i:includesActivity f:ForeignOperation .
''')
 assertion('CCTX_complete',ADDITIONAL_CONTROLS['CCTX'][5:-2],False,additional_files=[scope_clean],case='Declared lot/plan operations match manufacturing process; unrelated standalone operation does not violate integrity',expected_context_status='CONTEXT_VALID')
 assertion('CCTX_foreign_link',ADDITIONAL_CONTROLS['CCTX'][5:-2],True,additional_files=[scope_wrong],case='Imported contradictory activity/contribution/plan links are visible as context errors; OWL consistency alone is insufficient',expected_context_status='CONTEXT_INTEGRITY_ERROR')
 # Exercise each CCTX branch independently. Each fixture adds only one
 # contradictory edge; its ASK also requires the other two branches to be
 # absent for that lot/operation, rather than relying on the combined case.
 context_branches={
  'activity':'''?lot a i:Lot ; i:hasActivity ?operation .
FILTER NOT EXISTS { ?lot i:hasProcess ?process . ?operation i:partOfProcess ?process . }''',
  'contribution':'''?lot t:hasGHGContribution ?operation .
FILTER NOT EXISTS { ?lot a i:Lot ; i:hasActivity ?operation ; i:hasProcess ?process . ?operation i:partOfProcess ?process . }''',
  'plan_activity':'''?lot a i:Lot ; t:hasPlan ?plan . ?plan i:includesActivity ?operation .
FILTER NOT EXISTS { ?lot i:hasProcess ?process . ?operation i:partOfProcess ?process . }''',
 }
 isolated_links={
  'activity':'f:ScopeLot i:hasActivity f:ForeignOperation .',
  'contribution':'f:ScopeLot t:hasGHGContribution f:ForeignOperation .',
  'plan_activity':'f:ScopePlan i:includesActivity f:ForeignOperation .',
 }
 for branch,edge in isolated_links.items():
  path=ttl('scope-context-foreign-'+branch+'-only',scope_base+edge+'\n')
  body=context_branches[branch]+'\n'+ '\n'.join('FILTER NOT EXISTS { '+pattern+' }' for other,pattern in context_branches.items() if other!=branch)
  assertion('CCTX_foreign_'+branch+'_only',body,True,additional_files=[path],control='CCTX',isolated_branch=branch,expected_isolated_branches={name:name==branch for name in context_branches},case='Exactly one declared '+branch+' edge refers to a foreign-process operation; the other CCTX branches must not trigger for this pair',expected_context_status='CONTEXT_INTEGRITY_ERROR')
 scoped_pairs={
 'Q01':('?process=f:ScopeProcess && ?energyKWh=10 && ?ghgKgCO2e=5','?process=f:ForeignProcess'),
 'Q02':('?lot=f:ScopeLot && ?resource=f:PearlMill_01','?lot=f:ScopeLot && ?resource=f:FilterSystem_04'),
 'Q03':('?lot=f:ScopeLot && ?energyKWh=10 && ?ghgKgCO2e=5','?lot=f:ScopeLot && ?ghgKgCO2e>5'),
 'Q06':('?lot=f:ScopeLot && ?ghgKgCO2e=5 && ?kgCO2ePerL=0.05','?lot=f:ScopeLot && ?ghgKgCO2e>5'),
 'Q11':('?lot=f:ScopeLot && ?ghgKgCO2e=5 && ?numNC=0','?lot=f:ScopeLot && ?ghgKgCO2e>5'),
 'Q16':('?plan=f:ScopePlan && ?resource=f:PearlMill_01 && ?usedTimeMinutes=20','?plan=f:ScopePlan && ?resource=f:FilterSystem_04'),
 'Q17':('?lot=f:ScopeLot && ?impactPerFU=0.05','?lot=f:ScopeLot && ?impactPerFU>0.05'),
 'Q18':('?plan=f:ScopePlan && ?activity=f:ScopeOperation && ?totalDurationMinutes=20','?plan=f:ScopePlan && ?activity=f:ForeignOperation'),
 'Q19':('?lot=f:ScopeLot && ?ghgKgCO2e=5','?lot=f:ScopeLot && ?ghgKgCO2e>5'),
 }
 for ident,(pos,neg) in scoped_pairs.items():
  assertion(ident+'_foreign_context_positive',qbody(ident)+'FILTER('+pos+')',additional_files=[scope_wrong],query=ident,case='Same process operation retains independent5kgCO2e contribution and20min duration despite an imported foreign link',expected_row_condition=pos)
  assertion(ident+'_foreign_context_negative',qbody(ident)+'FILTER('+neg+')',False,additional_files=[scope_wrong],query=ident,case='Foreign process operation must not contribute to this lot, product or plan aggregate',excluded_row_condition=neg)
 assertion('Q06_positive',qbody('Q06')+'FILTER(?lot=f:BatchA && ABS(?kgCO2ePerL - (57.6666665/98.4))<0.000000001)',additional_files=[extra])
 for kind in ['IncompatibleKgLot','ZeroLitersLot','MissingQuantityLot']:
  assertion('Q06_'+kind,qbody('Q06')+'FILTER(?lot=f:'+kind+')',False,additional_files=[extra])
 assertion('Q06_equal_contributions_preserved',qbody('Q06')+'FILTER(?lot=f:DuplicateContributionLot && ?ghgKgCO2e=10 && ?kgCO2ePerL=0.1)',additional_files=[extra],case='Two distinct operations with equal 5kg CO2e contributions must sum to10, not5')
 assertion('Q07_boundary_inclusive',qbody('Q07')+'FILTER(?operation=f:BatchB_Op7 && ?residueKg=2.5 && ?status="OK")',additional_files=[extra])
 assertion('Q07_above_boundary',qbody('Q07')+'FILTER(?operation=f:BatchC_Op7 && ?residueKg=1.26 && ?status="REVISAR")',additional_files=[extra])
 assertion('Q07_zero_invalid',qbody('Q07')+'FILTER(?operation=f:BatchZero_Op7 && ?status="INVALID_INPUT")',additional_files=[extra])
 assertion('Q07_missing_mass',qbody('Q07')+'FILTER(?operation=f:MissingMassOperation)',False,additional_files=[extra],case='Missing input mass has no fabricated balance')
 assertion('Q07_gram_fields_not_canonical_kg',qbody('Q07')+'FILTER(?operation=f:MassInGramsOperation)',False,additional_files=[extra],case='Explicit gram properties do not satisfy canonical kg fields. Q07 performs no unit conversion and cannot detect a physically mislabeled numeric value in a Kg property.')
 assertion('Q08_descriptive',qbody('Q08')+'FILTER(?operation=f:BatchA_Op2 && ?yieldPercent=100 && ?outputVolumeL=100 && ?kgCO2ePerL=0.05)',additional_files=[extra])
 assertion('Q08_zero_volume',qbody('Q08')+'FILTER(?operation=f:BatchZero_Op2)',False,additional_files=[extra])
 # Every SELECT has a concrete positive row and a specific excluded row.
 # Negative conditions refer to existing entities, values or relationships
 # in a nonempty fixture; none is an empty-graph test.
 query_pairs={
 'Q01':('?process=f:BatchA_Process && ABS(?ghgKgCO2e-57.6666665)<0.000000001','?process=f:MissingProcess'),
 'Q02':('?lot=f:BatchA && ?plan=f:BatchA_Plan && ?resource=f:TitaniumDioxide','?lot=f:BatchA && ?plan=f:BatchB_Plan'),
 'Q03':('?lot=f:BatchB && ABS(?ghgKgCO2e-115.3333335)<0.000000001','?lot=f:BatchA && ?ghgKgCO2e>100'),
 'Q04':('?process=f:BatchA_Process && ?resource=f:PearlMill_01','?process=f:BatchA_Process && ?resource=f:UnrelatedBottleneck'),
 'Q05':('?material=f:Coalescent && ?exceedsDeclaredCarbonThreshold=false','?material=f:Water'),
 'Q06':('?lot=f:BatchA && ?liters=98.4','?lot=f:IncompatibleKgLot'),
 'Q07':('?operation=f:BatchB_Op7 && ?residueKg=2.5 && ?status="OK"','?operation=f:BatchB_Op7 && ?status="REVISAR"'),
 'Q08':('?operation=f:BatchA_Op2 && ?yieldPercent=100 && ?kgCO2ePerL=0.05','?operation=f:BatchZero_Op2'),
 'Q09':('?process=f:BatchA_Process && ?indicator=f:BatchA_Indicator && ?factor=f:GridFactor','?process=f:BatchA_Process && ?indicator=f:BatchB_Indicator'),
 'Q10':('?material=f:TitaniumDioxide && ?supplier=f:Supplier_A','?material=f:TitaniumDioxide && ?supplier=f:Supplier_B'),
 'Q11':('?lot=f:BatchC && ?numNC=1','?lot=f:BatchA && ?numNC=1'),
 'Q12':('?indicator=f:BatchA_Indicator && ?operation=f:BatchA_Op2 && ?resource=f:PearlMill_01','?indicator=f:BatchB_Indicator && ?operation=f:BatchA_Op2'),
 'Q13':('?process=f:BatchA_Process && ?flow=f:BatchA_Flow_TitaniumDioxide && ?quantityKg=25','?process=f:BatchA_Process && ?flow=f:BatchB_Flow_TitaniumDioxide'),
 'Q14':('?lotA=f:BatchB && ?lotB=f:BatchC && ?equipment=f:PearlMill_01','?lotA=f:BatchA && ?lotB=f:BatchB'),
 'Q15':('?clean=f:ValidCleaning && ?waterL=5','?clean=f:BatchA_Op2'),
 'Q16':('?plan=f:BatchA_Plan && ?resource=f:Operator_01 && !BOUND(?oeePercent)','?resource=f:Operator_01 && BOUND(?oeePercent)'),
 'Q17':('?lot=f:BatchA && ?outputLiters=98.4','?lot=f:ZeroLitersLot'),
 'Q18':('?plan=f:BatchA_Plan && ?activity=f:BatchA_Op2 && ?stageIndex=2 && ?totalDurationMinutes=110','?plan=f:BatchA_Plan && ?activity=f:BatchB_Op2'),
 'Q19':('?lot=f:BatchB && ?ghgKgCO2e>100','?lot=f:BatchA && ?ghgKgCO2e>100'),
 'Q20':('?entity=f:MissingProcess && ?kind="ManufacturingProcess"','?entity=f:BatchA_Process'),
 'Q21':('?entity=f:GridFactor && STR(?currentVersion)="2" && STR(?priorVersion)="1"','?entity=f:UnchangedVersionEntity'),
 }
 for ident,(positive,negative) in query_pairs.items():
  assertion(ident+'_row_positive',qbody(ident)+'FILTER('+positive+')',additional_files=[extra],query=ident,case='Specified positive row in nonempty functional snapshot',expected_row_condition=positive)
  assertion(ident+'_row_negative',qbody(ident)+'FILTER('+negative+')',False,additional_files=[extra],query=ident,case='Specified wrong-context, absent-critical-input, incompatible-unit or filter-boundary row must not appear',excluded_row_condition=negative)
 for ident,body,expect in [('Ablation_R03',pairs['R03'][0],False),('Ablation_Q06',qbody('Q06')+'FILTER(?lot=f:BatchA)',False),('Ablation_Q07',qbody('Q07')+'FILTER(?operation=f:BatchB_Op7 && ?status="OK")',True),('Ablation_Q08',qbody('Q08')+'FILTER(?operation=f:BatchA_Op2)',False)]:
  assertion(ident,body,expect,enabled_rules='OWL_ONLY',case='same integrated TBox and CONSTRUCT, zero active SWRL')
 # Same IRI and retained source classification, 47/47 known components.
 auto=cat['configurations'][-1]['abox_paths'][-1]
 assertion('H3_components_same_identity','{ SELECT (COUNT(DISTINCT ?x) AS ?n) WHERE { ?x a ap:Configuration_Item, prod:Production_Component . } } FILTER(?n=47)',configuration='h3_extended')
 assertion('H3_source_classes_preserved','f:Component00 a ap:Configuration_Item, prod:Production_Component . f:AlternatorAssembly a ap:Product_Definition, c:Product . f:AssemblyShape a ap:Shape_Representation, prod:Product_Information .',configuration='h3_extended')
 assertion('H3_unmapped_negative','f:UnmappedComponent a prod:Production_Component .',False,configuration='h3_extended')
 assertion('H3_configuration_without_pilot_marker','f:UnmappedConfigurationRecord a prod:Production_Component .',False,configuration='h3_extended',case='Real AP242 configuration record with structural context but no explicit pilot component role')
 assertion('H3_product_definition_without_pilot_marker','f:UnmappedProductDefinition a c:Product .',False,configuration='h3_extended',case='An AP242 information view is not universally a PROTYS product')
 assertion('H3_shape_without_pilot_product','f:UnmappedShapeRecord a prod:Product_Information .',False,configuration='h3_extended',case='Shape information outside the explicitly selected product profile is not mapped by this pilot')
 assertion('H3_pre_extension_no_classification','f:Component00 a prod:Production_Component .',False,additional_files=[auto,'ontologies/iso10303-ap242-fragment.owl'],enabled_rules='OWL_ONLY')
 f_count=len({line.split(' ',1)[0] for name in ['research/data/functional.ttl',auto] for line in (ROOT/name).read_text().splitlines() if line.startswith('f:') and ' a owl:NamedIndividual .' in line})
 assertion('H3_no_new_named_individuals','{ SELECT (COUNT(DISTINCT ?x) AS ?n) WHERE { ?x a owl:NamedIndividual . FILTER(STRSTARTS(STR(?x),STR(f:))) } } FILTER(?n='+str(f_count)+')',configuration='h3_extended',case='Independent unique source declaration census in fixture namespace; stronger all-namespace set equality is tested separately')
 # Compare the entire independently read source set with the post-inference
 # set, across ALL namespaces. Equal cardinality alone cannot prove identity.
 h3=cat['configurations'][-1];source_individuals=set()
 for name in h3['tbox_paths']+h3['rules_paths']+h3['abox_paths']:
  path=ROOT/name
  if path.suffix=='.owl':
   tree=E.parse(str(path));ns={'owl':'http://www.w3.org/2002/07/owl#','rdf':'http://www.w3.org/1999/02/22-rdf-syntax-ns#'}
   source_individuals.update(tree.xpath('//owl:NamedIndividual/@rdf:about',namespaces=ns))
   source_individuals.update(tree.xpath('//rdf:Description[rdf:type/@rdf:resource="http://www.w3.org/2002/07/owl#NamedIndividual"]/@rdf:about',namespaces=ns))
  else:
   text=path.read_text();prefixes=dict(re.findall(r'@prefix ([\w]+): <([^>]+)>',text))
   for pre,local in re.findall(r'^([\w]+):([\w]+) a owl:NamedIndividual',text,re.M):source_individuals.add(prefixes[pre]+local)
 values=' '.join('<'+x+'>' for x in sorted(source_individuals));notin=', '.join('<'+x+'>' for x in sorted(source_individuals))
 assertion('H3_same_named_individual_set','FILTER NOT EXISTS { VALUES ?expected { '+values+' } FILTER NOT EXISTS { ?expected a owl:NamedIndividual } } FILTER NOT EXISTS { ?actual a owl:NamedIndividual . FILTER(?actual NOT IN ('+notin+')) }',configuration='h3_extended',case='Complete source and post-inference IRI sets must match in both directions, across all namespaces')
 (F/'h3-source-individuals.json').write_text(json.dumps({'method':'NamedIndividual declarations parsed from complete H3 source files before runtime; not obtained from materialized outputs','sources':h3['tbox_paths']+h3['rules_paths']+h3['abox_paths'],'count':len(source_individuals),'iris':sorted(source_individuals)},indent=2)+'\n')
 # Distinguished load/profile/inconsistency outcomes. A failure is never a
 # successful consistency result and cannot enter the benchmark.
 bad=F/'invalid-syntax.ttl';bad.write_text('@prefix f: <http://example.org/> .\nf:broken [ this is not Turtle\n')
 tests.append(dict(id='Failure_load_error',configuration='integrated',files=[str(bad.relative_to(ROOT))],enabled_rules='NONE',expected_status='NOT_EVALUATED',case='syntax load error'))
 outprofile=ttl('invalid-owl2dl','''f:Temporal a owl:Ontology . f:before a owl:ObjectProperty, owl:TransitiveProperty ; owl:propertyDisjointWith f:after . f:after a owl:ObjectProperty .''')
 tests.append(dict(id='Failure_profile_invalid',configuration='integrated',files=[outprofile],enabled_rules='NONE',expected_status='NOT_EVALUATED',case='OWL2DL global restriction violation'))
 inconsistent=ttl('inconsistent','''f:Ontology a owl:Ontology . f:C1 a owl:Class ; owl:disjointWith f:C2 . f:C2 a owl:Class . f:Individual a owl:NamedIndividual, f:C1, f:C2 .''')
 tests.append(dict(id='Failure_inconsistent',configuration='integrated',files=[inconsistent],enabled_rules='NONE',expected_status='INCONSISTENT',case='disjoint types contradiction'))
 for name,body,expected in [('cycle','f:E1 a owl:NamedIndividual ; c:precedes f:E2 . f:E2 a owl:NamedIndividual ; c:precedes f:E1 .',True),('acyclic','f:E1 a owl:NamedIndividual ; c:precedes f:E2 . f:E2 a owl:NamedIndividual .',False)]:
  path=ttl('temporal-'+name,body)
  assertion('Temporal_'+name,'?event c:precedes+ ?event .',expected,files=tbox+[path],enabled_rules='NONE',case='Additional explicit graph cycle validation; outside four canonical ASK')
 # Direct P3 witness on the exact product/resource identities, separate from
 # canonical SELECTs and four ASK validation mechanisms.
 p3='f:BatchA t:producesProduct f:WhitePaint ; t:hasPlan f:BatchA_Plan ; i:hasProcess f:BatchA_Process . f:BatchA_Plan t:planProduces f:WhitePaint ; i:includesActivity f:BatchA_Op2 ; i:assignsResource f:PearlMill_01 . f:BatchA_Op2 i:partOfProcess f:BatchA_Process ; i:requiresResource f:PearlMill_01 ; i:usesResource f:PearlMill_01 .'
 assertion('P3_WhitePaint_PearlMill_formal_use',p3,mechanism='R09',competency_question='P3',case='Exact formal witness: WhitePaint is the represented product and PearlMill_01 the required/assigned resource used by BatchA_Op2 after R09, not an industrial observation')
 assertion('P3_WhitePaint_PearlMill_without_R09',p3,False,enabled_rules='OWL_ONLY',mechanism='R09',competency_question='P3',case='Same source identities and snapshot without SWRL: the formal usesResource witness is absent; no asserted conclusion of use is supplied')
 from build_approved_contract_fixtures import append_cases
 append_cases(ttl,assertion,tests,tbox,qbody)
 # ap is used only in H3 assertion queries.
 for p in A.glob('H3*.sparql'):
  p.write_text('PREFIX ap: <http://w3id.org/protys/ontology/iso10303#>\nPREFIX prod: <http://w3id.org/protys/ontology/product#>\n'+p.read_text())
 (F/'tests.json').write_text(json.dumps({'schema_version':'1.0','derivation':'Authored input predicates and independent arithmetic; no result exported as expectation','assertions':tests},ensure_ascii=False,indent=2)+'\n')
 print(str(len(tests))+' authored assertions;22 SWRL each have positive and negative cases;4 ASK have independent closed snapshots')

if __name__=='__main__':main()
