# Pruebas complementarias

Pruebas sobre el ejecutor académico (`research/runtime/validator`), sin Docker ni la plataforma. Cada corrida es un
proceso Java nuevo de `AcademicRunner` con `-Xmx2g` para el proceso principal y para el trabajador SWRL, con
muestreo del RSS cada 100 ms y límite de 240 s (`comun.py`, la misma función que usa la evaluación).

```sh
./research/reproduce.sh --prepare-only
sh research/complementarias/correr.sh [disyunciones] [consultas-qc] [ablacion] [curva]
```

| Carpeta | Qué muestra |
|---|---|
| [`disyunciones/`](disyunciones/README.md) | Pares de clases disjuntas y el módulo opcional `ontologies/disjointness-module.ttl` |
| [`consultas-qc/`](consultas-qc/README.md) | QC01–QC18 con sus respuestas esperadas |
| [`ablacion-reglas/`](ablacion-reglas/README.md) | Efecto de quitar cada regla sobre las 21 consultas, las 18 complementarias y las 4 ASK |
| [`curva-costo/`](curva-costo/README.md) | Tiempo y memoria con 20, 50, 100 y 200 lotes |

Cada carpeta guarda en `salidas/entorno.json` (o en la subcarpeta de cada tarea) el equipo, el sistema, el JDK, el
Python y las huellas de los archivos de `ontologies/`.
