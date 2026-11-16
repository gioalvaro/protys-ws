# Informe de la Fase 1: conexión real con ADempiere y Odoo

Fecha: 2026-11-16. Rama `trabajo-tecnico-2026-11`. Corrida de evidencia: `run.sh` de cero,
**APROBADO en los 24 pasos** (`salidas/e2e/RESUMEN.txt`). Corrió sobre el commit `da3f100` más los
cambios que entraron en los commits `93f9065` (mapeos con Organización) y `13d2ceb` (`run.sh` y sus scripts); por eso
`02-versiones.txt` dice `+cambios-sin-commit`. Ningún archivo ejecutado cambió después de la corrida.

Este informe incorpora la revisión del 16-11-2026: tabla por pregunta P1–P5, verificación de la
brecha de cobertura, catálogo completo sin y con ERP, contraste con las Tablas 5.8 y 5.9, CARML fuera
del `pom.xml` y PostgreSQL de la plataforma fijado por digest.

## Qué se probó
1. **Fuentes reales.** ADempiere 3.9.4 LTS: base PostgreSQL restaurada desde la semilla oficial del
   release (cliente GardenWorld, sin servidor de aplicación; la base alcanzó). Odoo 17.0 (imagen oficial)
   con el módulo `mrp` y sus datos de demostración. Las dos versiones están fijadas por digest o por suma SHA-256.
2. **Esquemas reales** de manufactura relevados por SQL y por `/introspect` (`esquemas.md`). Por los
   dos caminos dan las mismas columnas en las 21 tablas usadas.
3. **Correspondencias** ERP → PROTYS(KB) (`correspondencias.md`), contrastadas con las Tablas 5.8 y 5.9
   de la tesis (`tablas-tesis-5.8-5.9.md`). Las 28 clases y propiedades de la red que se usan existen en `ontologies/`,
   comprobado por script (`salidas/verificar-vocabulario.txt`).
4. **Mapeo R2RML** (vía a1, `decision-mapeo.md`): procesador propio `R2RMLProcessor` sobre Jena y dos
   mapeos R2RML (`docker/integration/mappings/`). El procesador pasa los 63 casos R2RML de la suite W3C
   RDB2RDF (`salidas/w3c-r2rml-report.txt`).
5. **Prueba de punta a punta** (`run.sh`): línea de base sin ERP, registro de conectores por la API,
   prueba de conexión, inspección, materialización, razonamiento con la entrada preparada guardada,
   consultas, catálogo completo sin y con ERP, sólo lectura y huellas de esquema.
6. **Pruebas de backend.** Suite completa en Java 17: 192 pruebas, 0 fallas, 0 errores
   (`salidas/pruebas-backend.txt`), ya sin CARML. Antes de este trabajo eran 118.

## Qué pasó (corrida de evidencia)

| Paso | Resultado |
|---|---|
| Conexión con `protys_ro` | `connected: true` en los dos ERP |
| `/introspect` | 992 tablas (ADempiere), 336 (Odoo) |
| Materialización R2RML | ADempiere: 348 triples de 14 triples maps; Odoo: 201 triples de 14 triples maps. Cada uno en su grafo (`http://w3id.org/protys/erp/{adempiere,odoo}`), sólo con individuos tipados con clases de la red |
| Razonamiento red + 2 grafos | `CONSISTENT` (OWL y validación). Entrada de 4318 a 4867 triples (+549, exactamente lo materializado). Inferidos de 1167 a 1586 |
| Brecha de cobertura | Cadena R07 → R24 confirmada sobre la entrada preparada (ver abajo) |
| P1–P5 y consulta conjunta | Ver la tabla por pregunta. La consulta conjunta devuelve 10 filas en una respuesta: 8 recursos de ADempiere y 2 de Odoo |
| Catálogo Q01–Q21 sin y con ERP | Cambian sólo Q04 (+15 filas) y Q20 (+20); las otras 19 dan lo mismo (ver abajo) |
| Sólo lectura | `CREATE TABLE` y `DELETE` rechazados para `protys_ro` en los dos ERP |
| No intrusión | Huellas antes y después idénticas: tablas, columnas, SHA-256 de `information_schema.columns` y permisos de `protys_ro` |

