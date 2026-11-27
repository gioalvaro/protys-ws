# research/

Modelo, datos, ejecutores y evidencia de PROTYS(KB) sobre un caso simulado de fabricación de pintura base agua.

| Carpeta o archivo | Contenido |
|---|---|
| `catalog.json` | Fuentes, las cinco configuraciones (ISO 15531, ISO 14040, unión, modelo integrado y modelo integrado con el piloto ISO 10303), las 21 consultas, las 4 validaciones ASK y las preguntas de competencia |
| `reproduce.sh` | Punto de entrada de la evaluación |
| `model/` | Perfiles de investigación, consultas (`queries/`), validaciones (`validations/`), respuestas esperadas (`expected/`), contratos, censo de clases y propiedades (`census.json`), mecanismos (`mechanisms.json`), ejemplos y extractos (`examples/`) y la comprobación de que los extractos están en sus módulos (`comprobar_extractos.py`) |
| `data/` | Generador del escenario (semilla 42), datos funcionales, cargas de rendimiento y casos de prueba (`fixtures/`) |
| `runtime/` | Los dos procesos Java de la evaluación: validador (OWL API 5.1.20, HermiT 1.4.5.519, Jena 4.10.0) y trabajador de reglas (SWRLAPI 2.1.3, Drools 7.74.1.Final, OWL API 4.5.27) |
| `evaluation/` | Guiones de la evaluación, de la auditoría, del empaquetado, de la figura y de la comprobación de sintaxis; sondas de compatibilidad de HermiT (`version-compatibility/`) |
| `release/` | Resultados de la evaluación de la versión `v1.0.0` |
| `complementarias/` | Disyunciones, ablación por regla, consultas QC01–QC18 y curva de costo |
| `integration/` | Plataforma: conectores ERP, asistente, explorador, gestión de individuos, P6 y P7, capturas, diferencias entre versiones y suites de pruebas |
| `quality/oops/` | Respuestas del servicio OOPS! sobre los módulos |
| `literature/busqueda-acv/` | Registro de la búsqueda bibliográfica sobre ontologías y análisis de ciclo de vida |

## Evaluación

Requiere un JDK 17, Python 3.9 o posterior y, la primera vez, acceso a los repositorios Maven. Desde la raíz, con
`JAVA_HOME` en el JDK 17:

```sh
./research/reproduce.sh --prepare-only                                  # compila los dos procesos
./research/reproduce.sh --functional --output research/evaluation/runs/NOMBRE
./research/reproduce.sh --benchmark  --output research/evaluation/runs/NOMBRE
```

- **Fase funcional:** las 243 aserciones de `data/fixtures/tests.json` y las 21 consultas en cada configuración,
  comparadas con `model/expected/` como multiconjunto de filas con tolerancia numérica relativa de 1e-9.
- **Fase de rendimiento:** 100 lotes simulados, 30 réplicas por configuración (ISO 15531, ISO 14040, unión y
  modelo integrado), 120 en total, en orden aleatorio con la semilla 20261003. Cada réplica es un proceso Java nuevo
  con `-Xmx2g` para el proceso principal y para el trabajador; la memoria es el máximo observado del RSS de los dos,
  muestreado cada 100 ms. Exige un informe funcional `PASS` en la misma carpeta y los mismos hashes de entradas y
  binarios.
- **Salidas:** en la carpeta de `--output` (`functional.json`, `runs.jsonl`, `summary.json`, `summary.csv`,
  `environment.json` y una carpeta por réplica).

Después, en la misma carpeta:

```sh
python3 research/evaluation/capacity_pilot.py --output research/evaluation/runs/NOMBRE
python3 research/evaluation/demonstrate.py   --output research/evaluation/runs/NOMBRE
python3 research/evaluation/comprobar_sintaxis.py --salida research/evaluation/runs/NOMBRE
python3 research/evaluation/verificar_benchmark.py --repo . --evaluation-dir research/evaluation/runs/NOMBRE \
  --output research/release/NOMBRE/benchmark-audit.json
python3 research/evaluation/empaquetar_evidencia.py --repo . --evaluation-dir research/evaluation/runs/NOMBRE \
  --audit research/release/NOMBRE/benchmark-audit.json --assets ../protys-evidence/NOMBRE \
  --manifest research/release/NOMBRE/evidence-manifest.json --tag NOMBRE
```

- `capacity_pilot.py`: piloto de capacidad con 100 lotes completos.
- `demonstrate.py`: la misma red por la API de PROTYS-WS, con un PostgreSQL y un Fuseki propios que se eliminan al
  terminar.
- `comprobar_sintaxis.py`: analiza la sintaxis de los ejemplos, las consultas y las validaciones de `model/`.
- `verificar_benchmark.py`: reconstruye solicitudes, orden de las réplicas, estadísticas y hashes a partir de las
  salidas, sin volver a ejecutar el modelo.
- `empaquetar_evidencia.py`: manifiesto de todos los archivos de la carpeta y archivos comprimidos para adjuntar.
- La figura de resultados la genera `crear_figura4_7.py` con Python 3.12 y `evaluation/figures-requirements.txt`.

