# Resultados computacionales de los capítulos 3 y 4

Versión: `v1.0.0`. Los artefactos corresponden a un caso simulado y a ejecuciones computacionales conservadas. La ejecución se hizo la noche del 26-11-2026, hora de Argentina (27-11-2026 en UTC); el entorno está en [environment.json](environment.json).

## Evidencia y resultados

La evaluación funcional conserva 243 comprobaciones, incluidas entradas inválidas y fallos esperados, y 105 comparaciones de respuestas: 21 consultas SELECT en cinco configuraciones. Las 243 y las 105 resultaron correctas. La demostración de PROTYS-WS conserva 41 controles y 49 intercambios HTTP, todos correctos. El piloto de ISO 10303 AP242 comprueba una extensión contextual sobre identidades existentes y conserva las 21 respuestas previas del caso funcional. No acredita conformidad completa con STEP ni una validación industrial.

| Configuración | Triples de entrada | Triples materializados | Estado OWL |
|---|---:|---:|---|
| ISO 15531 | 2.294 | 2.566 | CONSISTENT |
| ISO 14040 | 1.137 | 1.328 | CONSISTENT |
| Unión sin alineamientos | 2.947 | 3.265 | CONSISTENT |
| Modelo integrado | 4.325 | 5.384 | CONSISTENT |
| Modelo integrado con el piloto ISO 10303 | 4.665 | 5.972 | CONSISTENT |

El rendimiento utiliza 100 lotes simulados, 700 operaciones, semilla de datos 42 y semilla de orden 20261003. Se ejecutaron 30 réplicas por configuración, con procesos Java nuevos y sin reutilizar tiempos de la caché de la aplicación. Las 120 ejecuciones terminaron con estado OWL consistente; los controles numéricos del modelo integrado resultaron válidos.

| Configuración | Triples de entrada | Triples materializados | Réplicas | Tiempo interno, media ± DE (s) | Mediana (s) | Mín.–máx. (s) | Máximo observado de RSS, media ± DE (MiB) | Mediana (MiB) | Mín.–máx. (MiB) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ISO 15531 | 37.038 | 41.031 | 30 | 5,452 ± 0,184 | 5,405 | 5,114–5,954 | 582,77 ± 61,69 | 566,80 | 536,55–864,20 |
| ISO 14040 | 10.349 | 12.842 | 30 | 2,584 ± 0,143 | 2,539 | 2,416–3,007 | 438,86 ± 17,30 | 434,54 | 400,33–479,72 |
| Unión sin alineamientos | 42.395 | 47.106 | 30 | 6,987 ± 0,262 | 6,967 | 6,437–7,706 | 572,82 ± 59,13 | 547,66 | 514,06–735,73 |
| Modelo integrado | 44.444 | 62.867 | 30 | 20,168 ± 0,429 | 20,257 | 19,539–21,227 | 1.512,19 ± 179,67 | 1.522,80 | 1.154,33–1.820,72 |

La memoria es la suma de RSS del proceso principal y su trabajador, muestreada cada 100 ms. El tiempo interno y el tiempo completo de proceso se conservan por separado. Las cachés del sistema operativo, el estado térmico y la planificación de CPU no se controlaron.

Las configuraciones ejecutan tareas semánticas distintas. Todas ejecutan las 21 SELECT; el modelo integrado añade 22 reglas SWRL, cuatro ASK canónicos, preparación mediante dos CONSTRUCT y controles auxiliares. Las respuestas vacías esperadas en las proyecciones se interpretan dentro de los datos y conceptos formalizados. La integración tiene un mayor costo observado. No se deriva superioridad general de velocidad ni beneficio industrial.

La unión y el modelo integrado conservan los mismos vínculos contextuales declarados del caso. El integrado incorpora además las declaraciones del alineamiento, incluidos dos axiomas de tipado de la capacidad `a:FineGrinding`; sus entradas completas no son idénticas byte a byte. Los conteos de axiomas añadidos por el trabajador combinan consecuencias OWL 2 RL y SWRL. La atribución adicional a reglas se contrasta con la ablación funcional OWL_ONLY; las contribuciones de CONSTRUCT se cuentan por separado.

