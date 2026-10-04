# Evaluación reproducible de PROTYS

Este paquete comprueba el comportamiento de las formalizaciones, reglas y consultas usadas en los capítulos 3 y 4 de la tesis. Los datos representan un caso simulado de fabricación de pintura base agua. Los resultados funcionales se contrastan con respuestas esperadas; las mediciones describen el costo computacional de ejecutar ese caso. No son observaciones de una planta industrial ni validan una integración con sistemas ERP o MES reales.

## Preparación

Se requieren un JDK 17, Python 3.9 o posterior y acceso a los repositorios de dependencias Maven durante la primera preparación. El repositorio incluye el envoltorio Maven en `backend/mvnw`. Desde la raíz del repositorio, configure `JAVA_HOME` para su JDK 17 y ejecute:

```sh
# Una salida explícita permite continuar en otra terminal o sesión.
./research/reproduce.sh --functional --output research/evaluation/runs/mi-ejecucion
./research/reproduce.sh --benchmark --output research/evaluation/runs/mi-ejecucion
```

Sin argumentos, `reproduce.sh` ejecuta ambas etapas en ese orden. La etapa de rendimiento exige un informe funcional `PASS` y los mismos hashes de entradas y binarios. Si se modifica el modelo, el catálogo, los datos o el ejecutor, se debe repetir la etapa funcional antes de medir. Sin una salida explícita, cada invocación crea una carpeta nueva `research/evaluation/runs/<fecha-UTC>-<UUID>/` y muestra su ruta absoluta. `--output` selecciona la carpeta, o puede fijarse `PROTYS_EVALUATION_OUT` para los productores y lectores. Las rutas relativas se resuelven desde el repositorio, aunque el comando se invoque desde otro directorio. `--output` tiene prioridad sobre esa variable.

Cada fase se reserva antes de escribir. Una fase ya iniciada, completada o parcial se rechaza; no se elimina una carpeta previa ni se reinician sus archivos. La continuación `--benchmark` requiere `functional.json` en la carpeta seleccionada y conserva su entorno y sus resultados. Si la etapa queda interrumpida, conserve esa carpeta y elija otra para repetirla: no hay reanudación parcial. El bloqueo `.evaluation-lock` impide productores simultáneos sobre la misma carpeta. Si un proceso termina abruptamente y deja el bloqueo, no lo retire mientras exista un productor activo; conservar la carpeta y usar una nueva evita reutilizar evidencia incompleta.

Para preparar los dos motores sin evaluar ni escribir resultados:

```sh
./research/reproduce.sh --prepare-only
```

Esta preparación compila en `research/runtime/*/target/` y ejecuta el smoke test del trabajador; requiere Java 17 y las dependencias Maven. No inicia el backend, la interfaz ni las 120 réplicas. `PYTHON` permite seleccionar el intérprete del punto de entrada. Las salidas publicadas en `research/evaluation/current/` y el manifiesto v1 de `research/release/` se preservan. Ese manifiesto describe las fuentes del tag `tesis-cierre-2026-10-03-v1`, no las modificaciones posteriores: la fuente de mantenimiento necesita su propia ejecución y manifiesto. La auditoría contra fuentes o binarios distintos debe rechazar el cotejo.

La preparación compila dos procesos Java separados:

| Función | Dependencias |
|---------|--------------|
| Perfil OWL 2 DL, consistencia y clasificación | OWL API 5.1.20, HermiT 1.4.5.519, Jena 4.10.0 |
| Ejecución de reglas | SWRLAPI 2.1.3, SWRLAPI Drools Engine 2.1.3, Drools 7.74.1.Final, OWL API 4.5.27 |

HermiT 1.4.5.456 produjo una incompatibilidad con OWL API 5.1.20 en la combinación inicialmente propuesta. La versión 1.4.5.519 superó los controles locales de ontología válida e inconsistente. La evidencia se conserva en `research/evaluation/version-compatibility/`.

