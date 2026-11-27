# Diferencias entre v1.0.0 y v1.1.0

Compara `ontologies/` de la etiqueta `v1.0.0` (leída con `git show`) con el árbol de trabajo, triple a triple, y
clasifica cada diferencia como lógica, de importación o de anotación (`scripts/diferencias.py`). Corre las dos
versiones con el ejecutor académico en la configuración integrada, con los datos del caso y las 22 reglas
(`scripts/corridas.py`), compara inferidos y las 21 consultas (`scripts/comparar.py`) y cuenta clases y propiedades
por módulo (`scripts/censo.py`).

```sh
sh research/integration/diferencias-versiones/correr.sh
```

## Resultado

- **Archivos** (`salidas/diferencias.txt`):
  - `alignment-rules.owl`: la etiqueta de R03.
  - `resource-module.owl`: el comentario de `resource:Activity`.
  - `process-module.owl`: el comentario de `Process_Activity`, `owl:imports` del módulo de recursos y un axioma
    lógico, `process:Process_Activity rdfs:subClassOf resource:Activity`.
  - Nuevos en v1.1.0: `disjointness-module.ttl` (88 triples) y `paint-case-study-instances.ttl` (488 triples).
- **Corridas** (`salidas/corridas.txt`):

  | | v1.0.0 | v1.1.0 |
  |---|---:|---:|
  | Entrada | 4325 | 4326 |
  | Materializados | 5384 | 5420 |
  | Inferidos | 1129 | 1164 |

  Las dos son CONSISTENT.
- **Inferidos** (`salidas/comparacion.txt`): aparecen 36, todos por el nuevo axioma (las operaciones y las
  subclases de actividad pasan a ser `resource:Activity`); desaparece 1, `Process_Activity ⊑ owl:Thing`.
- **Consultas:** las 21 coinciden con las esperadas en las dos versiones.
- **Censo** (`salidas/censo.txt`): los ocho módulos base suman 87 clases y 119 propiedades (81 de objeto y 38 de
  datos) en las dos versiones.
