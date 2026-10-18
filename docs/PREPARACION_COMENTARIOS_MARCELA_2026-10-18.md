# Preparación del repositorio para los comentarios de la dirección (18-10-2026)

## Siglas y abreviaturas

| Sigla | Significado |
|---|---|
| ABox / TBox | Parte de una ontología con los individuos (datos) / con las clases, propiedades y axiomas |
| ASK | Consulta SPARQL que responde sí o no; aquí se usa en cuatro validaciones (R07, R16, R19, R26) |
| CI | Integración continua: la verificación automática de GitHub Actions (`.github/workflows/verify.yml`) |
| COM-01 | Decisión pendiente sobre la relación entre `resource:Activity` y `process:Process_Activity` |
| DEC-10 | Decisión pendiente sobre si `ManufacturingResource` incluye materiales (difiere de ISO 15531-32) |
| H-43, H-48, H-56 | Hallazgos de la evaluación externa del 16-10: fixtures ERP que parecen extracciones reales; vocabulario duplicado en anexos; rótulo de R03 |
| HermiT | Razonador OWL usado para perfil, consistencia y clasificación |
| JDK | Kit de desarrollo de Java; la evaluación exige la versión 17 |
| LCI | Inventario del ciclo de vida (ISO 14040) |
| N-01 | Decisión pendiente sobre axiomas de disyunción entre Process, Product, Resource y Enterprise |
| OWL | Lenguaje de ontologías web del W3C (perfil OWL 2 DL) |
| P1 / REP-01…04 | Arreglos del prototipo del 15-10 (rama `fix/p1-integrity-and-wizard-2026-10-15`) |
| Q01…Q21 | Las 21 consultas SPARQL evaluadas |
| R01…R26 | Reglas SWRL y validaciones ASK de la capa de alineamiento |
| SWRL | Lenguaje de reglas para OWL; se ejecuta con SWRLAPI/Drools |

## Para qué sirve este documento

La directora está revisando la versión del 5-10 de la tesis. Sus comentarios pueden pedir cambios de texto (sólo en los DOCX), cambios en el prototipo o cambios en el modelo (ontologías, reglas, datos o consultas). Este documento deja el repositorio en un estado conocido y verificado, y fija un procedimiento para que cualquier cambio del modelo se mida contra una ejecución de referencia antes de tocar la tesis.

Actualización del 18-10, mañana: por decisión del autor, las dos propuestas (COM-01 y H-56) se integraron en la rama `integracion/main-2026-10-18` y de ahí en `main`. Siguen sujetas a la revisión de la directora; si ella pide otra cosa, se revierten con `git revert` sin tocar las etiquetas de cierre. La tesis sigue citando `tesis-cierre-2026-10-03-v1` y `tesis-mantenimiento-2026-10-04-v1`, que no se movieron.

## Mapa de ramas

```
tesis-cierre-2026-10-03-v1 (a042810)              ← evidencia de los capítulos 3 y 4
  └─ tesis-mantenimiento-2026-10-04-v1 (b4ad8a3)
       └─ maintenance/reproducible-startup-2026-10-03 (a549298)
            └─ fix/p1-integrity-and-wizard-2026-10-15 (6dc6e43)
                 └─ preparacion/comentarios-marcela-2026-10-18
                      ├─ propuesta/h-56-rotulo-r03
                      │    └─ integracion/main-2026-10-18 = main  (+ COM-01)
                      └─ propuesta/com-01-process-activity-subsuncion
```

Cada rama contiene a la anterior, así que llevar cualquiera de ellas a `main` es un avance rápido (fast-forward), sin conflictos. El orden recomendado es: primero subir `fix/p1` y `preparacion` a origin como respaldo; después decidir si `main` avanza. Las etiquetas `tesis-cierre-2026-10-03-v1` y `tesis-mantenimiento-2026-10-04-v1` no se mueven: la tesis cita esos commits.

`fix/p1` y `preparacion` no modifican ontologías, datos, consultas, reglas ni respuestas esperadas. La única diferencia en los archivos de entrada con huella (hash) es `research/reproduce.sh`, por la corrección del control de versión de Java.

## Qué cambió en `preparacion/comentarios-marcela-2026-10-18`

