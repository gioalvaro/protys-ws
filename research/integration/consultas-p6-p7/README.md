# P6 y P7 con las instancias del caso

Carga `ontologies/paint-case-study-instances.ttl` (488 triples) como módulo sobre la red integrada de la plataforma y
corre P6 y P7 (`consultas/`), sin razonar y después de razonar, contra sus respuestas esperadas (`esperadas/`,
generadas por `scripts/esperadas.py` sobre los triples afirmados, sin razonador). Compara además el catálogo Q01–Q21
con y sin las instancias y controla que el grafo del caso no cambie.

```sh
PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/consultas-p6-p7/correr.sh
```

Proyecto Compose `protys-v110`; se recrea y al terminar se baja.

## Resultado

| | Sólo el caso | Caso e instancias |
|---|---:|---:|
| Triples de entrada | 4318 | 4804 |
| Inferidos | 1167 | 1447 |
| P6 (sin razonar / razonada) | — | 5 / 5 filas, iguales a las esperadas |
| P7 (sin razonar / razonada) | — | 3 / 3 filas, iguales a las esperadas |
| Q20 | 4 filas | 8 filas |

- **Catálogo** (`salidas/06-catalogo-con-y-sin-instancias.txt`): sólo Q20 cambia. Las 4 filas nuevas son
  `paint-ext:QualityControl_Stage` y `paint-ext:RawMaterialInspection` sin `i:hasInput` ni `i:hasMeasurement`.
- **Controles** (`salidas/07-controles.txt`):
  - el grafo del caso es el mismo;
  - sobre los individuos del caso aparece un único inferido nuevo, `f:PearlMill_01 a i:temporarilyUnavailable`,
    que es R15 aplicada al registro de mantenimiento `ACTIVE` de las instancias;
  - no hay `owl:sameAs` con individuos del caso ni individuos ajenos al caso y al archivo.
- **Capturas:** `capturas/P6-consola.png` y `capturas/P7-consola.png` muestran las dos consultas en la consola SPARQL.
- **Registros:** el estado completo de la plataforma está en `salidas/*estado*.json` y el registro del backend en
  `salidas/09-backend.log`.
