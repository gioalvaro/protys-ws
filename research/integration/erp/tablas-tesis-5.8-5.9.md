# Tablas 5.8 y 5.9 de la tesis (versión candidata del 16-10-2026)

Copiadas del Capítulo 5 para contrastarlas con `correspondencias.md`. En la tesis figuran como «correspondencias conceptuales previstas», con estado «Diseño documental» en todas las filas. Se van a reescribir con lo que salga de esta fase.

## Tabla 5.8 — ADempiere → PROTYS(KB)

| Entidad | Fuente conceptual ADempiere | Destino conceptual | Condiciones por comprobar |
|---|---|---|---|
| Producto | M_Product | Producto o material | Categoría, unidad, identificador y versión |
| Producción | Orden y líneas de producción | Proceso y actividad | Claves de unión y cantidades |
| Composición | Lista y componentes de materiales | Especificación de componentes | Jerarquía, cantidad y unidad |
| Organización | AD_Org | Unidad empresarial | Identidad y alcance organizacional |
| Almacén | M_Warehouse | Recurso o instalación | Tipo, pertenencia y capacidad |

## Tabla 5.9 — Odoo → PROTYS(KB)

| Entidad | Fuente conceptual Odoo | Destino conceptual | Condiciones por comprobar |
|---|---|---|---|
| Producto | product_template / product_product | Producto o material | Identidad plantilla/variante, unidad y versión |
| Producción | mrp_production / mrp_workorder | Proceso y actividad | Claves, tiempos y centros de trabajo |
| Composición | mrp_bom / mrp_bom_line | Especificación de componentes | Jerarquía, cantidad y unidad |
| Organización | res_company | Unidad empresarial | Identidad y relación con entidades externas |
| Almacén | stock_warehouse | Recurso o instalación | Código, pertenencia y alcance |

## Contraste preliminar (hecho en el chat de la tesis) — reemplazado por el de abajo

Quedó superado por el contraste revisado. Correcciones respecto del preliminar: Composición no
está cubierta del todo (faltan cantidad y unidad); Producción en ADempiere no sale de "orden y
líneas" sino de la orden y sus nodos (las líneas de la orden no se mapean); en ninguna fila se
comprobaron todavía las "condiciones por comprobar" de la tesis, que se detallan abajo; y E7 Planta
y E8/E9 no son del todo nuevas (E8/E9 son la "Producción" de la tesis).

## Contraste revisado (Fase 1, 16-11-2026)

Referencias E/R: `correspondencias.md`. Mapeos ejecutados: `docker/integration/mappings/`.

| Fila de la tesis | Destino conceptual de la tesis | Qué se mapeó (ADempiere / Odoo) | Clase o propiedad de `ontologies/` | Condiciones por comprobar de la tesis | Estado |
|---|---|---|---|---|---|
| Producto | Producto o material | E1 producto fabricado (`m_product` con lista / `product_product` con lista); E2 componente (producto referido por una línea de lista) | `c:Product` (≡ `prod:Product`); `prod:Production_Component`, `r:Material` | Identificador: sí (IRI con la clave primaria). Nombre: sí. Categoría, unidad y versión: **no** se mapean. Plantilla/variante (Odoo): el individuo es la variante (`product_product`); la plantilla no se representa aparte | Cubierta en parte |
| Producción | Proceso y actividad | E8 orden (`pp_order` / `mrp_production`); E9 operación de la orden (`pp_order_node` / `mrp_workorder`), con R12 y R13. El proceso y la actividad (E4, E5) salen del flujo o la ruta, no de la orden | `i:ProductionOrder`, `i:WorkOrder`; `i:ManufacturingProcess`, `i:ProcessOperation` | Claves de unión: sí (R12, R13). Centros de trabajo: sí (R13). Tiempos: sólo la duración estándar de la operación (R11); los tiempos reales de la orden **no**. Cantidades de la orden: **no**. Líneas de la orden (`pp_order_bomline`): **no** | Cubierta en parte |
| Composición | Especificación de componentes | R7 producto → componente (`pp_product_bomline` / `mrp_bom_line`) | `prod:isComposedOF` | Jerarquía: un nivel por lista (producto → componente directo). Cantidad y unidad: **no**: los módulos generales no tienen propiedad para eso | Cubierta en parte |
| Organización | Unidad empresarial | E10 (`ad_org` del cliente GardenWorld / `res_company`); R14 empresa → proceso sólo en Odoo | `c:Enterprise` (refinada por el Módulo de Empresa, que no tiene una subclase para organización) | Identidad: sí (IRI por clave). Alcance organizacional (ADempiere): **no**: los flujos de la demo son de la organización comodín `*`. Relación con entidades externas (Odoo): **no** | Mapeada, sin alcance |
| Almacén | Recurso o instalación | Nada | Ninguna: no hay clase de almacén en los módulos generales (`paint-ext:Warehouse` es del caso de pintura; `i:ManufacturingFacility` es una instalación de manufactura) | — | **No mapeada**, por falta de clase |
| (no estaba en la tesis) | — | E3 plan, E6 centro de trabajo, E7 planta (sólo ADempiere); R2–R6, R8–R11 | `i:ProcessPlan`, `i:ManufacturingResource`, `i:ManufacturingFacility`, propiedades de `iso15531`, `core` | — | Nuevo |

Para reescribir las tablas de la tesis: la columna «Destino conceptual» puede pasar a nombrar la
clase de `ontologies/`, y el estado «Diseño documental» cambia por el de esta tabla. Almacén queda
como correspondencia prevista sin clase en la red.
