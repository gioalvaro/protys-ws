"""Declare common case terms and independent standard-specific profiles."""
from pathlib import Path
from lxml import etree as E
ROOT=Path(__file__).resolve().parents[2]
NS={'rdf':'http://www.w3.org/1999/02/22-rdf-syntax-ns#','rdfs':'http://www.w3.org/2000/01/rdf-schema#','owl':'http://www.w3.org/2002/07/owl#','xsd':'http://www.w3.org/2001/XMLSchema#'}
T='http://w3id.org/protys/ontology/research#'; I='http://w3id.org/protys/ontology/iso15531#'; C='http://w3id.org/protys/ontology/core#'; ENV='http://w3id.org/protys/ontology/iso14040#'
def q(p,n):return '{'+NS[p]+'}'+n
def declaration(root,kind,uri,sub=None,datatype=None):
 n=E.SubElement(root,q('owl',kind));n.set(q('rdf','about'),uri)
 if sub:E.SubElement(n,q('rdfs','subClassOf')).set(q('rdf','resource'),sub)
 if datatype:E.SubElement(n,q('rdfs','range')).set(q('rdf','resource'),NS['xsd']+datatype)
 return n
def profile(name,imports):
 root=E.Element(q('rdf','RDF'),nsmap=NS)
 o=E.SubElement(root,q('owl','Ontology'));o.set(q('rdf','about'),T[:-1]+'/'+name)
 E.SubElement(o,q('owl','versionInfo')).text='2.0.0'
 for imp in imports:E.SubElement(o,q('owl','imports')).set(q('rdf','resource'),imp)
 return root
