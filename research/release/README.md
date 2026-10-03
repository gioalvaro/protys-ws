# Resultados computacionales del cierre de los capítulos 3 y 4

Versión identificada: `tesis-cierre-2026-10-03-v1`. Los artefactos corresponden a un caso simulado y a ejecuciones computacionales conservadas. La publicación se comprueba contra el commit, la etiqueta y los archivos de esa versión.

## Evidencia y resultados

La evaluación funcional conserva 243 comprobaciones, incluidas entradas inválidas y fallos esperados, y 105 comparaciones de respuestas: 21 consultas SELECT en cinco configuraciones. La demostración de PROTYS-WS conserva 41 controles y 49 intercambios HTTP. El piloto de ISO 10303 AP242 comprueba una extensión contextual sobre identidades existentes y conserva las 21 respuestas previas del caso funcional. No acredita conformidad completa con STEP ni una validación industrial.

El rendimiento utiliza 100 lotes simulados, 700 operaciones, semilla de datos 42 y semilla de orden 20261003. Se ejecutaron 30 réplicas por configuración, con procesos Java nuevos y sin reutilizar tiempos de la caché de la aplicación. Las 120 ejecuciones terminaron con estado OWL consistente; los controles numéricos del modelo integrado resultaron válidos.

| Configuración | Réplicas | Tiempo interno, media ± DE (s) | Mediana (s) | Media del máximo observado de RSS (MiB) |
|---|---:|---:|---:|---:|
| ISO 15531 | 30 | 5,374 ± 0,078 | 5,381 | 626,98 |
| ISO 14040 | 30 | 2,526 ± 0,100 | 2,504 | 445,04 |
| Unión sin alineamientos | 30 | 6,915 ± 0,124 | 6,929 | 567,49 |
| Modelo integrado | 30 | 19,859 ± 0,285 | 19,813 | 1479,41 |

La memoria es la suma de RSS del proceso principal y su trabajador, muestreada cada 100 ms. El tiempo interno y el tiempo completo de proceso se conservan por separado. Las cachés del sistema operativo, el estado térmico y la planificación de CPU no se controlaron.

Las configuraciones ejecutan tareas semánticas distintas. Todas ejecutan las 21 SELECT; el modelo integrado añade 22 reglas SWRL, cuatro ASK canónicos, preparación mediante dos CONSTRUCT y controles auxiliares. Las respuestas vacías esperadas en las proyecciones se interpretan dentro de los datos y conceptos formalizados. La integración tiene un mayor costo observado. No se deriva superioridad general de velocidad ni beneficio industrial.

La unión y el modelo integrado conservan los mismos vínculos contextuales declarados del caso. El integrado incorpora además las declaraciones del alineamiento, incluidos dos axiomas de tipado de la capacidad `a:FineGrinding`; sus entradas completas no son idénticas byte a byte. Los conteos de axiomas añadidos por el trabajador combinan consecuencias OWL 2 RL y SWRL. La atribución adicional a reglas se contrasta con la ablación funcional OWL_ONLY; las contribuciones de CONSTRUCT se cuentan por separado.

## Archivos conservados

- [Resultados funcionales](functional.json), [demostración de PROTYS-WS](demonstration.json) y [piloto de capacidad](capacity.json).
- [Resumen completo](summary.json), [resumen CSV](summary.csv) y [entorno de ejecución](environment.json).
- [Auditoría de las 120 ejecuciones](benchmark-audit.json), [manifiesto de evidencia](evidence-manifest.json) y [registro de copias idénticas](CONSERVACION_RESULTADOS.json).
- [Figura 4.7](figures/figura4_7_resultados_definitivos.png), también en SVG y PDF, con sus registros de procedencia y revisión visual.

El manifiesto identifica todos los archivos regulares conservados en `research/evaluation/current/`, su tamaño, SHA-256 y archivo comprimido. Los archivos completos se distribuyen como adjuntos de la misma versión de GitHub. Los originales de esas salidas permanecen intactos. Al extraer los adjuntos se conserva la estructura relativa del repositorio. Las solicitudes y registros históricos documentan las ubicaciones físicas de la ejecución original; una reproducción nueva registra sus propias ubicaciones.

La preparación y ejecución se describen en [research/README.md](../README.md), con `research/reproduce.sh` como punto de entrada. Los contratos del modelo mantienen sus estados de preparación: los resultados observados se acreditan con estas salidas y auditorías, sin reescribir las expectativas para convertirlas en resultados.

## Alcance de la reproducción

Los dos archivos `research/runtime/validator/classpath.txt` y `research/runtime/swrl-worker/classpath.txt` son metadatos históricos conservados por hash. Los ejecutores activos utilizan los archivos de classpath que la preparación genera dentro de `target/`. Las rutas personales de los dos archivos históricos no son instrucciones de ejecución.

Las pruebas y compilación de la interfaz se realizaron con React y React DOM 18.3.1, Tailwind CSS 3.4.19, react-scripts 5.0.1 y Jest 27.5.1. El paquete de la interfaz mantiene rangos de dependencia y carece de lockfile; no se certifica una reinstalación idéntica de todas sus dependencias. La evaluación académica utiliza el entorno Java y las dependencias fijadas en su protocolo, separado de las dependencias de la interfaz.

C26 comprueba los extremos que delimitan el intervalo de limpieza entre lotes consecutivos: fin del primer lote e inicio del siguiente. Su resultado no acredita integridad temporal global de todos los extremos del lote. La ausencia de un registro de limpieza indica falta de evidencia registrada, sin demostrar contaminación física.

Las IRI del modelo se usan como identificadores lógicos. Su resolución pública, un depósito con DOI y una disponibilidad FAIR completa quedan fuera de la certificación de esta versión. La herramienta semiautomática de conversión de estándares desde lenguaje natural y EXPRESS de Fraga, Vegetti y Leone (2017) permanece como parte de la investigación; su ejecución no se atribuye a esta evaluación de pintura.