### P1–P5 por pregunta

La tesis puede decir: P1 se respondió con la consulta del catálogo sin cambios; P2–P5, con consultas
equivalentes que usan sólo vocabulario de `ontologies/`. Esto último está comprobado por script: los
términos de la red que usan P1–P5 y la consulta conjunta son de `core` e `iso15531`, ninguno de la
extensión del caso de pintura (`salidas/verificar-vocabulario-consultas.txt`).

| Pregunta | Consulta del catálogo | Por qué aplica o no a los datos de los ERP | Qué cambia en la variante (`queries/`) | Filas ADempiere | Filas Odoo |
|---|---|---|---|---|---|
| P1 Recursos requeridos por un proceso X | Q04 | Aplica: sólo pide operación `i:partOfProcess` proceso y `i:requiresResource` o `i:hasInput`, que los dos ERP tienen | Nada en Q04, que se corrió tal cual (15 filas con procesos ERP). `P1.rq` es el mismo patrón restringido a los grafos ERP, con la fuente y los nombres | 9 | 6 |
| P2 Recursos para ejecutar un plan X | Q02 | No aplica: parte de un lote (`i:Lot`) con `t:hasPlan` y `t:hasProcess`; las demos no tienen lotes y esas propiedades son del perfil de investigación, no de `ontologies/` | Parte del plan (`i:ProcessPlan`): recursos requeridos por las operaciones que incluye (`i:includesActivity`, `i:requiresResource`), insumos (`i:hasInput`) y el recurso que el plan asigna (`i:assignsResource`) | 18 | 6 |
| P3 Productos que usan un recurso X | Q02 | No aplica: llega al producto por el lote (`t:producesProduct`) y el plan (`t:planProduces`) | Llega al producto por el proceso (`c:produces`): un producto usa el recurso si lo requiere una operación del proceso que lo produce | 10 | 4 |
| P4 Actividades de un plan X | Q18 | No aplica: exige `t:lifecycleStage` y las etapas del escenario de pintura, y que el plan esté ligado a un lote | Operaciones del plan (`i:includesActivity`) con la secuencia (`i:operationSequence`) y la duración (`i:duration`) que registra el ERP, y la duración total por plan | 12 | 5 |
| P5 Producto generado por un plan X | Q02 | No aplica: usa `t:planProduces` y el lote | Cadena plan → operación (`i:includesActivity`) → proceso (`i:partOfProcess`) → producto (`c:produces`). Sobregenera en ADempiere (ver límites) | 8 | 3 |

### Brecha de cobertura: cadena R07 → R24 verificada

`run.sh` guarda la carpeta de trabajo del razonador de esta corrida (`salidas/e2e/07-evidencia-razonamiento/`:
entrada cruda, entrada preparada, deducciones SWRL) gracias a la nueva propiedad opcional
`protys.reasoning.evidence-dir`, y `scripts/verificar-brecha.py` comprueba la cadena
(`salidas/e2e/07-brecha-cobertura.txt`, 6 controles OK):

1. R24 está habilitada (entre las 22 reglas) y su definición SWRL en la entrada es
   `c:MissingEnvironmentalData(?p) → c:CoverageGap(?p)`.
2. `c:MissingEnvironmentalData` no está afirmada en la entrada cruda: la agrega la preparación
   `research/model/validations/R07-materialize.sparql` (CONSTRUCT sobre `i:ManufacturingProcess` sin
   representación de inventario con indicador), que el razonador corre antes de razonar.
3. En la entrada preparada, los 7 procesos ERP tienen `c:MissingEnvironmentalData`: ADempiere
   Patio Chair, Lawn Fertilizer, Fertilizer Packing Process y Patio Furniture Set; Odoo Ruta Table,
   Ruta Table Top y Ruta Drawer SEC-ASSEM.
