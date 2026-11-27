# Comparación con la ejecución del 3-10-2026

Compara los resultados de esta versión (ejecución del 26-11-2026, hora de Argentina) con los de la ejecución del 3-10-2026. Los datos, las reglas, las consultas y el protocolo son los mismos; entre ambas sólo cambiaron textos de los comentarios en las ontologías (`rdfs:comment` y comentarios XML), de los que se quitaron referencias a la numeración de la tesis y a un archivo de consultas que ya no está.

## Referencia

Los 51 archivos comprimidos de la ejecución del 3-10-2026, descargados de su publicación y extraídos. Cada archivo comprimido y cada uno de los 6117 archivos extraídos coinciden en SHA-256 con [referencia-evidence-manifest.json](referencia-evidence-manifest.json), el manifiesto de esa ejecución. El censo de referencia es `research/model/census.json` de esa versión.

## Comando

```sh
python3 scripts/comparar_ejecuciones.py REFERENCIA/research/evaluation/current research/release \
  --census-referencia CENSO_DE_REFERENCIA \
  --census-candidata research/model/census.json \
  --respuestas-referencia REFERENCIA/research/evaluation/current/demonstration/http-responses \
  --respuestas-candidata research/evaluation/current/demonstration/http-responses \
  --crudos-referencia REFERENCIA/research/evaluation/current \
  --crudos-candidata research/evaluation/current \
  --ignorar-comentarios \
  --json research/release/comparacion/comparacion.json
```

`research/model/census.json` se regeneró con `research/model/audit_model.py` antes de la ejecución; volver a generarlo sobre una copia de este árbol da el mismo archivo byte a byte. Python 3.9.6 y rdflib 7.6.0.

## Resultado

Sin diferencias (código de salida 0). Detalle en [comparacion.md](comparacion.md) y [comparacion.json](comparacion.json).

- `functional.json`: las 243 aserciones, las 105 comparaciones y los conteos de cada configuración son iguales.
- `runs.jsonl`: las 120 réplicas, comparadas por configuración y número de réplica, tienen los mismos estados, conteos de triples, axiomas y filas por consulta.
- `capacity.json` y `demonstration.json`: iguales.
- `census.json`: los mismos conteos (87 clases, 81 propiedades de objeto y 38 de datos en la base de ocho módulos; 134, 119 y 109 en la unión completa). Cambian sólo los SHA-256 de los archivos de cada módulo.
- Las 49 respuestas HTTP de la demostración: iguales. Una lista (reglas SWRL devueltas por la API) viene en otro orden con los mismos elementos.
- 3012 respuestas SPARQL guardadas (consultas funcionales, de cada réplica, del piloto y de las aserciones): iguales.
- 69 hashes de archivos RDF que el ejecutor escribe o recibe son distintos: la entrada al razonador SWRL (`input_sha256`, 33), la materialización (`materialized_sha256`, 33), la entrada OWL de la demostración (2) y el archivo de reglas subido a la API (1). En los 69 casos los dos grafos tienen la misma cantidad de triples y son isomorfos después de quitar los triples `rdfs:comment`; en dos de esos grafos se cotejaron además los literales: los 81 comentarios distintos pertenecen a los mismos sujetos y todos son textos corregidos. Los hashes de las deducciones SWRL son iguales.

El comparador no cuenta tiempos, memoria, fechas, rutas, puertos, identificadores de proceso, de contenedor ni UUID, ni los hashes de los archivos fuente.

## Tiempo y memoria

30 réplicas por configuración. Tiempo interno total (`total_ms`) y máximo observado de RSS del proceso y su trabajador.

| Configuración | Ejecución | Tiempo, media ± DE (s) | Mediana (s) | Mín.–máx. (s) | RSS, media ± DE (MiB) | Mediana (MiB) | Mín.–máx. (MiB) |
|---|---|---:|---:|---:|---:|---:|---:|
| ISO 15531 | 26-11 | 5,452 ± 0,184 | 5,405 | 5,114–5,954 | 582,77 ± 61,69 | 566,80 | 536,55–864,20 |
| | 3-10 | 5,374 ± 0,078 | 5,381 | 5,218–5,516 | 626,98 ± 109,72 | 591,22 | 528,25–905,89 |
| ISO 14040 | 26-11 | 2,584 ± 0,143 | 2,539 | 2,416–3,007 | 438,86 ± 17,30 | 434,54 | 400,33–479,72 |
| | 3-10 | 2,526 ± 0,100 | 2,504 | 2,353–2,781 | 445,04 ± 17,74 | 444,45 | 421,28–495,31 |
| Unión sin alineamientos | 26-11 | 6,987 ± 0,262 | 6,967 | 6,437–7,706 | 572,82 ± 59,13 | 547,66 | 514,06–735,73 |
| | 3-10 | 6,915 ± 0,124 | 6,929 | 6,701–7,165 | 567,49 ± 51,01 | 550,84 | 523,69–772,16 |
| Modelo integrado | 26-11 | 20,168 ± 0,429 | 20,257 | 19,539–21,227 | 1.512,19 ± 179,67 | 1.522,80 | 1.154,33–1.820,72 |
| | 3-10 | 19,859 ± 0,285 | 19,813 | 19,460–20,535 | 1.479,41 ± 192,02 | 1.426,81 | 1.194,69–1.972,86 |

Entornos: el mismo equipo (Apple M4 Pro, Mac16,8, 14 núcleos, 24 GiB), Temurin 17.0.20.1, Python 3.9.6, Maven 3.9.9 y las mismas versiones de los motores. Cambió el sistema: macOS 27.2 el 26-11 y macOS 26.5.2 el 3-10.
