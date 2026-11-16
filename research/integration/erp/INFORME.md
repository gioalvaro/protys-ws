# Informe de la Fase 1: conexión real con ADempiere y Odoo

Fecha: 2026-11-16. Rama `trabajo-tecnico-2026-11`. Corrida de evidencia: `run.sh` sobre el commit
`fbbbb09`, resultado **APROBADO en los 21 pasos** (`salidas/e2e/RESUMEN.txt`).

## Qué se probó
1. **Fuentes reales.** ADempiere 3.9.4 LTS: base PostgreSQL restaurada desde la semilla oficial del
   release (cliente GardenWorld, sin servidor de aplicación; la base alcanzó). Odoo 17.0 (imagen oficial)
   con el módulo `mrp` y sus datos de demostración. Las dos versiones están fijadas por digest o por suma SHA-256.
2. **Esquemas reales** de manufactura relevados por SQL y por `/introspect` (`esquemas.md`). Por los
   dos caminos dan las mismas columnas en las 21 tablas usadas.
3. **Correspondencias** ERP → PROTYS(KB) (`correspondencias.md`). Las 26 clases y propiedades usadas existen en `ontologies/`, comprobado por script.
4. **Mapeo R2RML** (vía a1, `decision-mapeo.md`): procesador nuevo `R2RMLProcessor` sobre Jena y dos
   mapeos R2RML (`docker/integration/mappings/`). El procesador pasa los 63 casos R2RML de la suite W3C
   RDB2RDF (`salidas/w3c-r2rml-report.txt`).
5. **Prueba de punta a punta** (`run.sh`): registro de conectores por la API, prueba de conexión,
   inspección, materialización, razonamiento, consultas, sólo lectura y huellas de esquema.
6. **Pruebas de backend.** Suite completa: 191 pruebas, 0 fallas, 0 errores (`salidas/pruebas-backend.txt`).
   Antes de este trabajo eran 118. Las nuevas son 63 casos W3C, 6 del conector R2RML y 4 de razonamiento con grafos de datos.

## Qué pasó (corrida de evidencia)

| Paso | Resultado |
|---|---|
| Conexión con `protys_ro` | `connected: true` en los dos ERP |
| `/introspect` | 992 tablas (ADempiere), 336 (Odoo) |
| Materialización R2RML | ADempiere: 321 triples de 13 triples maps; Odoo: 192 triples de 12 triples maps. Cada uno en su grafo (`http://w3id.org/protys/erp/{adempiere,odoo}`) y solo con individuos tipados con clases de la red |
| Razonamiento red + 2 grafos | `CONSISTENT` (OWL y validación). Entrada de 4318 a 4831 triples (+513, exactamente lo materializado). Inferidos de 1167 a 1572 |
| Q04 canónica, sin cambios (P1) | 15 filas con procesos ERP, además de las del escenario de la tesis |
| P1 recursos de un proceso | 15 filas: ADempiere 9, Odoo 6 |
| P2 recursos de un plan | 24 filas: ADempiere 18, Odoo 6 |
| P3 productos que usan un recurso | 14 filas: ADempiere 10, Odoo 4 |
| P4 actividades de un plan | 17 filas: ADempiere 12, Odoo 5, con secuencia y duración |
| P5 producto de un plan | 11 filas: ADempiere 8, Odoo 3 (ver límite abajo) |
| Consulta conjunta | 10 filas en una respuesta: 8 recursos de ADempiere y 2 de Odoo, con operaciones, procesos y productos |
| Sólo lectura | `CREATE TABLE` y `DELETE` rechazados para `protys_ro` en los dos ERP |
| No intrusión | Huellas antes y después idénticas: tablas, columnas, SHA-256 de `information_schema.columns` y permisos de `protys_ro` |

**Hallazgo del razonamiento.** El razonador dedujo 216 tipos no afirmados sobre individuos ERP
(`08-INFERENCIA.json`). Entre ellos está el que más dice: los 7 procesos ERP (4 de ADempiere y 3 de
Odoo, todos los mapeados) quedan clasificados `c:CoverageGap`. Esa clase es la cabeza de la regla R24,
cuya premisa es `c:MissingEnvironmentalData`, que según la red se asigna con el patrón de validación R07.
La lectura es que la red detecta que los datos de los ERP no traen información ambiental. Sin embargo,
`c:MissingEnvironmentalData` no aparece en el grafo de resultados de inferencia, así que el paso
intermedio no quedó verificado en esta corrida. Los demás tipos son consecuencia de
las jerarquías y de los dominios y rangos (`c:Resource`, `prod:Product`, `c:Process`…).

