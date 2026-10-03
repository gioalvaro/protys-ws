# Catálogo canónico de mecanismos
22 reglas SWRL y cuatro validaciones ASK. Las clases se añaden sobre los mismos individuos; no se generan individuos nuevos. La ejecución y sus resultados se documentan por separado.

## R01 — GHG de electricidad por operación
Tipo: SWRL. kg CO2e =kWh×kg CO2e/kWh; entrada numérica única y factor seleccionado único, preparados por CNUM.
```
i:ProcessOperation(?op) ^
t:NumericInputValid(?op) ^
i:consumesEnergyKWh(?op, ?kwh) ^
t:hasEmissionFactor(?op, ?factor) ^
t:ElectricityEmissionFactor(?factor) ^
t:factorUnit(?factor, "kg CO2e/kWh"^^xsd:string) ^
t:factorValue(?factor, ?f) ^
swrlb:greaterThanOrEqual(?kwh, 0) ^
swrlb:greaterThanOrEqual(?f, 0) ^
swrlb:multiply(?ghg, ?kwh, ?f)
→
e:hasGHGImpactKgCO2e(?op, ?ghg)
```

## R02 — Presencia de inventario de agua del proceso
Tipo: SWRL. Presencia de flujo de agua; sin suma cuantitativa.
```
i:ProcessOperation(?op) ^
i:consumesWaterL(?op, ?water) ^
i:partOfProcess(?op, ?process) ^
a:hasInventoryRepresentation(?process, ?lci)
→
e:hasInventoryFlow(?lci, c:WaterFlow)
```

## R03 — Vincular insumo comprado al lote mediante su representación LCI
Tipo: SWRL. IRI del flujo y del lote.
```
i:Lot(?lot) ^
i:producesLot(?order, ?lot) ^
i:hasProcess(?lot, ?process) ^
a:hasInventoryRepresentation(?process, ?lci) ^
e:UnitProcess(?lci) ^
e:hasInventoryFlow(?lci, ?flow) ^
e:ProductFlow(?flow)
→
a:hasRecordedInputFlowForLot(?lot, ?flow)
```

## R04 — Indicador por umbral explícito de la misma unidad y categoría
Tipo: SWRL. Indicador y ThresholdRecord explícito con la misma unidad y categoría; valor, unidad y categoría del mismo registro; frontera estricta >.
```
e:ImpactIndicator(?indicator) ^
t:hasThreshold(?indicator, ?record) ^
t:ThresholdRecord(?record) ^
e:hasIndicatorValue(?indicator, ?value) ^
t:unit(?indicator, ?unit) ^
t:indicatorCategory(?indicator, ?category) ^
t:thresholdValue(?record, ?threshold) ^
t:thresholdUnit(?record, ?unit) ^
t:thresholdCategory(?record, ?category) ^
swrlb:greaterThan(?value, ?threshold)
→
c:HighImpactIndicator(?indicator)
```

## R05 — Vincular al lote los indicadores de sus procesos, sin sumar
Tipo: SWRL. IRI del indicador del proceso; asociación sin suma.
```
i:Lot(?lot) ^
i:hasProcess(?lot, ?process) ^
a:hasInventoryRepresentation(?process, ?lci) ^
e:hasImpact(?lci, ?indicator)
→
e:hasImpact(?lot, ?indicator)
```

## R06 — Asociar factor al flujo del material correspondiente
Tipo: SWRL. IRI del factor del material.
```
e:ProductFlow(?flow) ^
e:refersToMaterial(?flow, ?material) ^
t:hasEmissionFactor(?material, ?factor)
→
t:usesEmissionFactor(?flow, ?factor)
```

