# Consultas complementarias QC01–QC18

Una consulta por cada regla que el catálogo de 21 consultas no ejercita. `definiciones.py` escribe `QC01.sparql` a
`QC18.sparql` y `tabla.csv` / `tabla.md` (regla, descripción de origen en `research/model/mechanisms.json`, pregunta
y consulta). `esperadas.py` calcula la respuesta esperada de cada una con Python sobre los triples afirmados de los
datos, sin SPARQL, sin razonador y sin reglas; de la red sólo toma la taxonomía declarada. `tabla.py` corre la red
integrada con las 22 reglas y compara.

```sh
sh research/complementarias/correr.sh consultas-qc
```

| Consulta | Caso: obtenidas | Caso: esperadas | Coincide | Caso + instancias: obtenidas | Caso + instancias: esperadas | Coincide |
|---|---:|---:|---|---:|---:|---|
| QC01 | 4 | 4 | sí | 4 | 4 | sí |
| QC02 | 20 | 16 (simple) / 20 (encadenada R02→R03) | no / sí | 20 | 16 (simple) / 20 (encadenada R02→R03) | no / sí |
| QC03 | 3 | 3 | sí | 3 | 3 | sí |
| QC04 | 4 | 4 | sí | 4 | 4 | sí |
| QC05 | 16 | 16 | sí | 16 | 16 | sí |
| QC06 | 4 | 4 | sí | 4 | 4 | sí |
| QC07 | 4 | 4 | sí | 4 | 4 | sí |
| QC08 | 21 | 21 | sí | 21 | 21 | sí |
| QC09 | 1 | 1 | sí | 1 | 1 | sí |
| QC10 | 4 | 4 | sí | 4 | 4 | sí |
| QC11 | 28 | 28 | sí | 28 | 28 | sí |
| QC12 | 0 | 0 | sí | 1 | 1 | sí |
| QC13 | 1 | 1 | sí | 1 | 1 | sí |
| QC14 | 112 | 112 | sí | 112 | 112 | sí |
| QC15 | 1 | 1 | sí | 1 | 1 | sí |
| QC16 | 21 | 21 | sí | 21 | 21 | sí |
| QC17 | 1 | 1 | sí | 1 | 1 | sí |
| QC18 | 1 | 1 | sí | 1 | 1 | sí |

- **QC02 (R03):** la esperada simple lee sólo los datos; la encadenada aplica primero R02 (`c:WaterFlow` como flujo
  del inventario de un proceso con consumo de agua) y después R03. Las 4 filas de diferencia son
  (BatchA, BatchB, BatchC, BatchZero, `c:WaterFlow`).
- **QC12 (R15):** necesita un registro de mantenimiento `ACTIVE`, que sólo está en las instancias
  (`f:PearlMill_01 i:hasMaintenance f:M01`). Sin R15, con las instancias, sólo cambia QC12.
- **Corridas:** caso, 4326 → 5420 triples; caso más instancias, 4812 → 6230; sin R15, 4812 → 6198.
- **Salidas:** `salidas/tabla.md`, `tabla.csv`, `resumen.json`, las esperadas en `salidas/esperadas/` y las
  respuestas de cada corrida en `salidas/corridas/`.