El descriptor padre de OWL API 4.5.27 se conserva en `research/runtime/vendor/`, con su procedencia y hash. No se sustituye silenciosamente por otra versión. Los archivos OWL intercambiados entre procesos y las dependencias ejecutadas se identifican por SHA-256.

## Modelo y datos

`research/catalog.json` identifica las fuentes, las cuatro configuraciones de comparación, las 21 consultas, las cuatro validaciones ASK y las preguntas de competencia. `research/model/census.json` cuenta declaraciones OWL únicas por IRI y distingue los ocho módulos base de la unión con extensiones y perfiles locales. Esos conteos no son equivalentes a cantidades de registros, individuos o triples.

Los perfiles de cada estándar conservan sus conceptos. Las correspondencias del caso se expresan mediante relaciones explícitas entre representaciones identificadas; no afirman que una frontera ambiental sea una fábrica ni que un inventario sea un plan de proceso. El piloto separado de ISO 10303 es un fragmento local derivado de AP242, con reglas acotadas al ejemplo. No certifica conformidad completa con STEP ni incluye un importador general de archivos STEP.

Los generadores y sus manifiestos se conservan en `research/data/` y `research/model/`. La semilla de generación es 42. Los casos funcionales pequeños incluyen respuestas esperadas, datos ausentes, denominadores cero, unidades incompatibles, umbrales y vínculos ajenos. Las cargas para medir rendimiento son independientes de esos casos de prueba. Los archivos CSV y RDF son representaciones de registros sintéticos; no constituyen una extracción de ERP, MES o LCA industriales.

El generador de datos usa sólo la biblioteca estándar de Python. Para regenerar una copia sin sustituir las entradas canónicas, desde la raíz ejecute:

```sh
python3 research/data/generate_dataset.py --seed 42 --output-dir research/evaluation/provisional/regenerated-data
```

El directorio de salida debe estar dentro del repositorio para conservar rutas relativas en el manifiesto. Sus archivos RDF y la especificación del escenario pueden cotejarse por hash con los archivos canónicos. El manifiesto de la copia conserva la nueva ruta de salida; esa diferencia de ruta no representa una diferencia de datos. Los generadores XML del modelo son herramientas de mantenimiento separadas, requieren `lxml` y modifican los artefactos canónicos; la reproducción de la evaluación usa los artefactos publicados y no requiere regenerarlos.

El escenario tiene siete etapas, con control de calidad antes del envasado. `PearlMill_01` tiene una cámara de 50 L y potencia de 30 kW. Los volúmenes mayores se dividen en subcargas secuenciales; capacidad, duración, potencia y energía son magnitudes distintas. No se incluye `DryingDrum_01` en el escenario canónico. Los archivos históricos de ERP permanecen separados y no alimentan la evaluación nueva.

## Indicadores y alcance

Q06 calcula la intensidad de emisiones eléctricas de producción en kg CO₂e/L. Incluye sólo la electricidad consumida por las operaciones, con factores declarados como supuestos del escenario. Excluye la fabricación de materias primas y otras fuentes sin datos; no es una huella de ciclo de vida completa. Las contribuciones se conservan por operación antes de sumarlas, por lo que dos contribuciones iguales de operaciones diferentes no se pierden por el colapso de un triple RDF.

Q07 calcula `abs(entrada − salida − pérdidas registradas)`, en masa y contabilizando las adiciones. Acepta un residuo de hasta el 1 % de la entrada, inclusive. Los valores ausentes o las unidades incompatibles tienen casos explícitos de prueba. Q08 compara descriptivamente rendimiento másico e intensidad de emisiones; no genera un índice combinado ni recomendaciones automáticas.

La unidad de las magnitudes de Q07 está fijada por los predicados `inputMassKg`, `outputMassKg` y `registeredLossKg`. La consulta no convierte unidades. El control con magnitudes declaradas en gramos y sin los campos canónicos en kg no produce un balance; una cifra físicamente mal etiquetada en un predicado `MassKg` requiere revisión de la fuente y no puede detectarse sólo a partir de su valor numérico.