4. Las deducciones SWRL clasifican `c:CoverageGap` exactamente a los 8 procesos con
   `c:MissingEnvironmentalData`: los 7 ERP y `fixture#MissingProcess`, un proceso del escenario hecho a
   propósito sin inventario.
5. Prueba negativa: los 4 procesos del escenario de pintura con representación de inventario e
   indicador (BatchA, BatchB, BatchC y BatchZero) no quedan ni como `c:MissingEnvironmentalData` ni
   como `c:CoverageGap`.

La lectura: la red detecta que los datos de los ERP no traen información ambiental, y lo hace por la
misma regla y la misma preparación que en el escenario. Es una consecuencia esperable (los ERP de
manufactura no registran inventario de ciclo de vida), no un descubrimiento sobre los ERP.

### Catálogo Q01–Q21 sin y con los grafos ERP

Cuadro completo: `salidas/e2e/08-catalogo-diferencias.md` (respuestas crudas en `03b-catalogo-sin-erp/`
y `08-catalogo-con-erp/`). Ninguna consulta pierde filas.

- **Q04** suma 15 filas, todas de procesos ERP: es P1.
- **Q20** (datos críticos ausentes) pasa de 4 a 24 filas. Las 4 de antes son los individuos del
  escenario armados para esa prueba (`MissingProcess`, `MissingOperation`, `MissingQC`). Las 20 nuevas
  son todas ERP:
  - los 7 procesos ERP, sin `e:computedForProcess` (sin inventario con indicador): el mismo criterio
    que la brecha de cobertura;
  - 13 operaciones sin `i:hasInput`: las 9 de ADempiere, porque ADempiere no registra en qué operación
    se consume cada componente, y 4 de las 5 de Odoo (la quinta sí tiene insumo por operación).
  Q20 muestra, sin cambios en la consulta, qué datos les faltan a los ERP para el análisis ambiental.
- **Las otras 19 no cambian.** Dependen de lotes, indicadores, factores, flujos, etapas del ciclo de
  vida o registros del escenario de pintura, que los ERP no aportan. Que no cambien también muestra que
  los grafos ERP no alteran las respuestas del escenario.

## Qué no se pudo, o salió distinto de lo esperado
- **CARML no sirve para R2RML sobre bases.** Ninguna versión trae fuente SQL. Se implementó un
  procesador propio (decisión aprobada) y la dependencia se sacó del `pom.xml`.
- **P2, P3, P4 y P5 son variantes**, no las consultas del catálogo (tabla de arriba).
- **P5 sobregenera en ADempiere.** Sin una propiedad plan → producto en la red, P5 llega al producto por
  el proceso. En GardenWorld, *Fertilizer #50* y *Fertilizer #70* comparten el flujo de envasado, así que
  cada uno de esos planes devuelve los dos productos. Son 2 de las 8 filas de ADempiere; en Odoo no pasa.
  Queda como límite documentado: no se toca `ontologies/`.
- **Insumos por operación.** ADempiere no registra en qué operación se consume cada componente. En
  Odoo lo tienen 2 de 17 líneas. La relación producto–componente se expresa con `prod:isComposedOF`.
- **Tablas 5.8 y 5.9** (`tablas-tesis-5.8-5.9.md`). Producto, Producción y Composición están cubiertas
  en parte: no se mapean categoría, unidad ni versión del producto, ni tiempos reales y cantidades de
  las órdenes, ni cantidad y unidad de los componentes (los módulos generales no tienen propiedad para
  eso). **Organización** se mapeó a `c:Enterprise` (9 organizaciones de GardenWorld, 2 compañías de
  Odoo); el Módulo de Empresa no tiene una subclase para organización. En Odoo además se afirma qué
  compañía tiene cada proceso (`c:hasProcess`); en ADempiere no, porque los flujos de la demo son de la
  organización comodín `*`. **Almacén no se mapeó**: no hay una clase de almacén en los módulos
  generales (la única, `paint-ext:Warehouse`, es del caso de pintura).
