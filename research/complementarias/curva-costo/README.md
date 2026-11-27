# Curva de costo

Tiempo y memoria de la red integrada (`integrated`: 22 reglas, 4 ASK y construcciones previas) y de la unión sin
correspondencias ni reglas (`union`) con 20, 50, 100 y 200 lotes, con el protocolo de la evaluación de rendimiento:
cada corrida es un proceso Java nuevo con `-Xmx2g` para el proceso principal y para el trabajador SWRL; la memoria
es el máximo observado del RSS de los dos, muestreado cada 100 ms; límite de 240 s. 30 repeticiones por par, 240
corridas, en orden aleatorio con la semilla 20261003.

```sh
sh research/complementarias/correr.sh curva
```

`cargas.py` genera las cargas en `cargas/` (no versionada) con el generador del caso (`research/data/generate_dataset.py`,
semilla 42). Las de 20, 100 y 200 lotes son idénticas byte a byte a `load-small`, `load-medium` y `load-large` de
`research/data/` (`salidas/cargas.log`); sus huellas están en `salidas/huellas-cargas.json`.

## Resultado (`salidas/tabla.md`)

| Lotes | Configuración | Corridas consistentes | Tiempo medio (s) | DE (s) | Mediana (s) | RSS medio (MiB) | DE (MiB) | Mediana (MiB) | Triples de entrada | Triples inferidos |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | integrated | 30/30 | 6.742 | 0.161 | 6.697 | 922.289 | 84.506 | 904.383 | 10995 | 4100 |
| 20 | union | 30/30 | 2.276 | 0.094 | 2.262 | 437.847 | 19.639 | 441.992 | 9506 | 1180 |
| 50 | integrated | 30/30 | 11.289 | 0.139 | 11.265 | 1212.129 | 151.481 | 1174.586 | 23544 | 9737 |
| 50 | union | 30/30 | 3.932 | 0.074 | 3.922 | 494.561 | 28.427 | 498.367 | 21845 | 2767 |
| 100 | integrated | 30/30 | 19.12 | 0.33 | 19.002 | 1601.916 | 171.336 | 1590.57 | 44445 | 19130 |
| 100 | union | 30/30 | 6.649 | 0.132 | 6.635 | 585.855 | 82.407 | 557.656 | 42396 | 5410 |
| 200 | integrated | 30/30 | 35.438 | 0.661 | 35.274 | 2668.044 | 290.411 | 2601.805 | 86289 | 37922 |
| 200 | union | 30/30 | 12.145 | 0.204 | 12.115 | 910.439 | 150.106 | 967.062 | 83540 | 10702 |

- **Tiempo:** `total_ms`, la operación dentro del proceso. El tiempo del proceso completo (`wall_process_ms`, con
  el arranque de la JVM) está en `salidas/resumen.csv`.
- **Triples:** de entrada, después de las construcciones previas. Inferidos: materializados menos entrada. Son los
  mismos en las 30 repeticiones de cada par.
- **Archivos:**
  - `salidas/corridas.jsonl`: una línea por corrida;
  - `salidas/corridas/`: la petición y el resultado de cada corrida;
  - `salidas/orden.json`: el orden de ejecución;
  - `salidas/resumen.json`: estadísticas con mínimo y máximo;
  - `salidas/entorno.json`: equipo, sistema, JDK y huellas de `ontologies/`.