Los factores hipotéticos de materiales usados en consultas complementarias se separan del inventario eléctrico. Un factor de emisiones no se declara como factor de caracterización de impactos. La precedencia temporal se limita al mismo lote y proceso; no demuestra causalidad. La falta de un registro de limpieza del equipo dentro del intervalo requerido señala falta de evidencia registrada y no prueba contaminación física.

Las decisiones C030 a C033 delimitan cuatro contratos del caso. R04 compara un indicador con un registro de umbral vinculado explícitamente a ese indicador y con la misma unidad y categoría; no compara magnitudes incompatibles. R15 marca indisponibilidad temporal cuando el mantenimiento figura como `ACTIVE` en la instantánea; un registro histórico, planificado o sin estado no basta. Cada operación seleccionada por su lote y proceso requiere un valor energético y una identidad de factor eléctrico seleccionada, con valores coherentes. Literales numéricamente equivalentes, como `100` y `100.0`, se aceptan sin duplicar la contribución ni la suma. Los extremos del intervalo de limpieza también deben tener valores numéricos unívocos y ordenados.

## Comprobaciones funcionales

Las 21 consultas se contrastan con archivos de respuestas esperadas en `research/model/expected/`. La comparación verifica variables y todas las filas como multiconjunto, con tolerancia numérica declarada. Una salida vacía sólo se acepta cuando corresponde al caso definido. Las pruebas adicionales de `research/data/fixtures/tests.json` cubren reglas, patrones ASK y condiciones de límite.

Los contratos del modelo, `competency-contract.json`, `h3-extension-contract.json` y `approved-contracts.json`, describen el alcance, las decisiones y las expectativas preparados antes de la ejecución. Conservan sus estados de preparación; esos campos no acreditan resultados ni cierre. Las respuestas esperadas se definen desde el escenario y las consultas y se cotejan después con las salidas del motor. Los resultados observados se registran en `functional.json` dentro de la carpeta de ejecución seleccionada y en las auditorías de evidencia; la publicación conserva sus copias y manifiestos en `research/release/`. No se modifican las entradas para marcar un `PASS` a partir de la salida que deben contrastar.

Las configuraciones de ISO 15531, ISO 14040 y unión tienen respuestas esperadas propias, incluidas las ausencias definidas por la proyección de sus entradas y sus mecanismos disponibles. La diferencia entre esas respuestas y las del modelo integrado no demuestra una limitación universal de los estándares. Las consultas de ausencia de registros se interpretan dentro de su contexto de entrada.

La ejecución distingue `CONSISTENT`, `INCONSISTENT` y `NOT_EVALUATED`. Un error de carga o del razonador no acredita consistencia. Los patrones ASK comprueban registros presentes o ausentes en una instantánea cerrada y no convierten la ausencia de información en una contradicción OWL.

La consistencia OWL se informa en `owl_validation_status` y la evaluación numérica en `numeric_evaluation`; son comprobaciones diferentes. Esta última distingue `VALID`, `AMBIGUOUS_INPUT`, `NOT_EVALUABLE` y `NOT_APPLICABLE`. Una ambigüedad energética o de factor bloquea SWRL y las consultas solicitadas, con estado de ejecución `NOT_EVALUATED`, aunque OWL sea consistente. Los datos numéricos ausentes, incompatibles o incoherentes se señalan como no evaluables; el razonamiento estructural puede continuar, pero las consultas no presentan un total parcial del lote afectado como un agregado completo. Un vínculo con una operación de otro proceso se excluye del cálculo y permanece visible en el control de contexto.

Los controles numéricos integrados se habilitan sólo en las configuraciones integrada y H3. Las tres fuentes de comparación declaran `numeric_controls_enabled=false` y no se certifican mediante ese contrato eléctrico. `output_checked=false` distingue una comprobación de entradas de una comprobación de las salidas GHG después del trabajador. En el prototipo REST, una ejecución bloqueada devuelve HTTP 422 con su estado y motivo, y vacía el grafo de inferencias anterior para impedir que sus resultados se reutilicen como aprobación vigente.