- **Datos de demostración, no industriales.** El volumen es chico (549 triples). No hay owl:sameAs entre
  ERP: los catálogos no comparten productos. La consulta conjunta combina por clases comunes, no por identidad.
- **`/history`** del conector devuelve una lista vacía: es un esqueleto anterior a este trabajo y no se implementó.
- **Provisión de Odoo.** Para que el rol de sólo lectura no pudiera crear tablas, hubo que quitar a
  PUBLIC el permiso CREATE sobre el esquema `public` (Odoo lo deja abierto). Ocurre al preparar el
  entorno, antes de la huella "antes". La huella cubre el esquema, no el contenido de las filas.
  Mientras corre la prueba, el servidor web de Odoo está apagado.
- **Entorno.** En el JDK 21 del host fallan 2 pruebas preexistentes: el motor Drools usa
  `java.lang.Compiler`, que ya no existe en JDK 21. La suite se corre en Java 17, la versión del
  proyecto. La imagen de PostgreSQL de la plataforma quedó fijada por digest a la que se venía usando
  (`postgres:15@sha256:0dda651c…`, PostgreSQL 15.19); la publicación actual de `postgres:15.19` tiene
  otro digest y no se pudo bajar por cortes de red. Un primer intento de la corrida falló por la misma
  causa (descargas de Maven cortadas al reconstruir la imagen del backend); el segundo aprobó.

## Qué puede afirmar la tesis
- PROTYS-WS se conectó, en modo de sólo lectura, a una base real de ADempiere 3.9.4 y a una de Odoo 17.0
  con datos de demostración de manufactura, y extrajo de ellas individuos de PROTYS(KB).
- La extracción usa mapeos R2RML (W3C) ejecutados por un procesador que pasa los 63 casos R2RML de la
  suite de pruebas W3C RDB2RDF.
- Los individuos extraídos quedan tipados con clases de los módulos core, ISO 15531, product y
  resource de la red, en un grafo nombrado por fuente.
- La red sigue consistente después de sumarle los dos grafos ERP.
- El razonamiento clasifica los siete procesos ERP como brechas de cobertura (`c:CoverageGap`), por la
  preparación R07 y la regla R24, las mismas del escenario; y no clasifica así a los procesos del
  escenario que tienen inventario.
- P1 se respondió sobre los dos ERP con la consulta del catálogo sin cambios (Q04); P2–P5, con
  consultas equivalentes que usan sólo vocabulario de `ontologies/`. Una consulta devolvió en una sola
  respuesta recursos de los dos ERP.
- Con las 21 consultas del catálogo sin cambios, sumar los ERP sólo agrega filas a Q04 y a Q20; Q20
  identifica qué datos ambientales y de insumos les faltan a los ERP.
- Los esquemas de los dos ERP no cambiaron con la prueba.

## Qué no puede afirmar la tesis
- Que la integración se probó con datos o volúmenes industriales: son demos con decenas de registros.
- Que P2, P3, P4 y P5 se respondieron con las consultas del catálogo: se usaron variantes, y P5
  sobregenera cuando dos productos comparten un proceso.
- Que el procesador R2RML es conforme en general: pasa la suite W3C sobre H2, y el uso real se probó
  sobre PostgreSQL. Restringe `rr:sqlQuery` a un SELECT y rechaza `rr:graphMap` en los conectores.
- Que hay identidad de individuos entre ERP (`owl:sameAs`) ni reconciliación de catálogos.
- Que la integración escribe o sincroniza datos hacia los ERP: sólo lee.
- Que las Tablas 5.8 y 5.9 se cumplen completas: Almacén no tiene clase en la red, y varias
  "condiciones por comprobar" (unidades, cantidades, versiones, alcance organizacional) no se mapean.
- Que la plataforma guarda un historial de materializaciones: `/history` devuelve una lista vacía.
- Que la brecha de cobertura es un hallazgo sobre los ERP: es la consecuencia esperada de que no
  registran inventario de ciclo de vida.
