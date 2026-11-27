# Ablación por regla

`ablacion.py` corre la red integrada con los datos del caso 25 veces: con las 22 reglas (ALL), sin cada una de ellas,
con el trabajador sin reglas (OWL_ONLY) y sin trabajador (NONE). Sólo cambia `enabled_rules`. Cada consulta se
compara con ALL como multiconjunto de filas, con la tolerancia de la evaluación. Incluye las 21 consultas del
catálogo, las 18 complementarias (`../consultas-qc/`) y las 4 ASK.

```sh
sh research/complementarias/correr.sh ablacion
```

Inferidos: triples del modelo final que no están en la entrada preparada de la misma corrida, sin nodos en blanco.

| Corrida | Consultas del catálogo que cambian | ASK que cambian | Inferidos (ALL: 923) |
|---|---|---|---:|
| sin R01 | Q01 Q03 Q06 Q08 Q11 Q17 Q19 | — | 867 |
| sin R09 | Q12 Q16 | — | 871 |
| sin R17 | Q14 | R26 | 921 |
| sin R21 | Q06 Q11 Q17 Q19 | — | 895 |
| sin cada una de las otras 18 | ninguna | — | |
| OWL_ONLY | Q01 Q03 Q06 Q08 Q11 Q12 Q14 Q16 Q17 Q19 | R26 | 567 |
| NONE | Q01 Q03 Q06 Q08 Q11 Q12 Q14 Q16 Q17 Q19 | R26 | 427 |

- De las 18 reglas que el catálogo no ejercita, 17 cambian su consulta complementaria al quitarlas (R02 cambia QC01
  y QC02; R04, QC03 y QC15). R15 no cambia ninguna de las 39 consultas con los datos del caso: su antecedente
  necesita un registro de mantenimiento `ACTIVE`, que sólo está en las instancias (ver `../consultas-qc/`).
- **Salidas:** `salidas/ablacion-regla-x-consulta.csv` (regla × consulta), `ablacion-detalle.json` (filas perdidas
  y nuevas), `complementarias.csv`, `resumen.json` y las respuestas de cada corrida en `salidas/corridas/`.