## R07 — Datos ambientales obligatorios ausentes del proceso
Tipo: SPARQL ASK. true means missing required record in the closed snapshot, not OWL inconsistency or physical contamination.
```
PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX c: <http://w3id.org/protys/ontology/core#>
PREFIX a: <http://w3id.org/protys/ontology/alignment#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
PREFIX p: <http://w3id.org/protys/ontology/paint-ext#>
PREFIX r: <http://w3id.org/protys/ontology/resource#>
PREFIX f: <http://w3id.org/protys/fixture#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
ASK { ?process a i:ManufacturingProcess . FILTER NOT EXISTS { ?process t:hasInventoryRepresentation ?lci . ?indicator a e:ImpactIndicator ; e:computedForProcess ?lci ; e:indicatorValue ?value . } }
```

## R08 — Cantidad de unidad funcional del lote
Tipo: SWRL. Cantidad en unidad funcional declarada; no conversión automática.
```
i:Lot(?lot) ^
i:functionalUnit(?lot, ?unit) ^
i:producesQuantity(?lot, ?qty)
→
e:hasFunctionalUnitQuantity(?lot, ?qty)
```

## R09 — Uso del recurso requerido y asignado en el mismo plan
Tipo: SWRL. IRI de recurso/operación/plan.
```
i:ProcessPlan(?plan) ^
i:assignsResource(?plan, ?resource) ^
i:includesActivity(?plan, ?op) ^
i:requiresResource(?op, ?resource)
→
i:usesResource(?op, ?resource)
```

## R10 — Registrar estado propagable del mismo lote
Tipo: SWRL. Estado textual del mismo lote.
```
i:Lot(?lot) ^
i:hasQualityStatus(?lot, ?status)
→
i:propagatedQualityStatus(?lot, ?status)
```

## R11 — Razón rendimiento sobre duración positiva de operación
Tipo: SWRL. %/min; razón de rendimiento másico sobre duración.
```
i:ProcessOperation(?op) ^
i:yield(?op, ?yield) ^
i:duration(?op, ?duration) ^
swrlb:greaterThan(?duration, 0) ^
swrlb:multiply(?preciseYield, ?yield, "1.0000000000000000"^^xsd:decimal) ^
swrlb:divide(?efficiency, ?preciseYield, ?duration)
→
a:hasEfficiency(?op, ?efficiency)
```

## R12 — Capacidad FineGrinding por operación de molienda explícita
Tipo: SWRL. Capacidad de molienda explícita; no umbral numérico.
```
i:EquipmentResource(?resource) ^
t:FineGrindingOperation(?op) ^
i:requiresResource(?op, ?resource)
→
i:hasCapability(?resource, a:FineGrinding)
```

## R13 — Precedencia de eventos MES del mismo lote y proceso
Tipo: SWRL. Segundos desde origen común, mismo lote/proceso.
```
i:ManufacturingData(?e1) ^
i:ManufacturingData(?e2) ^
t:forLot(?e1, ?lot) ^
t:forLot(?e2, ?lot) ^
t:eventForProcess(?e1, ?process) ^
t:eventForProcess(?e2, ?process) ^
t:eventTimeSeconds(?e1, ?t1) ^
t:eventTimeSeconds(?e2, ?t2) ^
swrlb:lessThan(?t1, ?t2)
→
i:precedesEvent(?e1, ?e2)
```

## R14 — Operador responsable de la operación de su orden
Tipo: SWRL. IRI de operador responsable.
```
i:WorkOrder(?order) ^
i:executedBy(?order, ?operator) ^
i:HumanResource(?operator) ^
i:executesActivity(?order, ?op)
→
i:responsibleOperator(?op, ?operator)
```

## R15 — Marcar indisponibilidad temporal por mantenimiento ACTIVE en la instantánea
Tipo: SWRL. Indisponibilidad temporal sólo con mantenimiento ACTIVE de la instantánea; no registro histórico/programado.
```
i:ManufacturingResource(?resource) ^
i:hasMaintenance(?resource, ?maintenance) ^
t:maintenanceStatus(?maintenance, "ACTIVE"^^xsd:string)
→
i:temporarilyUnavailable(?resource)
```

