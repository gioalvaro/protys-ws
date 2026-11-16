# Correspondencias ERP → PROTYS(KB)

Una fila por correspondencia, ADempiere 3.9.4 (GardenWorld) y Odoo 17.0 (`mrp`, demo) en paralelo.
Los términos de la red están entre backticks y los comprueba `scripts/verificar-vocabulario.py`
contra `ontologies/*.owl` (salida en `salidas/verificar-vocabulario.txt`).

Esta tabla se armó a partir de los esquemas reales (`esquemas.md`) y del vocabulario de
`ontologies/`. El contraste con las Tablas 5.8 y 5.9 de la tesis está en `tablas-tesis-5.8-5.9.md`;
de ahí salen las filas E10 (Organización) y E11 (Almacén).

Prefijos: `c:` core, `r:` resource, `prod:` product, `i:` iso15531, todos bajo
`http://w3id.org/protys/ontology/<módulo>#`. Individuos: `http://w3id.org/protys/erp/<fuente>/<entidad>/<id>`.
Grafos nombrados: `http://w3id.org/protys/erp/adempiere` y `http://w3id.org/protys/erp/odoo`.

## Entidades → clases

| # | Concepto | ADempiere (tabla, filtro) | Odoo (tabla, filtro) | Clase PROTYS(KB) |
|---|---|---|---|---|
| E1 | Producto fabricado | `m_product` que es producto de una `pp_product_bom` | `product_product` cuya plantilla tiene `mrp_bom` | `c:Product` (≡ `prod:Product`) |
| E2 | Componente / insumo | `m_product` referido por `pp_product_bomline.m_product_id` | `product_product` referido por `mrp_bom_line.product_id` | `prod:Production_Component`, `r:Material` |
| E3 | Plan de proceso | `pp_product_planning` con `pp_product_bom_id` y `ad_workflow_id` | `mrp_bom` (la lista de materiales lleva su ruta de operaciones) | `i:ProcessPlan` |
| E4 | Proceso de manufactura (ruta) | `ad_workflow` con `workflowtype='M'` | ruta de la `mrp_bom` (en Odoo 17 no es una tabla aparte) | `i:ManufacturingProcess` |
| E5 | Operación | `ad_wf_node` de un flujo `M` | `mrp_routing_workcenter` | `i:ProcessOperation` |
| E6 | Centro de trabajo / línea | `s_resource` con `ismanufacturingresource='Y'` y tipo `WC` o `PL` | `mrp_workcenter` | `i:ManufacturingResource` |
| E7 | Planta | `s_resource` con tipo `PT` | sin equivalente en la demo | `i:ManufacturingFacility` |
| E8 | Orden de producción | `pp_order` | `mrp_production` | `i:ProductionOrder` |
| E9 | Orden de trabajo (operación de una orden) | `pp_order_node` | `mrp_workorder` | `i:WorkOrder` |
| E10 | Organización | `ad_org` del cliente GardenWorld (`ad_client_id = 11`; queda afuera la organización comodín `*`) | `res_company` | `c:Enterprise` |
| E11 | Almacén | `m_warehouse` | `stock_warehouse` | **sin mapear**: no hay clase que corresponda (ver Decisiones) |

## Relaciones y atributos → propiedades

| # | Relación | ADempiere | Odoo | Propiedad PROTYS(KB) |
|---|---|---|---|---|
| R1 | Nombre | `m_product.name`, `ad_workflow.name`, `ad_wf_node.name`, `s_resource.name` | `product_template.name->>'en_US'`, `mrp_routing_workcenter.name`, `resource_resource.name` | `c:hasName`, `rdfs:label` |
| R2 | El plan incluye la operación | `pp_product_planning.ad_workflow_id` → `ad_wf_node.ad_workflow_id` | `mrp_routing_workcenter.bom_id` | `i:includesActivity` |
| R3 | La operación pertenece al proceso | `ad_wf_node.ad_workflow_id` | `mrp_routing_workcenter.bom_id` (ruta de esa lista) | `i:partOfProcess` |
| R4 | La operación requiere un recurso | `ad_wf_node.s_resource_id` | `mrp_routing_workcenter.workcenter_id` | `i:requiresResource` |
| R5 | El plan asigna la planta | `pp_product_planning.s_resource_id` | — | `i:assignsResource` |
| R6 | El proceso produce el producto | `pp_product_planning.m_product_id` (vía el flujo del plan) | `mrp_bom.product_tmpl_id` → variante | `c:produces` |
| R7 | El producto se compone del componente | `pp_product_bomline` (lista → producto de la lista) | `mrp_bom_line` (lista → producto de la lista) | `prod:isComposedOF` |
| R8 | La operación consume el insumo | sin dato: `pp_product_bomline` no indica operación | `mrp_bom_line.operation_id` cuando no es nulo (2 de 17 líneas) | `i:hasInput` |
| R9 | Orden de secuencia de la operación | `ad_wf_node.value` (10, 20, …) | `mrp_routing_workcenter.sequence` | `i:operationSequence` |
| R10 | Precedencia entre operaciones | `ad_wf_nodenext` (nodo → siguiente) | orden por `sequence` dentro de la lista | `i:precedes` |
| R11 | Duración de la operación (min) | `ad_wf_node.duration` | `mrp_routing_workcenter.time_cycle_manual` | `i:duration` |
| R12 | La orden de trabajo ejecuta la operación | `pp_order_node.ad_wf_node_id` | `mrp_workorder.operation_id` | `i:executesActivity` |
| R13 | La orden de trabajo se ejecuta en un recurso | `pp_order_node.s_resource_id` | `mrp_workorder.workcenter_id` | `i:executedBy` |
| R14 | La empresa tiene el proceso | sin dato: los flujos de la demo son de la organización `*` (`ad_workflow.ad_org_id = 0`) | `mrp_bom.company_id` de las listas con operaciones | `c:hasProcess` |