## Comparación con la ejecución del 3-10-2026

Los mismos datos y el mismo protocolo se ejecutaron también el 3-10-2026. Desde entonces sólo cambiaron textos de los comentarios en las ontologías (`rdfs:comment` y comentarios XML). [`comparacion/`](comparacion/) contiene la comparación automática de los resultados funcionales de ambas ejecuciones: `functional.json`, `runs.jsonl`, `capacity.json`, `demonstration.json`, `census.json`, los cuerpos de las 49 respuestas HTTP de la demostración y las 3012 respuestas SPARQL guardadas. Resultado: sin diferencias. Los hashes de las entradas y de la materialización del razonador cambian en 69 casos; en todos, los dos grafos tienen la misma cantidad de triples y son isomorfos después de quitar los triples `rdfs:comment`. Tiempos y memoria se comparan aparte, en [comparacion/README.md](comparacion/README.md).

## Archivos conservados

- [Resultados funcionales](functional.json), [demostración de PROTYS-WS](demonstration.json) y [piloto de capacidad](capacity.json).
- [Mediciones de las 120 réplicas](runs.jsonl), [resumen completo](summary.json), [resumen CSV](summary.csv) y [entorno de ejecución](environment.json).
- [Auditoría de las 120 ejecuciones](benchmark-audit.json), [manifiesto de evidencia](evidence-manifest.json) y [registro de copias idénticas](CONSERVACION_RESULTADOS.json).
- [Figura 4.7](figures/figura4_7_resultados_definitivos.png), también en SVG y PDF, con su registro de procedencia.
- [Grafo materializado](grafo-materializado/) de la configuración integrada del conjunto funcional, en Turtle y RDF/XML, con las 21 consultas corridas sobre él con `arq` de Apache Jena ([resumen](grafo-materializado/consultas-arq/resumen.json)).

El manifiesto identifica todos los archivos regulares de `research/evaluation/current/` (6117), su tamaño, SHA-256 y archivo comprimido. Los 51 archivos comprimidos se distribuyen como adjuntos de la publicación `v1.0.0`; al extraerlos se conserva la estructura relativa del repositorio. Entre ellos están, por réplica, las solicitudes, las respuestas de las 21 consultas y los modelos razonados (`runs/`).

La preparación y ejecución se describen en [research/README.md](../README.md), con `research/reproduce.sh` como punto de entrada, y los pasos completos en la sección 5 del [README](../../README.md) de la raíz. Los contratos del modelo registran que las respuestas coinciden con las esperadas; las expectativas se definieron antes de la ejecución y no se reescribieron a partir de las salidas.

## Alcance de la reproducción

Las pruebas y compilación de la interfaz se realizaron con React y React DOM 18.3.1, Tailwind CSS 3.4.19, react-scripts 5.0.1 y Jest 27.5.1. El paquete de la interfaz mantiene rangos de dependencia y carece de lockfile; no se certifica una reinstalación idéntica de todas sus dependencias. La evaluación académica utiliza el entorno Java y las dependencias fijadas en su protocolo, separado de las dependencias de la interfaz.

C26 comprueba los extremos que delimitan el intervalo de limpieza entre lotes consecutivos: fin del primer lote e inicio del siguiente. Su resultado no acredita integridad temporal global de todos los extremos del lote. La ausencia de un registro de limpieza indica falta de evidencia registrada, sin demostrar contaminación física.

Las IRI del modelo se usan como identificadores lógicos. Su resolución pública, un depósito con DOI y una disponibilidad FAIR completa quedan fuera de esta versión. La herramienta semiautomática de conversión de estándares desde lenguaje natural y EXPRESS, descrita en [SAOA 2017, páginas 53–66](https://clei.org/clei2017/sites/default/files/Mem/SAOA/SAOA-05.pdf), permanece como parte de la investigación; su ejecución no se atribuye a esta evaluación de pintura.
