# Decisión: cómo convertir filas de ERP en individuos tipados de PROTYS(KB)

**Estado: propuesta, espera visto bueno del autor antes de implementar.**

## Problema
`ERPConnectorService` hoy ejecuta una lista de `SELECT` (formato XML `<mapping>`). Cada fila
genera un sujeto `erp:<conector>/<tabla>/<col1>`, tipado `erp:<tabla>`, con literales
`erp:<columna>`. No hay clases ni propiedades de la red, no hay vínculos entre individuos
(todo son literales) y el grafo no se puede consultar con P1–P5.
Hacen falta: tipos de la red (`i:ProcessPlan`, `i:ProcessOperation`…), propiedades de objeto
entre individuos (`i:includesActivity`, `i:requiresResource`…) e IRIs estables por fuente.

## Hecho nuevo que cambia la opción (a)
CARML no procesa R2RML sobre bases relacionales. Lo comprobé en el artefacto declarado
(`io.carml:carml-model:0.4.3`): el vocabulario `ql:` sólo define `JsonPath`, `XPath` y `Csv`.
En Maven Central no hay resolvedor SQL de CARML en ninguna versión (la última es la 0.4.11).
La dependencia del `pom.xml` (comentada como "R2RML Processing") no puede cumplir esa
función y no la usa ninguna clase. La opción (a) tal como estaba planteada no es viable.

## Opciones reales

| | (a1) Procesador R2RML propio sobre Jena (subconjunto declarado) | (a2) RMLMapper (procesador externo) | (b) Convención de alias en el formato actual |
|---|---|---|---|
| Qué es | Un intérprete de mapeos R2RML en Turtle: `rr:logicalTable` (`rr:sqlQuery`/`rr:tableName`), `rr:subjectMap` (`rr:template`, `rr:class`, `rr:graph`), `rr:predicateObjectMap` con `rr:column`, `rr:template`, `rr:constant`, `rr:datatype`, `rr:parentTriplesMap` + `rr:joinCondition` | Agregar `be.ugent.rml:rmlmapper` y llamarlo desde el servicio | Columnas con alias del tipo `"p:i:requiresResource->wc"` y `"class"` en el XML actual |
| Mapeos | R2RML estándar W3C, legibles fuera de PROTYS-WS | R2RML/RML estándar | Formato propio |
| Código nuevo | ~300–400 líneas + pruebas | ~100 líneas, pero dependencia grande (trae RDF4J, además de Jena) | ~100 líneas |
| Conformidad | Se mide con los casos oficiales de la suite W3C (`rdb2rdf-tests`) para las funciones implementadas; lo no implementado se rechaza con error explícito | Heredada del proyecto externo | No aplica |
| Riesgo | Errores propios en el intérprete (los acota la suite W3C) | Conflictos de dependencias, peso de la imagen, menor control del sólo lectura de la conexión | La tesis no podría llamarlo R2RML |
| Qué puede afirmar la tesis | "PROTYS-WS materializa mapeos R2RML (subconjunto X, validado con N casos W3C) desde ADempiere y Odoo" | "PROTYS-WS invoca un procesador R2RML de terceros" | "PROTYS-WS materializa con una convención propia basada en SQL" |

## Recomendación: (a1)
1. La tesis ya presenta R2RML como el mecanismo de integración (el manuscrito incluye un fragmento R2RML). Con (a1) los mapeos ejecutados son R2RML de verdad, y el alcance se declara y se mide.
2. No suma una segunda biblioteca RDF. Todo sigue en Jena, igual que `FusekiService` y `ERPGraphPublication`.
3. La seguridad actual se mantiene: solo `SELECT`, conexión `setReadOnly(true)`, publicación compensable, y un grafo vacío no reemplaza al existente.
4. Lo que no se implemente (por ejemplo `rr:inverseExpression` o `rr:language`) se rechaza con `UNSUPPORTED_MAPPING`. Nunca se ignora en silencio.

## Qué se hace en cada caso
- **(a1):** nuevo `R2RMLProcessor` en `service/`. `ERPConnectorService.materialize` lo usa cuando el mapeo es Turtle, y el formato XML viejo sigue funcionando para no romper sus pruebas. Mapeos en `docker/integration/mappings/{adempiere,odoo}.r2rml.ttl`. Pruebas unitarias más un subconjunto de la suite W3C sobre H2 o PostgreSQL de prueba.
- **CARML:** propongo dejar la dependencia como está y anotar en el informe que no procesa SQL. Sacarla es una limpieza aparte que no hace falta para esta fase.
- **(b)**, si se prefiere: alias en el XML actual, sin cambiar el formato de archivo.

## Lo que necesito
Elegir (a1), (a2) o (b).
