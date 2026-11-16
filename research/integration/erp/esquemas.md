# Esquemas reales de manufactura en ADempiere y Odoo

Fuentes: ADempiere 3.9.4 LTS (semilla oficial, cliente GardenWorld, `ad_client_id=11`) y
Odoo Server 17.0-20260908 (módulo `mrp` con datos de demostración). Las dos bases se
levantan con `docker/integration/docker-compose.erp.yml`.

Relevamiento por SQL directo con el rol de sólo lectura `protys_ro`: `scripts/relevar-esquemas.sh`,
salida cruda en `salidas/esquemas-sql.txt` (todas las columnas y tipos de cada tabla).
Relevamiento por la API: se registraron los conectores con `POST /api/erp/connectors`
(usuario `protys_ro`), `/test` devolvió `connected: true` en los dos, y
`POST /api/erp/connectors/{id}/introspect` dejó la salida cruda en `salidas/introspect-adempiere.json`
y `salidas/introspect-odoo.json`. El cruce con SQL está en `salidas/introspect-vs-sql.txt`: las 21
tablas relevadas tienen la misma cantidad de columnas por los dos caminos.

Tamaño total (`information_schema.tables`): ADempiere, esquema `adempiere`, 992 tablas base y 185
vistas (1177 en total, 27 858 columnas). Odoo, esquema `public`, 336 tablas base y 1 vista
(337 en total, 3354 columnas). `/introspect` lista sólo tablas base (992 y 336), así que coincide.

## ADempiere (esquema `adempiere`)

| Concepto | Tabla | Columnas usadas | Filas totales | Filas relevantes |
|---|---|---|---|---|
| Producto | `m_product` | `m_product_id`, `value`, `name`, `producttype` | 62 | 13 en listas de materiales |
| Plan de producto | `pp_product_planning` | `m_product_id`, `pp_product_bom_id`, `ad_workflow_id`, `s_resource_id` | 219 | 6 con lista y flujo |
| Lista de materiales | `pp_product_bom` | `pp_product_bom_id`, `m_product_id`, `value`, `name`, `bomtype`, `bomuse` | 8 | 8 |
| Componente | `pp_product_bomline` | `pp_product_bom_id`, `m_product_id`, `line`, `qtybom`, `componenttype` | 30 | 30 |
| Ruta / proceso | `ad_workflow` | `ad_workflow_id`, `value`, `name`, `workflowtype`, `s_resource_id` | 68 | 4 con `workflowtype='M'` |
| Operación | `ad_wf_node` | `ad_wf_node_id`, `ad_workflow_id`, `value`, `name`, `s_resource_id`, `duration`, `setuptime` | 305 | 9 en flujos `M` |
| Precedencia | `ad_wf_nodenext` | `ad_wf_node_id`, `ad_wf_next_id` | 238 | 5 en flujos `M` |
| Recurso | `s_resource` + `s_resourcetype` | `s_resource_id`, `value`, `name`, `manufacturingresourcetype`, `ismanufacturingresource` | 11 | 10 productivos: 2 plantas (`PT`), 7 centros de trabajo (`WC`), 1 línea (`PL`) |
| Orden de producción | `pp_order` | `pp_order_id`, `documentno`, `docstatus`, `m_product_id`, `pp_product_bom_id`, `ad_workflow_id`, `s_resource_id`, `qtyordered` | 2 | 2 (en borrador, `DR`) |
| Operación de la orden | `pp_order_node` | `pp_order_node_id`, `pp_order_id`, `ad_wf_node_id`, `s_resource_id` | 2 | 2 |
| Componente de la orden | `pp_order_bomline` | `pp_order_id`, `m_product_id`, `qtyrequired` | 4 | 4 |
| Organización (revisión) | `ad_org` | `ad_org_id`, `ad_client_id`, `name` | 10 en los clientes 0 y 11 | 9 de GardenWorld (HQ, Store Central, Furniture, Fertilizer, cuatro Store y la sumaria Stores); la `*` (id 0) es del sistema |
| Almacén (revisión, no mapeado) | `m_warehouse` | `m_warehouse_id`, `ad_org_id`, `name` | 11 de GardenWorld | — |