## Qué no se pudo, o salió distinto de lo esperado
- **CARML no sirve para R2RML sobre bases.** Ninguna versión trae fuente SQL. Se implementó un
  procesador propio (decisión aprobada). La dependencia CARML sigue en `pom.xml` sin uso.
- **P2, P3, P4 y P5 son variantes, no las consultas canónicas.** Q02 parte de un lote (`i:Lot`) y usa
  `t:hasPlan`, `t:planProduces` y `t:producesProduct`, que son del perfil de investigación y no de
  `ontologies/`. Además, las demos no tienen lotes. Q18 exige `t:lifecycleStage`. Cada variante explica
  la diferencia en su archivo (`queries/`). Solo P1 se respondió además con la consulta canónica (Q04) tal cual.
- **P5 sobregenera en ADempiere.** Sin una propiedad plan → producto en la red, P5 llega al producto por
  el proceso. En GardenWorld, *Fertilizer #50* y *Fertilizer #70* comparten el flujo de envasado, así que
  cada uno de esos planes devuelve los dos productos. Son 2 de las 8 filas de ADempiere; en Odoo no pasa.
  Se informa como límite del vocabulario, no se corrigió la prueba.
- **Insumos por operación.** ADempiere no registra en qué operación se consume cada componente. En
  Odoo lo tienen 2 de 17 líneas. La relación producto–componente se expresa con `prod:isComposedOF`.
- **Datos de demostración, no industriales.** El volumen es chico (513 triples). No hay owl:sameAs entre
  ERP: los catálogos no comparten productos. La consulta conjunta combina por clases comunes, no por identidad.
- **Tablas 5.8 y 5.9.** No estaban en el repositorio. Las correspondencias salen de los esquemas reales
  y falta contrastarlas con esas tablas.
- **`/history`** sigue devolviendo una lista vacía: es un esqueleto anterior a este trabajo.
- **Provisión de Odoo.** Para que el rol de sólo lectura no pudiera crear tablas, hubo que quitar a
  PUBLIC el permiso CREATE sobre el esquema `public` (Odoo lo deja abierto). Ocurre al preparar el
  entorno, antes de la huella "antes". La huella cubre el esquema, no el contenido de las filas.
  Mientras corre la prueba, el servidor web de Odoo está apagado.
- **Entorno.** En el JDK 21 del host fallan 2 pruebas preexistentes: el motor Drools usa
  `java.lang.Compiler`, que ya no existe en JDK 21. La suite se corre en Java 17, la versión del
  proyecto, y ahí pasa completa. La composición de la plataforma usa `postgres:15` sin digest; es
  anterior a este trabajo y no se cambió.

## Qué puede afirmar la tesis
- PROTYS-WS se conectó, en modo de sólo lectura, a una base real de ADempiere 3.9.4 y a una de Odoo 17.0
  con datos de demostración de manufactura, y extrajo de ellas individuos de PROTYS(KB).
- La extracción usa mapeos R2RML (W3C) ejecutados por un procesador que pasa los 63 casos R2RML de la
  suite de pruebas W3C RDB2RDF.
- Los individuos extraídos quedan tipados con clases de los módulos ISO 15531, core, product y
  resource de la red, en un grafo nombrado por fuente.
- La red sigue consistente después de sumarle los dos grafos ERP, y el razonamiento clasifica los
  siete procesos ERP como brechas de cobertura (`c:CoverageGap`).
- Con las mismas consultas, la plataforma respondió las cinco preguntas de competencia sobre datos de
  los dos ERP (P1 también con la consulta canónica Q04 sin cambios), y una consulta devolvió en una
  sola respuesta recursos de los dos.
- Los esquemas de los dos ERP no cambiaron con la prueba.

## Qué no puede afirmar la tesis
- Que la integración se probó con datos o volúmenes industriales: son demos con decenas de registros.
- Que P2, P3, P4 y P5 se respondieron con las consultas canónicas del catálogo: se usaron variantes
  (justificadas en `queries/`), y P5 sobregenera cuando dos productos comparten un proceso.
- Que el procesador R2RML es conforme en general: pasa la suite W3C sobre H2, y el uso real se probó
  sobre PostgreSQL. Restringe `rr:sqlQuery` a un SELECT y rechaza `rr:graphMap` en los conectores.
- Que hay identidad de individuos entre ERP (`owl:sameAs`) ni reconciliación de catálogos.
- Que la integración escribe o sincroniza datos hacia los ERP: solo lee.
- Que las correspondencias coinciden con las Tablas 5.8 y 5.9 hasta que se contrasten.