## R16 — Entrada material ausente de la operación
Tipo: SPARQL ASK. true means missing required record in the closed snapshot, not OWL inconsistency or physical contamination.
```
PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX c: <http://w3id.org/protys/ontology/core#>
PREFIX a: <http://w3id.org/protys/ontology/alignment#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
PREFIX p: <http://w3id.org/protys/ontology/paint-ext#>
PREFIX r: <http://w3id.org/protys/ontology/resource#>
PREFIX f: <http://w3id.org/protys/fixture#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
ASK { ?operation a i:ProcessOperation . FILTER NOT EXISTS { ?operation i:hasInput ?input . } }
```

## R17 — Requerir limpieza entre lotes consecutivos de familia distinta
Tipo: SWRL. Código entero de categoría, mismo equipo y par consecutivo.
```
i:Lot(?l1) ^
i:Lot(?l2) ^
i:processedOn(?l1, ?equipment) ^
i:processedOn(?l2, ?equipment) ^
t:nextLot(?l1, ?l2) ^
t:productFamilyCode(?l1, ?f1) ^
t:productFamilyCode(?l2, ?f2) ^
swrlb:notEqual(?f1, ?f2)
→
i:requiresCleaningBetween(?l1, ?l2)
```

## R18 — Marcar lote candidato a reproceso por no conformidad
Tipo: SWRL. Clase candidato a reproceso; sin ejecución de operación nueva.
```
i:Lot(?lot) ^
i:hasNonConformity(?lot, ?nonconformity)
→
i:requiresReprocess(?lot)
```

## R19 — Medición ausente del control de calidad
Tipo: SPARQL ASK. true means missing required record in the closed snapshot, not OWL inconsistency or physical contamination.
```
PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX c: <http://w3id.org/protys/ontology/core#>
PREFIX a: <http://w3id.org/protys/ontology/alignment#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
PREFIX p: <http://w3id.org/protys/ontology/paint-ext#>
PREFIX r: <http://w3id.org/protys/ontology/resource#>
PREFIX f: <http://w3id.org/protys/fixture#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
ASK { ?operation a i:Quality_Control_Activity . FILTER NOT EXISTS { ?operation i:hasMeasurement ?measurement . } }
```

## R20 — Puente flujo y operación de consumo del mismo proceso
Tipo: SWRL. IRI de flujo/material/operación del mismo proceso.
```
i:ProcessOperation(?op) ^
i:consumesMaterial(?op, ?material) ^
i:partOfProcess(?op, ?process) ^
a:hasInventoryRepresentation(?process, ?lci) ^
e:ProductFlow(?flow) ^
e:hasInventoryFlow(?lci, ?flow) ^
e:refersToMaterial(?flow, ?material) ^
t:belongsToProcess(?flow, ?process)
→
c:bridgesFlowToActivity(?flow, ?op)
```

## R21 — Contribución eléctrica identificable por operación del lote
Tipo: SWRL. Contribución identificada por IRI de operación, evitando colapso de literales iguales.
```
i:Lot(?lot) ^
t:NumericInputValid(?op) ^
i:hasActivity(?lot, ?op) ^
i:partOfProcess(?op, ?process) ^
i:hasProcess(?lot, ?process) ^
t:hasEmissionFactor(?op, ?factor) ^
t:factorUnit(?factor, "kg CO2e/kWh"^^xsd:string) ^
e:hasGHGImpactKgCO2e(?op, ?ghg)
→
t:hasGHGContribution(?lot, ?op)
```

## R22 — Hotspot vinculado al mismo proceso y recurso cuello de botella
Tipo: SWRL. Clase hotspot; cuello de botella declarado como entrada.
```
c:HighImpactIndicator(?indicator) ^
e:computedForProcess(?indicator, ?lci) ^
a:hasInventoryRepresentation(?process, ?lci) ^
i:partOfProcess(?op, ?process) ^
i:requiresResource(?op, ?resource) ^
c:BottleneckResource(?resource)
→
c:Hotspot(?resource)
```

