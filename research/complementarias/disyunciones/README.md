# Clases disjuntas

`disyunciones.py` propone 76 pares de clases de primer nivel de los módulos núcleo, producto, proceso, recurso,
empresa, ISO 15531 e ISO 14040, con la fuente de cada uno (definición y norma citada, `rdfs:comment` o ambas), y los
prueba con la red integrada, los datos del caso (`research/data/functional.ttl`) y las 22 reglas.
`modulo.py` prueba el módulo opcional `ontologies/disjointness-module.ttl` (75 pares) con el caso y con las
instancias ilustrativas (`ontologies/paint-case-study-instances.ttl`).

```sh
sh research/complementarias/correr.sh disyunciones
```

## Pares (`salidas/pares/`)

| Corrida | Antes de las reglas | Después de las reglas |
|---|---|---|
| Sin disyunciones | CONSISTENT | CONSISTENT |
| Con los 76 pares | INCONSISTENT | no se ejecuta |
| Cada par por separado (76 corridas) | 75 CONSISTENT, 1 INCONSISTENT | 75 CONSISTENT |
| Sin el par causante (75 pares) | CONSISTENT | CONSISTENT |

- **Par causante:** `core:Product ⊥ core:Resource`, ya antes de las reglas, por `f:TitaniumDioxide`, `f:Water`,
  `f:AcrylicResin` y `f:Biocide`.
  - Son `core:Resource` por su tipo afirmado `resource:Raw_Material` (`Raw_Material ⊑ Material ⊑ core:Resource`).
  - Son `core:Product` por ser objeto de `t:flowRefersToProduct`, subpropiedad de `a:flowRefersToProduct`, cuyo
    rango es `core:Product`.
- **Archivos:** `pares.csv` (pares y fuentes), `resultado.json` (estado de cada corrida y explicación), y
  `disjointness.ttl` y `disjointness-sin-pares-inconsistentes.ttl`, junto a los guiones.
- Las cuatro disyunciones que ya declara la red no se repiten.

## Módulo (`salidas/modulo/resultado.json`)

| Corrida (integrada, 22 reglas) | Estado | Entrada → materializados | P6 / P7 |
|---|---|---|---|
| Caso sin el módulo | CONSISTENT | 4326 → 5420 | — |
| Caso con el módulo | CONSISTENT | 4407 → 5499 | — |
| Caso e instancias sin el módulo | CONSISTENT | 4812 → 6230 | 5 / 3 |
| Caso e instancias con el módulo | CONSISTENT | 4893 → 6309 | 5 / 3 |

- Con el módulo, las 21 consultas y las 4 ASK dan lo mismo que sin él; P6 y P7 coinciden con sus esperadas.
- El módulo tiene 88 triples (75 `owl:disjointWith` y 13 de cabecera) y no declara clases ni propiedades: los ocho
  módulos base suman 87 clases y 119 propiedades (81 de objeto y 38 de datos) con y sin él.
