# Comparación de resultados funcionales

- Referencia: `/Users/Shared/protys-herramientas/referencia-v1/extraido/research/evaluation/current`
- Candidata: `research/release`
- Réplicas comparadas: 120 de referencia, 120 candidatas
- Respuestas SPARQL crudas comparadas: 3012

**Diferencias: 0**

| Archivo | Diferencias | Listas en otro orden con los mismos elementos |
|---|---:|---:|
| functional.json | 0 | 0 |
| capacity.json | 0 | 0 |
| demonstration.json | 0 | 0 |
| census.json | 0 | 0 |
| runs.jsonl | 0 | 0 |
| demonstration/http-responses | 0 | 1 |
| respuestas SPARQL crudas | 0 | 0 |
| hashes de serialización sin explicar | 0 | |

Hashes de serialización distintos con grafos isomorfos: 0.
Hashes de serialización distintos con grafos isomorfos sólo después de quitar los triples rdfs:comment: 69.

| Archivo | Campo | Criterio | Apariciones | Triples (ref. / cand.) |
|---|---|---|---:|---|
| functional.json | `input_sha256` | isomorfos sin rdfs:comment | 2 | 4725 / 4725, 5123 / 5123 |
| functional.json | `materialized_sha256` | isomorfos sin rdfs:comment | 2 | 5384 / 5384, 5972 / 5972 |
| capacity.json | `input_sha256` | isomorfos sin rdfs:comment | 1 | 50389 / 50389 |
| capacity.json | `materialized_sha256` | isomorfos sin rdfs:comment | 1 | 62867 / 62867 |
| demonstration.json | `file_sha256` | isomorfos sin rdfs:comment | 2 | 1374 / 1374, 4296 / 4296 |
| demonstration.json | `sha256` | isomorfos sin rdfs:comment | 1 | 4296 / 4296 |
| runs.jsonl | `input_sha256` | isomorfos sin rdfs:comment | 30 | 50389 / 50389 |
| runs.jsonl | `materialized_sha256` | isomorfos sin rdfs:comment | 30 | 62867 / 62867 |