## R23 — Razón por operación e indicador vinculados, sin normalización
Tipo: SWRL. min/unidad del indicador; razón por pareja, sin normalización.
```
i:Lot(?lot) ^
i:WorkOrder(?order) ^
i:producesLot(?order, ?lot) ^
i:hasActivity(?lot, ?op) ^
i:hasProcess(?lot, ?process) ^
i:partOfProcess(?op, ?process) ^
a:hasInventoryRepresentation(?process, ?lci) ^
i:cycleTime(?op, ?time) ^
e:computedForProcess(?indicator, ?lci) ^
e:indicatorValue(?indicator, ?value) ^
t:forOperation(?ratio, ?op) ^
t:forIndicator(?ratio, ?indicator) ^
swrlb:greaterThan(?value, 0) ^
swrlb:multiply(?preciseTime, ?time, "1.0000000000000000"^^xsd:decimal) ^
swrlb:divide(?result, ?preciseTime, ?value)
→
t:operationIndicatorRatio(?ratio, ?result)
```

## R24 — Marcar brecha de cobertura desde faltante ambiental detectado
Tipo: SWRL. Clase brecha de cobertura desde core:MissingEnvironmentalData.
```
c:MissingEnvironmentalData(?process)
→
c:CoverageGap(?process)
```

## R25 — Marcar factor modificado que requiere recálculo
Tipo: SWRL. Clase requiere recálculo; sin recalcular el valor.
```
t:EmissionFactor(?factor) ^
c:hasVersion(?factor, ?version) ^
c:modifiedInVersion(?factor, ?version)
→
c:RequiresRecomputeIndicators(?factor)
```

## R26 — Registro de limpieza ausente en el intervalo y equipo correspondientes
Tipo: SPARQL ASK. true means missing required record in the closed snapshot, not OWL inconsistency or physical contamination.
```
PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX c: <http://w3id.org/protys/ontology/core#>
PREFIX a: <http://w3id.org/protys/ontology/alignment#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
PREFIX p: <http://w3id.org/protys/ontology/paint-ext#>
PREFIX r: <http://w3id.org/protys/ontology/resource#>
PREFIX f: <http://w3id.org/protys/fixture#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
ASK { ?lotA i:requiresCleaningBetween ?lotB ; i:processedOn ?equipment .
?lotB i:processedOn ?equipment .
?lotA t:endTimeSeconds ?endA .
FILTER(isNumeric(?endA))
FILTER NOT EXISTS { ?lotA t:endTimeSeconds ?pairEndOther .
FILTER(!isNumeric(?pairEndOther) || ?pairEndOther != ?endA) }
?lotB t:startTimeSeconds ?startB .
FILTER(isNumeric(?startB))
FILTER NOT EXISTS { ?lotB t:startTimeSeconds ?pairStartOther .
FILTER(!isNumeric(?pairStartOther) || ?pairStartOther != ?startB) }
FILTER(?endA <= ?startB)
FILTER NOT EXISTS { ?clean a i:CleaningOperation ; t:cleanedResource ?equipment .
?clean t:startTimeSeconds ?cleanStart .
FILTER(isNumeric(?cleanStart))
FILTER NOT EXISTS { ?clean t:startTimeSeconds ?cleanStartOther .
FILTER(!isNumeric(?cleanStartOther) || ?cleanStartOther != ?cleanStart) }
?clean t:endTimeSeconds ?cleanEnd .
FILTER(isNumeric(?cleanEnd))
FILTER NOT EXISTS { ?clean t:endTimeSeconds ?cleanEndOther .
FILTER(!isNumeric(?cleanEndOther) || ?cleanEndOther != ?cleanEnd) }
FILTER(?cleanStart >= ?endA && ?cleanEnd <= ?startB && ?cleanEnd >= ?cleanStart) } }
```