En la ejecución académica, la conservación se comprueba por inclusión exacta de conjuntos de axiomas no SWRL, incluidas sus anotaciones cuando sean axiomas. Se cotejan los originales durante la clasificación y el conjunto completo posterior a la inferencia al guardar y recargar el modelo materializado. El proceso principal comprueba ese archivo completo frente al modelo leído por Jena y los axiomas de entrada por separado. Las reglas desactivadas se cuentan aparte. Una pérdida impide aceptar la ejecución; un cambio de identificador de un individuo anónimo que no pueda cotejarse se informa como no evaluado, sin afirmar una pérdida científica demostrada. El control no acredita igualdad byte a byte de RDF, cabeceras de ontología, imports retirados previamente ni representación de los estándares ISO completos. La demostración REST comprueba los mismos motores, las 21 salidas y el cambio de configuración, pero su API no expone el recibo completo de retención ni conserva los intercambios internos del servicio; la evidencia directa de RF2 corresponde al paquete académico.

`new_axiom_count` cuenta la diferencia entre conjuntos de axiomas OWL antes y después del trabajador. `engine_inferred_axiom_count` es un contador interno del motor; el total de axiomas del archivo de deducciones recargado puede incluir declaraciones añadidas durante su lectura. Son cantidades diferentes. El control `OWL_ONLY` y los casos específicos de cada regla respaldan la atribución de consecuencias adicionales; el contador total del motor por sí solo no identifica su causa.

El catálogo conserva 22 reglas SWRL, cuatro ASK canónicos y 21 SELECT. Además identifica seis ASK auxiliares: C26 comprueba la posibilidad de evaluar el intervalo de limpieza, CCTX revisa los vínculos declarados entre lote, operación, proceso y plan, y los cuatro controles CNUM delimitan aplicabilidad, ambigüedad, entradas inválidas y salidas inválidas. Dos CONSTRUCT preparan marcas de comprobación sobre individuos ya existentes: `R07-materialize.sparql` y `CNUM-materialize.sparql`. Esta preparación SPARQL se cuenta por separado de las consecuencias SWRL y no crea individuos nuevos.

C26 distingue `NOT_APPLICABLE`, `NOT_EVALUABLE`, `MISSING_CLEANING_RECORD` y `COMPLETE_CLEANING_RECORD`. El registro completo se refiere al equipo y al intervalo declarados, sin aprobar una condición física de limpieza. Un par sin equipo compartido o sin extremos numéricos unívocos y ordenados no se interpreta como limpieza acreditada, aunque el ASK de ausencia no devuelva una infracción. CCTX distingue `CONTEXT_VALID`, `CONTEXT_INTEGRITY_ERROR` y `NOT_APPLICABLE`; un error de esos vínculos no se presenta como inconsistencia OWL ni como aprobación global del conjunto de datos.

El intercambio del ejecutor usa `enabled_rules` como una cadena textual: `NONE`, `OWL_ONLY`, `ALL` o identificadores separados por comas, por ejemplo `R01,R21`. El productor normaliza las listas de las fichas de prueba después de sus ajustes y las guarda en ese formato. Cada petición conservada permite cotejar la selección con las reglas importadas y activas.

La clasificación exporta consecuencias sobre individuos nombrados y propiedades. No crea testigos de restricciones existenciales ni pretende producir una clausura RDF completa de OWL 2 DL. El trabajador de reglas puede añadir consecuencias tanto de OWL 2 RL como de SWRL. El control `OWL_ONLY`, con las mismas entradas y sin reglas SWRL activas, permite separar las consecuencias adicionales atribuibles a esas reglas. Los triples añadidos por CONSTRUCT se cuentan por separado.

El piloto comprueba consistencia, conservación de las respuestas previas y una capacidad nueva. Conserva la identidad de los individuos reclasificados y las clases de origen. La matriz de preguntas relaciona P1 a P5 con consultas, datos, salidas e interpretación; el alcance comprobado corresponde a la fase de producción formalizada.

## Mediciones