Flujos de manufactura de la demo: *Patio Chair* (5 operaciones en secuencia: Assembly → Paint →
Drying → Chrome Process → Inspection Process), *Lawn Fertilizer* (Mixed → Inspection the Mixed),
*Fertilizer Packing Process* (1 operación) y *Patio Furniture Set* (1 operación).
Las dos órdenes (8000 y 8001) fabrican *Fertilizer #50* y *Fertilizer #70* con el flujo de envasado.

## Odoo (esquema `public`)

| Concepto | Tabla | Columnas usadas | Filas |
|---|---|---|---|
| Producto (plantilla) | `product_template` | `id`, `name` (jsonb, se lee `en_US`), `type` | 41 |
| Producto (variante) | `product_product` | `id`, `product_tmpl_id`, `default_code` | 44 |
| Lista de materiales (+ ruta) | `mrp_bom` | `id`, `product_tmpl_id`, `product_id`, `code`, `type`, `product_qty` | 8 (7 `normal`, 1 `phantom`) |
| Componente | `mrp_bom_line` | `id`, `bom_id`, `product_id`, `product_qty`, `operation_id` | 17 (13 productos distintos) |
| Operación | `mrp_routing_workcenter` | `id`, `bom_id`, `name`, `sequence`, `workcenter_id`, `time_cycle_manual` | 5, en 3 listas |
| Centro de trabajo | `mrp_workcenter` + `resource_resource` | `id`, `resource_id`, `name` (de `resource_resource`), `code` | 3 |
| Orden de producción | `mrp_production` | `id`, `name`, `state`, `product_id`, `bom_id`, `product_qty` | 4 (1 borrador, 2 confirmadas, 1 terminada) |
| Orden de trabajo | `mrp_workorder` | `id`, `production_id`, `operation_id`, `workcenter_id`, `name` | 3 |
| Compañía (revisión) | `res_company` | `id`, `name` | 2 (San Francisco y Chicago) |
| Compañía de la lista | `mrp_bom.company_id` | `company_id` | las 8 listas en la compañía 1 |
| Almacén (revisión, no mapeado) | `stock_warehouse` | `id`, `company_id`, `code`, `name` | 2 |

Listas con ruta: *Table* (Assembly), *Table Top* (Manual Assembly) y *Drawer* `SEC-ASSEM`
(Packing → Testing → Long time assembly). Las otras cinco listas no tienen operaciones.

## Observaciones
- En Odoo 17 la ruta no es una entidad propia: las operaciones cuelgan de la lista de materiales
  (`mrp_routing_workcenter.bom_id`). En ADempiere la ruta es un flujo (`ad_workflow`) y el plan del
  producto (`pp_product_planning`) une lista, flujo y planta.
- ADempiere no registra en qué operación se consume cada componente. Odoo sí, pero de forma
  opcional (`mrp_bom_line.operation_id`): en la demo sólo 2 de las 17 líneas lo tienen.
- Las órdenes de la demo no tienen lotes de producto terminado asociados.
- En Odoo, `product_template.name` es `jsonb` (traducible); se lee la clave `en_US`. Los demás
  nombres usados son `character varying`.
- **Revisión (16-11-2026).** Se agregaron `ad_org`, `m_warehouse`, `res_company` y `stock_warehouse` para las filas Organización y Almacén de las Tablas 5.8 y 5.9. Las columnas usadas aparecen en la salida de `/introspect` de la corrida (`salidas/e2e/05-introspect-*.json`); las filas se contaron por SQL sobre las bases de demostración. Los flujos de manufactura de ADempiere tienen `ad_org_id = 0` (organización `*`).
