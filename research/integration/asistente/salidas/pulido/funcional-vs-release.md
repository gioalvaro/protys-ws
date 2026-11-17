# Comparación de ejecuciones funcionales

- Base: `research/release/functional.json`
- Candidata: `research/evaluation/runs/20261117T155256Z-3329c609192f40ecb00b5425738ad06e/functional.json`

**Diferencias de resultados:** sí

## Configuraciones

| configuracion | campo | base | candidata |
|---|---|---|---|
| h3_extended | input_triples | 4665 | 4666 |
| h3_extended | dl_materialized_triples | 458 | 485 |
| h3_extended | materialized_triples | 5972 | 6008 |
| integrated | input_triples | 4325 | 4326 |
| integrated | dl_materialized_triples | 400 | 427 |
| integrated | materialized_triples | 5384 | 5420 |
| iso14040 | input_triples | 1137 | 1138 |
| iso14040 | dl_materialized_triples | 191 | 190 |
| iso15531 | input_triples | 2294 | 2295 |
| iso15531 | dl_materialized_triples | 272 | 271 |
| union | input_triples | 2947 | 2948 |
| union | dl_materialized_triples | 318 | 345 |
| union | materialized_triples | 3265 | 3293 |

## Consultas (cantidad de filas)

Sin diferencias.

## Comprobaciones contra respuestas esperadas

Sin diferencias.

## Aserciones

Sin diferencias.


## Archivos de entrada

Modificados: 14. Agregados: 1. Eliminados: 0.

- modificado: `ontologies/alignment-rules.owl`
- modificado: `ontologies/process-module.owl`
- modificado: `ontologies/resource-module.owl`
- modificado: `research/evaluation/capacity_pilot.py`
- modificado: `research/evaluation/demonstrate.py`
- modificado: `research/evaluation/empaquetar_evidencia.py`
- modificado: `research/evaluation/run_evaluation.py`
- modificado: `research/evaluation/verificar_benchmark.py`
- modificado: `research/model/build_rules.py`
- modificado: `research/model/census.json`
- modificado: `research/model/mechanisms.json`
- modificado: `research/model/mechanisms.md`
- modificado: `research/reproduce.sh`
- modificado: `research/runtime/README.md`
- agregado: `research/evaluation/output_paths.py`