## Decisiones y límites

- **Plan y proceso.** En ADempiere son dos registros distintos: el plan (`pp_product_planning`)
  apunta al flujo (`ad_workflow`). En Odoo 17 la ruta vive dentro de la lista de materiales, así
  que de una misma fila de `mrp_bom` salen dos individuos: `…/odoo/plan/<id>` y `…/odoo/process/<id>`.
- **Insumos por operación (R8).** Odoo lo registra en algunas líneas; ADempiere no. Por eso P1
  ("recursos que necesita un proceso") se responde con `i:requiresResource`, y los insumos se
  agregan vía `prod:isComposedOF`. No se inventa la operación de consumo para ADempiere.
- **Lotes.** Las órdenes de la demo están en borrador o confirmadas y no producen lotes. `i:Lot`
  queda sin poblar, así que Q02 tal cual (que parte de un lote) no aplica: ver las variantes en `queries/`.
- **Vocabulario del escenario de pintura.** `t:hasPlan`, `t:planProduces` y `t:producesProduct`
  son del perfil de investigación (`research/model/research-profile.owl`), no de `ontologies/`, y no
  se usan. `paint-ext:Final_Product` tampoco: es de la extensión del caso de pintura.
- **Inferencias por dominio y rango.** El rango de `i:hasInput` es `i:ManufacturingResource`, así que
  un insumo de Odoo con operación también se va a inferir recurso de manufactura. El dominio de
  `c:produces` es `c:Process`, así que cada `i:ManufacturingProcess` mapeado se infiere `c:Process`.
  Las dos cosas son consecuencias de la red, no del mapeo.
- **Recursos no productivos.** `s_resource` "Mary Consultant" (`ismanufacturingresource='N'`) queda afuera.
- **Mismo concepto, dos fuentes.** No se afirman `owl:sameAs` entre ERP: los catálogos de demostración
  no comparten productos. La consulta conjunta se apoya en las clases comunes, no en identidad de individuos.
- **Organización (E10).** `c:Enterprise` es la clase del Nivel Principal que el Módulo de Empresa
  (`enterprise-module.owl`) refina; el módulo no declara una subclase para una organización o
  compañía (sus clases son `Station`, `Cell`, `Shop`, `Factory` y `Location`, la jerarquía de
  planta). `paint-ext:Organization` existe, pero es de la extensión del caso de pintura y no se usa.
  En ADempiere no hay relación con los procesos: los flujos de manufactura de GardenWorld están en la
  organización comodín `*`, que significa "todas", así que no se afirma a qué organización pertenecen.
- **Almacén (E11), sin mapear.** Ningún módulo general de `ontologies/` tiene una clase de almacén.
  Las candidatas no corresponden: `i:ManufacturingFacility` es una instalación de manufactura y ya
  la usa la planta (E7); `enterprise:Shop` es un área de producción; `enterprise:Location` es una
  ubicación geográfica. La única clase de almacén es `paint-ext:Warehouse` (subclase de
  `enterprise:Shop`), de la extensión del caso de pintura. No se fuerza un mapeo.
- **Cantidades y unidades de la composición (R7).** `prod:isComposedOF` relaciona producto y
  componente, pero los módulos generales de la red no tienen una propiedad para la cantidad ni la
  unidad de la línea de la lista de materiales (`i:producesQuantity` es la cantidad producida de un lote,
  `i:Lot`; `paint-ext:inputQuantity` es de la extensión del caso de pintura). Esas columnas (`qtybom`/`c_uom_id`, `product_qty`/`product_uom_id`) no se mapean.