def main():
 common=profile('common',[])
 for name in ['RatioObservation','Measurement','Supplier','Subload','ThresholdRecord','NumericInputValid']:declaration(common,'Class',T+name)
 declaration(common,'Class',T+'EmissionFactor')
 for name in ['ElectricityEmissionFactor','MaterialEmissionFactor']:declaration(common,'Class',T+name,sub=T+'EmissionFactor')
 for name in ['hasGHGContribution','forOperation','forIndicator','nextLot','cleanedResource','belongsToProcess','forLot','producesProduct','hasPlan','planProduces','providedBy','eventForProcess','hasFormulation','hasStageResource','hasMeasurement','subloadOfOperation','componentOf','flowRefersToProduct','hasInventoryRepresentation','inputFlowForResource','facilityInBoundary','indicatorForLot','qualityForLot','allocationForOperation','inventoryForPlan','inventoryForProcess','hasEmissionFactor','usesEmissionFactor','hasThreshold']:
  declaration(common,'ObjectProperty',T+name)
 datatypes={'operationIndicatorRatio':'decimal','startTimeSeconds':'decimal','endTimeSeconds':'decimal','eventTimeSeconds':'decimal','inputMassKg':'decimal','outputMassKg':'decimal','registeredLossKg':'decimal','stageIndex':'integer','energyKWh':'decimal','ghgKgCO2e':'decimal','waterL':'decimal','materialQuantityKg':'decimal','materialFactorKgCO2ePerKg':'decimal','waterFootprintLPerKg':'decimal','powerKW':'decimal','capacityLiters':'decimal','capacityUnitsPerMinute':'decimal','availabilityPercent':'decimal','yieldPercent':'decimal','oeePercent':'decimal','unit':'string','lifecycleStage':'string','scenarioRole':'string','productFamilyCode':'integer','outputVolumeL':'decimal','densityKgPerL':'decimal','subloadVolumeL':'decimal','subloadDurationMinutes':'decimal'}
 for name,dtype in datatypes.items():declaration(common,'DatatypeProperty',T+name,datatype=dtype)
 declaration(common,'DatatypeProperty',C+'modifiedInVersion',datatype='string')
 declaration(common,'DatatypeProperty',T+'factorValue',datatype='decimal')
 declaration(common,'DatatypeProperty',T+'factorUnit',datatype='string')
 declaration(common,'DatatypeProperty',T+'qualityStatus',datatype='string')
 for name,datatype in [('thresholdValue','decimal'),('thresholdUnit','string'),('thresholdCategory','string')]:
  n=declaration(common,'DatatypeProperty',T+name,datatype=datatype)
  E.SubElement(n,q('rdf','type')).set(q('rdf','resource'),NS['owl']+'FunctionalProperty')
  E.SubElement(n,q('rdfs','domain')).set(q('rdf','resource'),T+'ThresholdRecord')
 declaration(common,'DatatypeProperty',T+'indicatorCategory',datatype='string')
 declaration(common,'DatatypeProperty',T+'maintenanceStatus',datatype='string')
 manufacturing=profile('manufacturing',['http://w3id.org/protys/ontology/iso15531'])
 declaration(manufacturing,'Class',T+'FineGrindingOperation',sub=I+'ProcessOperation')
 declaration(manufacturing,'Class',C+'BottleneckResource',sub=I+'EquipmentResource')
 declaration(manufacturing,'Class',I+'Quality_Control_Activity',sub=I+'ProcessOperation')
 declaration(manufacturing,'Class',I+'CleaningOperation',sub=I+'ProcessOperation')
 declaration(manufacturing,'ObjectProperty',I+'hasMeasurement')
 environmental=profile('environmental',['http://w3id.org/protys/ontology/iso14040'])
 declaration(environmental,'Class',T+'EnvironmentalEnergyRecord')
 for filename,root in [('research-profile.owl',common),('manufacturing-profile.owl',manufacturing),('environmental-profile.owl',environmental)]:E.ElementTree(root).write(str(ROOT/'research/model'/filename),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 path=ROOT/'ontologies/iso14040-module.owl';tree=E.parse(str(path));root=tree.getroot()
 if not root.xpath('./owl:DatatypeProperty[@rdf:about="'+ENV+'energyInputKWh"]',namespaces=NS):declaration(root,'DatatypeProperty',ENV+'energyInputKWh',datatype='decimal')
 for n in root.xpath('./owl:ObjectProperty[@rdf:about="'+ENV+'usesCharacterizationFactor"]/rdfs:domain',namespaces=NS):n.getparent().remove(n)
 annotations={ENV+'hasImpact':'Asocia un proceso o lote con un indicador de impacto de su proceso (R05); la asociación no calcula una suma. Las contribuciones eléctricas se identifican por operación con research:hasGHGContribution y se agregan en consultas.',ENV+'usesCharacterizationFactor':'Asocia un flujo de inventario o un indicador con su factor de caracterización; la fabricación de materias primas queda fuera del inventario eléctrico de Q06.'}
 for uri_,comment in annotations.items():
  for n in root.xpath('./*[@rdf:about="'+uri_+'"]/rdfs:comment',namespaces=NS):n.text=comment
 tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 for filename,annotations in {'iso15531-module.owl':{I+'cycleTime':'Duración de ciclo de una operación en minutos (R23); R12 utiliza el tipo explícito FineGrindingOperation.',I+'propagatedQualityStatus':'Estado de calidad registrado como propagable sobre el mismo lote (R10); no crea ni modifica el producto final.',I+'productFamily':'Nombre legible de la familia del lote. El código entero research:productFamilyCode representa la misma categoría para comparación operativa en R17 con el motor declarado.',I+'precedesEvent':'Precedencia de eventos MES con marcas numéricas en segundos desde un origen común y pertenecientes al mismo lote y proceso (R13).'},'core-concepts.owl':{C+'hasVersion':'Versión vigente de un término o entidad (consulta Q21 y regla R25); P5 corresponde al producto generado por un plan de proceso.',C+'hasPriorVersion':'Versión anterior de un término o entidad, usada por Q21; no redefine la pregunta de competencia P5.'}}.items():
  path=ROOT/'ontologies'/filename;tree=E.parse(str(path))
  for uri_,comment in annotations.items():
   for n in tree.getroot().xpath('./*[@rdf:about="'+uri_+'"]/rdfs:comment',namespaces=NS):n.text=comment
  tree.write(str(path),encoding='UTF-8',xml_declaration=True,pretty_print=True)
 print('Common profile has no ISO imports; isolated profiles each import their own standard only')
if __name__=='__main__':main()