Se comparan ISO 15531, ISO 14040, la unión sin axiomas ni reglas de alineamiento y el modelo integrado. La unión y el modelo integrado reciben las mismas relaciones contextuales declaradas del escenario; la integración añade los axiomas que relacionan esas propiedades con el modelo y los mecanismos ejecutables. La configuración de unión no representa una ausencia absoluta de correspondencias en los registros de entrada. Las configuraciones no ejecutan todas las mismas tareas semánticas: los resultados describen consultas comunes, capacidades adicionales y costo de integración, sin presuponer que el modelo integrado sea más rápido.

El protocolo exige 30 réplicas por configuración, 120 en total, con orden aleatorio independiente de la generación de datos y semilla 20261003. Cada réplica inicia una JVM nueva y, cuando corresponde, un trabajador SWRL nuevo. No reutiliza tiempos de la caché de la aplicación. Las cachés de disco del sistema operativo, el estado térmico y la planificación de CPU no se controlan.

Cada proceso Java tiene un límite de heap de 2 GiB; la ejecución tiene un límite de 240 segundos por réplica. La memoria informada es el máximo observado de la suma de RSS del proceso principal y su trabajador, muestreada cada 100 ms. No es un pico instantáneo ni una medida de memoria viva del heap. Los tiempos por fase, el tiempo total interno y el tiempo de proceso se informan separadamente.

La auditoría exige una observación de RSS positiva para cada ejecución completada. Un valor de cero no acredita una medición de memoria y no se acepta como un resultado de rendimiento.

Las réplicas caracterizan variabilidad computacional sobre un conjunto simulado fijo. No son observaciones industriales independientes y no sustentan, por sí mismas, ventajas operativas o estadísticas sobre una población de fábricas.

## Lectura de las salidas

| Archivo o carpeta | Contenido |
|-------------------|-----------|
| `environment.json` | Fecha, hardware, sistema y versiones ejecutadas |
| `benchmark-environment.json` | Entorno de la sesión de medición, sin reemplazar el snapshot funcional |
| `artifacts.json`, `runtime-binaries.json` | Hashes de fuentes y binarios |
| `functional.json` | Comprobaciones funcionales y resultado global |
| `functional/`, `assertion-groups/` | Peticiones, resultados, modelos y errores de las pruebas |
| `replicate-order.json` | Orden y semilla de las 120 réplicas |
| `runs.jsonl`, `runs/` | Mediciones y evidencia de cada réplica |
| `benchmark-input-hashes*.json`, `benchmark-runtime-hashes*.json` | Estabilidad de entradas y binarios antes y después |
| `summary.json`, `summary.csv` | Distribuciones descriptivas por configuración |

## Auditoría, archivos de evidencia y figura de resultados

Después de completar las etapas funcional y de rendimiento, desde la raíz del repositorio ejecute:

```sh
python3 research/evaluation/verificar_benchmark.py \
  --repo . --evaluation-dir research/evaluation/runs/mi-ejecucion \
  --output research/release/mi-ejecucion/benchmark-audit.json
python3 research/evaluation/empaquetar_evidencia.py \
  --repo . --evaluation-dir research/evaluation/runs/mi-ejecucion \
  --audit research/release/mi-ejecucion/benchmark-audit.json \
  --assets ../protys-evidence/mi-ejecucion \
  --manifest research/release/mi-ejecucion/evidence-manifest.json \
  --tag mi-ejecucion
```

El verificador lee los archivos conservados y reconstruye las solicitudes, los grupos de pruebas, el orden de las réplicas, las estadísticas y los hashes. Comprueba los resultados esperados y los motivos de los fallos definidos, sin ejecutar nuevamente el modelo. El empaquetador repite esa auditoría, conserva todos los archivos regulares de la carpeta seleccionada, comprueba cada miembro de los archivos comprimidos y vuelve a cotejar las entradas y las salidas. No use Python con optimización (`-O`): las utilidades rechazan esa modalidad para mantener sus condiciones de comprobación. Los informes y manifiestos anteriores se preservan; al repetir el procedimiento, elija nombres y destinos nuevos.

La etiqueta del ejemplo es un identificador nuevo para los archivos; no reemplaza la versión v1 publicada. La creación de archivos locales no demuestra que esa etiqueta o sus archivos estén publicados. La comprobación remota de commit, etiqueta y archivos se registra por separado.