1. **`research/reproduce.sh`.** El control de Java tomaba la primera línea de `java -version`. Si la variable `JAVA_TOOL_OPTIONS` está definida (pasa con proxys corporativos y con algunos entornos de CI), la JVM imprime antes un aviso y el script rechazaba un JDK 17 válido. Ahora busca la línea de versión.
2. **`scripts/comparar_ejecuciones.py`** (con pruebas en `scripts/tests/`). Compara dos `functional.json`: estado global, consistencia, perfil, clases insatisfacibles, triples materializados, filas por consulta, comprobaciones contra respuestas esperadas, aserciones y archivos de entrada modificados. No escribe en las ejecuciones ni en las respuestas esperadas. Los tiempos no cuentan como diferencias de resultado. Está en `scripts/` para no entrar en el inventario de entradas con huella de `research/`.
3. **Este documento.**

## Estado verificado

Las cuatro ramas pasan los mismos pasos que el trabajo `backend-and-research` del CI, y la de preparación además el trabajo `frontend`. El detalle está en la sección «Verificación», al final.

## Procedimiento para cada comentario de la directora

1. **Clasificar.** Sólo texto de la tesis → no se toca el repositorio. Prototipo (backend o interfaz) → rama `fix/...`. Modelo (ontologías, reglas, datos, consultas) → rama `propuesta/<id>-<tema>` desde `preparacion/comentarios-marcela-2026-10-18`.
2. **Editar la fuente, no la salida generada.**
   - Reglas SWRL y rótulos: `research/model/build_rules.py` → regenera `ontologies/alignment-rules.owl`.
   - Censo y registro de mecanismos: `research/model/audit_model.py` → regenera `census.json`, `mechanisms.json` y `mechanisms.md`.
   - Comentarios de módulos con fuente bibliográfica: `research/model/build_annotations.py` (sólo comentarios; aborta si cambia la lógica).
   - Clases y axiomas de los módulos base: directamente en `ontologies/*.owl`, y después `audit_model.py`.
   - Datos: `research/data/generate_dataset.py` (semilla 42); consultas: `research/model/queries/`.
   Los generadores `build_rules.py` y `audit_model.py` reproducen byte a byte los archivos de esta rama (comprobado el 18-10), así que su diff muestra sólo el cambio pedido.
3. **Ejecutar la etapa funcional** en una carpeta nueva (nunca se reutiliza una carpeta):
   ```sh
   export JAVA_HOME=/ruta/al/jdk-17
   ./research/reproduce.sh --functional --output research/evaluation/runs/<rama>-<fecha>
   ```
4. **Comparar contra la referencia** de `preparacion` (o contra `research/release/` para la evidencia publicada):
   ```sh
   python3 scripts/comparar_ejecuciones.py research/evaluation/runs/<referencia> research/evaluation/runs/<rama>-<fecha>
   ```
   Código de salida 0 = mismos resultados; 1 = hay diferencias, que se informan tal como salen.
5. **No ajustar respuestas esperadas para que «pase».** Si un cambio del modelo modifica una respuesta, la nueva expectativa se escribe a partir del escenario y de la decisión registrada, con fecha y fundamento, y se deja constancia en el commit.
6. **Etiquetar** la rama aceptada (`tesis-revision-<fecha>-v1`) y recién entonces actualizar las tablas y fragmentos de la tesis que citan resultados.

## Decisiones pendientes y archivos que tocarían

