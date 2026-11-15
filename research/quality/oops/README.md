# Análisis con OOPS! (Evidencia E-09)

Análisis de los módulos de PROTYS(KB) con OOPS! (OntOlogy Pitfall Scanner!, https://oops.linkeddata.es), ejecutado el 14 de noviembre de 2026 sobre los archivos OWL de la etiqueta `tesis-cierre-2026-10-03-v1`. No modifica ninguna ontología.

| Archivo | Contenido |
|---|---|
| `oops_run.py` | Programa que repite el análisis |
| `EVIDENCIA_OOPS.log` | Registro de la ejecución: archivos cargados con su huella, cantidades de clases y propiedades, resultado por conjunto |
| `EVIDENCIA_OOPS_terminal.png` | El mismo registro como imagen |
| `OOPS_resumen.csv` | Una fila por problema y conjunto, con los elementos señalados |
| `respuesta_oops_*.rdf` | Respuesta del servicio para cada conjunto, sin editar |

Para repetirlo, desde la raíz del repositorio (requiere Python 3 con rdflib y acceso a Internet):

```sh
python3 research/quality/oops/oops_run.py . research/quality/oops/nueva-corrida
```

Las reglas SWRL se apartan antes del envío porque OOPS! no las evalúa. La tesis presenta el resultado en la Sección 3.6.3 (Tabla 3.13).