`scripts/conservar_resultados.py`, `scripts/comparar_ejecuciones.py` y `scripts/exportar_grafo_materializado.py`
copian los resúmenes a `release/`, comparan dos ejecuciones y exportan el grafo materializado de la configuración
integrada. Los pasos completos, en orden, están en la sección 5 del README de la raíz.

`evaluation/version-compatibility/ejecutar_sondas.py` corre el validador con HermiT 1.4.5.456 y 1.4.5.519 sobre
una ontología válida y una inconsistente (`--hermit-456` indica el JAR de 1.4.5.456). Con 1.4.5.456 y OWL API
5.1.20 la validación falla con `NoSuchMethodError`; con 1.4.5.519 la válida es consistente y la inconsistente se
detecta. Resultado en `evaluation/version-compatibility/compatibility.json`.

## Resultados de la versión evaluada

`release/` tiene los resultados de la evaluación de `v1.0.0`; su README los resume. En esta versión cambian tres
módulos de `ontologies/` (un axioma lógico, una importación y anotaciones) y se agregan el módulo opcional de
disyunciones y las instancias ilustrativas del caso: `integration/diferencias-versiones/` mide el efecto sobre la
configuración integrada (las 21 consultas siguen coincidiendo con las esperadas).

## Grafo materializado

`python3 scripts/exportar_grafo_materializado.py` deja en `release/grafo-materializado/` el grafo sobre el que
corrieron las 21 consultas en la configuración integrada del conjunto funcional (4325 triples de entrada, 5384
materializados), en Turtle y en RDF/XML: un único grafo por defecto, sin grafos con nombre ni preparación adicional.
En Protégé se abre con File › Open sobre el `.rdf` o el `.ttl`; ya contiene las inferencias.
Con Apache Jena: `arq --data research/release/grafo-materializado/integrado-funcional-materializado.ttl --query
research/model/queries/Q01.sparql`; `python3 scripts/consultar_grafo_exportado.py --arq RUTA/bin/arq` corre las 21 y
las compara con `model/expected/`.

## Pruebas complementarias

`sh research/complementarias/correr.sh [disyunciones] [consultas-qc] [ablacion] [curva]`, con el ejecutor académico
y sin la plataforma. Cada carpeta tiene su README.

| Carpeta | Qué muestra |
|---|---|
| [`complementarias/disyunciones/`](complementarias/disyunciones/README.md) | 76 pares de clases de primer nivel propuestos como disjuntos: sólo `core:Product ⊥ core:Resource` vuelve inconsistente la red; el módulo opcional de 75 pares es consistente con el caso y con las instancias |
| [`complementarias/consultas-qc/`](complementarias/consultas-qc/README.md) | QC01–QC18, una consulta por cada regla que el catálogo no ejercita, con respuestas esperadas calculadas sin razonador |
| [`complementarias/ablacion-reglas/`](complementarias/ablacion-reglas/README.md) | Qué consultas cambian al quitar cada regla |
| [`complementarias/curva-costo/`](complementarias/curva-costo/README.md) | Tiempo y memoria con 20, 50, 100 y 200 lotes, red integrada y unión |

## Plataforma

Cada carpeta de `integration/` tiene un `correr.sh` y un README. Los guiones levantan la plataforma en proyectos de
Compose propios (`protys-v110` y `protys-v110-erp`) y los bajan al terminar.

| Carpeta | Qué muestra |
|---|---|
| [`integration/erp/`](integration/erp/README.md) | Conexión en sólo lectura con ADempiere 3.9.4 y Odoo 17.0, materialización R2RML, razonamiento y P1–P5 |
| [`integration/erp-solo-lectura/`](integration/erp-solo-lectura/README.md) | La conexión del conector no puede escribir, contra un PostgreSQL desechable |
| [`integration/asistente/`](integration/asistente/README.md) | El asistente incorpora el piloto ISO 10303 por la API con el mismo resultado que la configuración `h3_extended` |
| [`integration/explorador/`](integration/explorador/README.md) | El explorador devuelve la jerarquía completa del catálogo integrado (128 clases) |
| [`integration/gestion-individuos/`](integration/gestion-individuos/README.md) | Alta, lectura, modificación, baja y deshacer de individuos por la API y por la interfaz |
| [`integration/consultas-p6-p7/`](integration/consultas-p6-p7/README.md) | P6 y P7 con las instancias del caso: entrada 4804, inferidos 1447 |
| [`integration/diferencias-versiones/`](integration/diferencias-versiones/README.md) | Diferencias entre `ontologies/` de `v1.0.0` y de esta versión |
| [`integration/capturas-interfaz/`](integration/capturas-interfaz/README.md) | Capturas de la interfaz: red integrada, asistente y conectores ERP |
| [`integration/suites/`](integration/suites/README.md) | Salidas de las suites de pruebas, de la comprobación de sintaxis y de la de extractos |

## Calidad y literatura

- `quality/oops/`: respuestas de OOPS! sobre los conjuntos de módulos; su README describe cómo se obtuvieron.
- `literature/busqueda-acv/`: consultas, registros y clasificación de la búsqueda en OpenAlex; su `LEEME.md`
  describe el procedimiento.
