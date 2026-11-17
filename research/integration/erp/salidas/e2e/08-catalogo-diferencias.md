# Consultas del catálogo sin y con los grafos ERP

Generado por `scripts/diferencias-catalogo.py` a partir de `03b-catalogo-sin-erp/` y `08-catalogo-con-erp/`. Misma plataforma y misma red; la única diferencia es que en la segunda corrida están materializados los dos grafos ERP y el razonamiento los incluyó.

| Consulta | Pregunta | Filas antes (sin ERP) | Filas después (con ERP) | Nuevas | Quitadas | Nuevas con individuos ERP |
|---|---|---|---|---|---|---|
| Q01 | Q01 Ranking de procesos por electricidad y emisiones acumuladas | 4 | 4 | 0 | 0 | 0 |
| Q02 | Q02 Trazabilidad lote, plan, producto, recursos requeridos e insumos | 40 | 40 | 0 | 0 | 0 |
| Q03 | Q03 Electricidad y emisiones por lote, sin fuentes de materias primas | 4 | 4 | 0 | 0 | 0 |
| Q04 | Q04 Recursos requeridos e insumos materiales por proceso | 40 | 55 | 15 | 0 | 15 |
| Q05 | Q05 Materiales según factores hipotéticos del escenario, excluidos de Q06 | 5 | 5 | 0 | 0 | 0 |
| Q06 | Q06 Intensidad de emisiones eléctricas del proceso productivo en kg CO2e/L | 3 | 3 | 0 | 0 | 0 |
| Q07 | Q07 Residuo de balance después de pérdidas registradas, tolerancia máxima 1% | 28 | 28 | 0 | 0 | 0 |
| Q08 | Q08 Rendimiento másico, electricidad e intensidad de emisiones por operación | 21 | 21 | 0 | 0 | 0 |
| Q09 | Q09 Dependencias de indicadores respecto de factores y versiones | 4 | 4 | 0 | 0 | 0 |
| Q10 | Q10 Proveedores y factores hipotéticos de materiales, separados del inventario eléctrico | 6 | 6 | 0 | 0 | 0 |
| Q11 | Q11 Emisiones eléctricas y cantidad de no conformidades por lote | 4 | 4 | 0 | 0 | 0 |
| Q12 | Q12 Trazabilidad indicador, proceso, operación y recurso vinculados | 52 | 52 | 0 | 0 | 0 |
| Q13 | Q13 Inventario explícito de flujos materiales por proceso | 16 | 16 | 0 | 0 | 0 |
| Q14 | Q14 Falta de registro de limpieza del mismo equipo entre lotes consecutivos | 1 | 1 | 0 | 0 | 0 |
| Q15 | Q15 Agua registrada por operación auxiliar de limpieza | 3 | 3 | 0 | 0 | 0 |
| Q16 | Q16 Tiempo de uso y OEE registrado por recurso y plan | 24 | 24 | 0 | 0 | 0 |
| Q17 | Q17 Emisiones eléctricas por unidad funcional de salida, litros positivos | 3 | 3 | 0 | 0 | 0 |
| Q18 | Q18 Actividades del plan, orden explícito y duración de cadena lineal | 28 | 28 | 0 | 0 | 0 |
| Q19 | Q19 Ranking de lotes por emisiones eléctricas acumuladas | 4 | 4 | 0 | 0 | 0 |
| Q20 | Q20 Datos críticos ausentes por tipo, con criterio explícito | 4 | 24 | 20 | 0 | 20 |
| Q21 | Q21 Términos con versión modificada respecto de la registrada | 1 | 1 | 0 | 0 | 0 |

## Filas que cambian

### Q04 — Q04 Recursos requeridos e insumos materiales por proceso

Filas sin ERP: 40; con ERP: 55. Nuevas: 15 (15 mencionan un individuo ERP). Quitadas: 0.


Nuevas:

| process | resource |
|---|---|
| erp:adempiere/process/50015 | erp:adempiere/resource/50001 |
| erp:adempiere/process/50015 | erp:adempiere/resource/50002 |
| erp:adempiere/process/50015 | erp:adempiere/resource/50003 |
| erp:adempiere/process/50015 | erp:adempiere/resource/50004 |
| erp:adempiere/process/50015 | erp:adempiere/resource/50008 |
| erp:adempiere/process/50016 | erp:adempiere/resource/50007 |
| erp:adempiere/process/50016 | erp:adempiere/resource/50009 |
| erp:adempiere/process/50017 | erp:adempiere/resource/50006 |
| erp:adempiere/process/50018 | erp:adempiere/resource/50001 |
| erp:odoo/process/2 | erp:odoo/product/38 |
| erp:odoo/process/2 | erp:odoo/product/39 |
| erp:odoo/process/2 | erp:odoo/workcenter/1 |
| erp:odoo/process/3 | erp:odoo/workcenter/1 |
| erp:odoo/process/8 | erp:odoo/workcenter/1 |
| erp:odoo/process/8 | erp:odoo/workcenter/2 |

### Q20 — Q20 Datos críticos ausentes por tipo, con criterio explícito

Filas sin ERP: 4; con ERP: 24. Nuevas: 20 (20 mencionan un individuo ERP). Quitadas: 0.


Nuevas:

| entity | kind | missingProperty |
|---|---|---|
| erp:adempiere/operation/50085 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50086 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50087 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50088 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50089 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50090 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50091 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50092 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/operation/50093 | ProcessOperation | iso15531#hasInput |
| erp:adempiere/process/50015 | ManufacturingProcess | iso14040#computedForProcess |
| erp:adempiere/process/50016 | ManufacturingProcess | iso14040#computedForProcess |
| erp:adempiere/process/50017 | ManufacturingProcess | iso14040#computedForProcess |
| erp:adempiere/process/50018 | ManufacturingProcess | iso14040#computedForProcess |
| erp:odoo/operation/1 | ProcessOperation | iso15531#hasInput |
| erp:odoo/operation/3 | ProcessOperation | iso15531#hasInput |
| erp:odoo/operation/4 | ProcessOperation | iso15531#hasInput |
| erp:odoo/operation/5 | ProcessOperation | iso15531#hasInput |
| erp:odoo/process/2 | ManufacturingProcess | iso14040#computedForProcess |
| erp:odoo/process/3 | ManufacturingProcess | iso14040#computedForProcess |
| erp:odoo/process/8 | ManufacturingProcess | iso14040#computedForProcess |
