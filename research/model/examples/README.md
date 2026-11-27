# Ejemplos y extractos

Consultas, datos y extractos de la red usados en el texto. El prefijo indica el capítulo de la tesis en que
aparecen (`c3_`, `c4_`, `c5_`). `research/evaluation/comprobar_sintaxis.py` analiza la sintaxis de todos.

| Archivos | Contenido |
|---|---|
| `Q01_presentacion.sparql`–`Q21_presentacion.sparql`, `R07`, `R16`, `R19`, `R26` | Las 21 consultas del catálogo y las 4 validaciones ASK, con el formato de presentación |
| `c3_extracto_*.ttl`, `c4_extracto_*.ttl` | Extractos de módulos de `ontologies/`; la cabecera de cada uno nombra los módulos de los que sale |
| `c4_extracto_jerarquia_planta.ttl` | `enterprise:isPartOf` transitiva y la cadena Station → Cell → Shop → Factory → `core:Enterprise` |
| `c4_extracto_disyunciones.ttl` | Las tres `owl:AllDisjointClasses` de los módulos de recursos, ISO 14040 e ISO 15531 |
| `c4_extracto_union.ttl` | Rango de `hasInventoryFlow` y dominio de `refersToMaterial` como unión de `ElementaryFlow` y `ProductFlow` |
| `c4_extracto_cardinalidades.ttl` | Cardinalidad exacta 1 de `LCAStudy` sobre `hasFunctionalUnit` y mínima 1 de `WorkOrder` sobre `executedBy` |
| `c3_consulta_q04.sparql`, `c5_consulta_q0*.sparql` | Consultas del catálogo citadas en el texto |
| `c4_datos_*.ttl`, `c5_datos_pearlmill.ttl` | Fragmentos de los datos del caso |
| `c3_integracion_ilustrativa.ttl`, `.sparql` | Ejemplo ilustrativo de integración, fuera de los datos del caso |
| `c5_r2rml_productos_ilustrativo.ttl` | Mapeo R2RML ilustrativo de una tabla de productos; los mapeos ejecutados están en `docker/integration/mappings/` |

`python3 research/model/comprobar_extractos.py` comprueba que cada triple de cada extracto esté en los módulos que
nombra su cabecera (salida en `research/integration/suites/extractos-contenidos.txt`).
