# Análisis con OOPS!

Análisis de los módulos de PROTYS(KB) con OOPS! (OntOlogy Pitfall Scanner!, https://oops.linkeddata.es), ejecutado
el 14 de noviembre de 2026 sobre los archivos OWL de la versión evaluada (huella y cantidad de triples de cada archivo
en `EVIDENCIA_OOPS.log`). Las reglas SWRL se apartan antes del envío porque OOPS! no las evalúa.

| Archivo | Contenido |
|---|---|
| `oops_run.py` | Programa que repite el análisis |
| `EVIDENCIA_OOPS.log` | Registro de la ejecución: archivos cargados con su huella, cantidades de clases y propiedades, resultado por conjunto |
| `EVIDENCIA_OOPS_terminal.png` | El mismo registro como imagen |
| `OOPS_resumen.csv` | Una fila por problema y conjunto, con los elementos señalados |
| `respuesta_oops_*.rdf` | Respuesta del servicio para cada conjunto, sin editar |

Para repetirlo, desde la raíz del repositorio (Python 3 con rdflib y acceso a Internet):

```sh
python3 research/quality/oops/oops_run.py . research/quality/oops/nueva-corrida
```

## Módulos de esta versión frente a los analizados

Comparados sin las propiedades de anotación (`rdfs:comment`, `rdfs:label` y similares):

| Archivo | Triples analizados → actuales | Diferencia sin anotaciones |
|---|---|---|
| `core-concepts.owl`, `enterprise-module.owl`, `product-module.owl`, `resource-module.owl`, `iso15531-module.owl`, `iso14040-module.owl`, `iso10303-ap242-fragment.owl`, `alignment-rules.owl` | iguales | ninguna |
| `process-module.owl` | 58 → 60 | `owl:imports` del módulo de recursos y `process:Process_Activity rdfs:subClassOf resource:Activity` |

`disjointness-module.ttl` y `paint-case-study-instances.ttl` se agregaron después del análisis.

Los archivos analizados se tomaron de una etiqueta del repositorio de trabajo anterior (campo `origen` de `EVIDENCIA_OOPS.log`); la tabla de esta página los compara con los de esta versión.