El gráfico científico se genera con las 120 mediciones conservadas y su auditoría. Su entorno de dibujo es opcional y separado del entorno requerido para ejecutar el modelo; la combinación comprobada usa Python 3.12 y las dependencias fijadas en `research/evaluation/figures-requirements.txt`. Por ejemplo:

```sh
python3.12 -m venv research/.cache/figures-venv
research/.cache/figures-venv/bin/python -m pip install \
  -r research/evaluation/figures-requirements.txt
research/.cache/figures-venv/bin/python research/evaluation/crear_figura4_7.py \
  --runs research/evaluation/runs/mi-ejecucion/runs.jsonl \
  --audit research/release/mi-ejecucion/benchmark-audit.json \
  --output-directory research/release/mi-ejecucion/figures
```

El directorio de la figura debe ser nuevo. El generador conserva PNG, SVG, PDF y un registro de procedencia con hashes, versiones y fuente tipográfica. Muestra las 30 observaciones de cada configuración, mediana y rango intercuartílico; los desplazamientos horizontales de los puntos sólo facilitan la lectura. La fuente resuelta se registra y puede variar entre sistemas. Antes de incorporar o publicar las figuras se debe revisar su legibilidad y su correspondencia con las mediciones. Las cajas y puntos no implican observaciones industriales ni tareas equivalentes entre configuraciones.

La publicación académica se identifica mediante commit y etiqueta. Los resúmenes y el manifiesto de todos los archivos de evidencia se conservarán en `research/release/`; las salidas crudas completas se publicarán como archivos de la misma versión. El manifiesto identifica cada miembro, su tamaño y SHA-256 y el archivo que lo contiene. La publicación y sus enlaces se comprobarán antes de declararlos disponibles. Las IRI del modelo son identificadores lógicos: su resolución pública y la disponibilidad FAIR completa no se presumen. El depósito con DOI queda para una etapa posterior. La herramienta semiautomática de conversión desde lenguaje natural y EXPRESS de Fraga, Vegetti y Leone (2017) sigue formando parte de la investigación; esta evaluación no atribuye su ejecución al paquete de pintura.

## Piloto y demostración en la carpeta seleccionada

Después de la fase funcional, los productores auxiliares comparten la selección:

```sh
python3 research/evaluation/capacity_pilot.py --output research/evaluation/runs/mi-ejecucion
python3 research/evaluation/demonstrate.py --output research/evaluation/runs/mi-ejecucion
```

El piloto reserva `capacity/`; la demostración reserva `demonstration/` y `demonstration.json`. Repetir cualquiera de esas fases se rechaza, incluso si dejó archivos parciales. La demostración lee por defecto `functional/integrated/raw-input.owl` dentro de esa misma ejecución; `--case` o `PROTYS_DEMONSTRATION_CASE` permiten elegir un archivo explícito. Requiere además Docker activo y el backend compilado. La variable histórica `PROTYS_DEMONSTRATION_OUTPUT`, si se usa, debe señalar el subdirectorio `demonstration` de la ejecución y no debe contradecir `--output` ni `PROTYS_EVALUATION_OUT`.

Los lectores `verificar_benchmark.py` y `empaquetar_evidencia.py` admiten `--evaluation-dir` o `PROTYS_EVALUATION_OUT`; sin selección explícita conservan el valor histórico `current` para lectura. Los destinos del informe, del manifiesto y de los archivos comprimidos deben ser nuevos y estar fuera de la evidencia leída. Para comprobar la evidencia v1 contra sus fuentes, use el tag v1 y sus binarios correspondientes; las nuevas herramientas no certifican una fuente histórica mediante los archivos modificados de mantenimiento.

Los controles ligeros de conservación se ejecutan con `python3 -m unittest discover -s research/evaluation/tests -v`. Simulan procesos para probar rutas, exclusión de fases y ausencia de truncamientos; no sustituyen una ejecución funcional real ni las 120 réplicas.