| Decisión | Archivos | Qué hay que volver a ejecutar | Estado en el repositorio |
|---|---|---|---|
| COM-01 Activity / Process_Activity | `ontologies/process-module.owl`, `ontologies/resource-module.owl`, `research/model/census.json` | Etapa funcional completa | Integrada en `main` (inclusión simple). Abiertos: si Setup, QualityControl, Inspection y Cleaning siguen bajo `Process_Activity`; si `Procedure_Activity` (ejecución) debe dejar de ser subclase de una definición |
| H-56 rótulo de R03 | `research/model/build_rules.py` → `alignment-rules.owl`, `mechanisms.*`, `census.json` | Etapa funcional (sólo cambia una anotación) | Integrada en `main` |
| N-01 disyunciones | `ontologies/core-concepts.owl` o módulos base | Etapa funcional; es probable que el TiO₂ (producto y recurso a la vez) vuelva inconsistente el caso | Sin rama: requiere decisión previa |
| DEC-10 materiales como recurso | `ontologies/iso15531-module.owl` (rangos de `hasInput` y `consumesMaterial`), consultas Q02 y Q04 | Etapa funcional; cambian respuestas esperadas | Sin rama: requiere decisión previa |
| H-43 fixtures ERP | `ontologies/paint-instances-adempiere.ttl`, `ontologies/paint-instances-odoo.ttl` | Ninguna (no alimentan la evaluación) | Se aclara abajo; los archivos no se tocan porque tienen huella en la evidencia publicada |
| H-48 vocabulario duplicado | `research/data/functional.ttl` y anexos | Etapa funcional si se elimina la duplicación | Sin rama |

### Sobre los fixtures ERP (H-43)

`ontologies/paint-instances-adempiere.ttl` y `ontologies/paint-instances-odoo.ttl` son fixtures ilustrativos del demostrador ERP. No provienen de una extracción ejecutada sobre un ERP real y no están en las `abox_paths` de ninguna configuración de `research/catalog.json`, de modo que no intervienen en las 21 consultas ni en las aserciones. Se dejan sin cambios porque sus huellas figuran en `research/release/functional.json`.

## Verificación

Ejecutado el 18-10-2026 en un contenedor Linux con Temurin/OpenJDK 17.0.20.1, Maven Wrapper del proyecto, Python 3.13 y Node 24.18.0 con npm 11.16.0 (las versiones que declara `frontend/package.json`). No es una corrida del CI remoto: el trabajo `docker-demonstration` no se ejecutó.

| Rama (commit verificado) | Preparación de motores | Backend (`mvnw verify`) | Preservación | Scripts | Funcional | Interfaz |
|---|---|---|---|---|---|---|
| `fix/p1-integrity-and-wizard-2026-10-15` (6dc6e43) | OK | 118 pruebas, 0 fallos | 16 OK | 6 OK | PASS | 61 pruebas en 13 suites, compilación OK |
| `preparacion/comentarios-marcela-2026-10-18` (aa0541f), con `JAVA_TOOL_OPTIONS` definida | OK | 118, 0 fallos | 16 OK | 11 OK | PASS | 61 en 13 suites, compilación OK |
| `propuesta/com-01-process-activity-subsuncion` | OK | 118, 0 fallos | 16 OK | 11 OK | PASS | — |
| `propuesta/h-56-rotulo-r03` | OK | 118, 0 fallos | 16 OK | 11 OK | PASS | — |

Comparaciones con `scripts/comparar_ejecuciones.py`:

- **Evidencia publicada (`research/release/`) frente a `fix/p1`:** sin diferencias de resultados. Sólo cambian las huellas de los scripts de ejecución de `research/evaluation/`, `research/reproduce.sh` y `research/runtime/README.md`, y se agrega `research/evaluation/output_paths.py`, todo del mantenimiento posterior al cierre. Es decir, el código actual reproduce exactamente las respuestas de los capítulos 3 y 4.
- **`fix/p1` frente a `preparacion`:** sin diferencias de resultados; cambia sólo la huella de `research/reproduce.sh`.
- **`preparacion` frente a `propuesta/h-56-rotulo-r03`:** sin diferencias de resultados (el cambio es una anotación).
- **`preparacion` frente a `propuesta/com-01-process-activity-subsuncion`:** las 21 consultas, las cuatro validaciones ASK, las 105 comprobaciones contra respuestas esperadas y las 243 aserciones dan lo mismo; los archivos de resultados de las consultas son idénticos byte a byte. La consistencia no cambia. Cambian sólo los conteos de triples: hay un triple de entrada más (el axioma) y, en las configuraciones unión, integrada y H3, el razonador deduce 28 tipos `resource:Activity` nuevos para las operaciones del caso (que son subclases de `Process_Activity` en la extensión de pintura) y deja de deducir el trivial `Process_Activity ⊑ owl:Thing` (27 triples netos). En las configuraciones ISO 15531 e ISO 14040 no hay individuos de `Process_Activity`, así que sólo desaparece esa deducción trivial.

