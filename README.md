# PROTYS-WS

**Sistema web para la gestión de la red de ontologías PROTYS(KB)**

## 1. Qué es

PROTYS-WS es el prototipo que pone en operación PROTYS(KB), una red de ontologías para la interoperabilidad semántica entre sistemas de información de manufactura. Permite explorar, consultar y gestionar formalizaciones de subconjuntos de ISO 15531 (ISO/TC 184/SC 4) e ISO 14040 (ISO/TC 207/SC 5), con un piloto separado de ISO 10303 AP242, y ejecutar razonamiento OWL y reglas SWRL. Se conecta en sólo lectura a bases de ERP (probado con ADempiere 3.9.4 y Odoo 17.0) y materializa sus datos con mapeos R2RML, y permite dar de alta, modificar, eliminar y deshacer individuos con validación OWL posterior.

Esta versión (`v1.1.0`) contiene el código, la red, los datos del caso, el protocolo de evaluación, los resultados de la evaluación de `v1.0.0` y la evidencia de esta versión.

### Versiones

- `v1.0.0`: versión evaluada; red, caso, evaluación reproducible y sus resultados.
- `v1.1.0`: conectores ERP de sólo lectura con ADempiere y Odoo, gestión de individuos, módulo opcional de disyunciones, instancias ilustrativas del caso y pruebas complementarias (disyunciones, consultas QC01–QC18, ablación por regla y curva de costo).

### Red de ontologías

La red se organiza en cuatro niveles:

- **Nivel Principal**: Producto, Proceso, Recurso y Empresa.
- **Nivel de Refinamiento**: módulos de producto, proceso, recurso y empresa.
- **Nivel de Estándares**: formalizaciones de ISO 14040 (ACV) e ISO 15531 (MANDATE), más un fragmento local de ISO 10303 AP242 como piloto de extensibilidad.
- **Nivel de Alineamiento**: correspondencias contextuales entre conceptos de cada estándar y 26 mecanismos R01–R26 (22 reglas SWRL sobre individuos nombrados y cuatro patrones SPARQL ASK: R07, R16, R19 y R26). El piloto de ISO 10303 añade R27–R29.

| Archivo | Contenido | Prefijo |
|---|---|---|
| `ontologies/core-concepts.owl` | Nivel Principal | `c:` |
| `ontologies/product-module.owl` | Producto | `prod:` |
| `ontologies/process-module.owl` | Proceso | |
| `ontologies/resource-module.owl` | Recurso | `r:` |
| `ontologies/enterprise-module.owl` | Empresa | |
| `ontologies/iso14040-module.owl` | ISO 14040 | `e:` |
| `ontologies/iso15531-module.owl` | ISO 15531 | `i:` |
| `ontologies/alignment-rules.owl` | Correspondencias contextuales y reglas SWRL R01–R26 | `a:` |
| `research/model/validations/` | Validaciones SPARQL ASK R07, R16, R19 y R26 | |
| `ontologies/paint-case-extension.owl` | Extensión del caso de pintura (ontología derivada) | |
| `ontologies/iso10303-ap242-fragment.owl`, `iso10303-alignment-rules.owl` | Piloto ISO 10303 AP242 y reglas R27–R29 | |
| `ontologies/disjointness-module.ttl` | Módulo opcional: 75 pares de clases de primer nivel disjuntas | |
| `ontologies/paint-case-study-instances.ttl` | Instancias ilustrativas del caso de pintura (planta, celdas, equipos y un registro de mantenimiento), usadas por P6, P7 y la plataforma | |

Todos los prefijos cuelgan de `http://w3id.org/protys/ontology/` (por ejemplo `http://w3id.org/protys/ontology/core#Product`). Las IRI son identificadores lógicos. Las instancias usan `http://w3id.org/protys/instances/` y los datos materializados desde ERP, `http://w3id.org/protys/erp/`.

### Arquitectura

```
┌─────────────────────────────────────────────────────────┐
│                  Interfaz  (React 18 + Tailwind CSS)     │
│  Dashboard │ Explorer │ SPARQL │ Alignment │ ERP │ Wizard│
├─────────────────────────────────────────────────────────┤
│                  API REST  (Spring Boot 3.2)             │
│  OntologyService │ SPARQLService │ AlignmentService      │
│  ReasoningService │ ERPConnectorService                  │
│  StandardIncorporationService │ FusekiService            │
├───────────────────────┬─────────────────────────────────┤
│  Apache Jena 4.10     │        PostgreSQL 15             │
│  Fuseki + TDB2        │        Metadatos JPA             │
├───────────────────────┴─────────────────────────────────┤
│              PROTYS(KB): red de ontologías               │
└─────────────────────────────────────────────────────────┘
```

| Carpeta | Contenido |
|---|---|
| `backend/` | API REST en Spring Boot 3.2 con Jena 4.10 |
| `frontend/` | Interfaz en React 18 y Tailwind CSS 3.4 |
| `ontologies/` | PROTYS(KB) en OWL 2 y Turtle |
| `docker/` | Composición de Fuseki, PostgreSQL, API e interfaz |
| `research/` | Modelo, datos, ejecutores y evaluación reproducible; ver [research/README.md](research/README.md) |
| `scripts/` | Conservación de resultados, comparación de ejecuciones, exportación y consulta del grafo materializado, y arranque y parada de una demostración en Docker |
| `sql/` | Esquemas ERP de ejemplo |
| `docker/integration/` | ADempiere y Odoo de prueba y los mapeos R2RML de cada uno |

### Caso de estudio

Datos sintéticos de un caso simulado de fabricación de pintura base agua:

- Siete etapas: recepción e inspección, dispersión, mezcla, ajuste de viscosidad, filtrado, control de calidad y envasado de lotes aprobados.
- Una ficha del molino `PearlMill_01`, con cámara de 50 L y potencia de 30 kW; las cargas mayores se representan mediante subcargas secuenciales.
- Inventario de emisiones de la electricidad consumida por las operaciones, con factores declarados como supuestos, en kg CO₂e/L.
- Casos funcionales pequeños con respuestas esperadas y datos independientes para medir el costo computacional.

### Preguntas de competencia

| Pregunta | Alcance comprobable en el caso | Consulta |
|---|---|---|
| P1 | Recursos necesarios para un proceso en las etapas formalizadas | Q04 |
| P2 | Recursos de un plan de proceso y su uso registrado | Q02 y Q16 |
| P3 | Productos que usan un recurso | Q02 |
| P4 | Actividades que componen un plan de proceso | Q18 |
| P5 | Producto generado por un plan de proceso | Q02 |

El caso formaliza la fase de producción.

## 2. Autoría, institución y licencia

Desarrollado como parte de la tesis doctoral *Modelo de Interoperabilidad Semántica entre Sistemas de Información de Ciclo de Vida de Productos para Empresas de Manufactura*.

Alvaro Luis Fraga. Universidad Tecnológica Nacional, Facultad Regional Santa Fe.

Dirección de la tesis: Dra. Marcela Vegetti y Dr. Horacio Leone.

Agradecimientos: al INGAR, Instituto de Desarrollo y Diseño (CONICET), y a la Universidad Tecnológica Nacional, Facultad Regional Santa Fe.

Distribuido bajo licencia MIT; ver [LICENSE](LICENSE). Los datos de cita (autor, afiliación, licencia y versión) están en [CITATION.cff](CITATION.cff).

La investigación incluye además una herramienta de generación semiautomática de ontologías desde documentación de estándares en lenguaje natural y fragmentos EXPRESS, descrita en [SAOA 2017, páginas 53–66](https://clei.org/clei2017/sites/default/files/Mem/SAOA/SAOA-05.pdf).

## 3. Requisitos

| Uso | Requisito |
|---|---|
| Evaluación | JDK 17 (la ejecución publicada usó Temurin 17.0.20.1), Python 3.9 o posterior, Docker (para la demostración REST) |
| Figura de resultados | Python 3.12 con `research/evaluation/figures-requirements.txt` |
| Desarrollo de la API | JDK 17; Maven se obtiene con `backend/mvnw` |
| Interfaz | Node.js 24.18.0 y npm 11.16.0 (fijados en `frontend/package.json` y `frontend/.nvmrc`) |
| Plataforma en contenedores | Docker y Docker Compose |

## 4. Instalación y arranque

### Desarrollo local

```bash
# API en http://localhost:8080/api, Swagger UI en http://localhost:8080/swagger-ui.html
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Interfaz en http://localhost:3000 (en otra terminal)
cd frontend
npm ci
npm start
```

### Plataforma con Docker Compose

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

Interfaz en http://127.0.0.1:8081, API en http://127.0.0.1:8080/api y Fuseki en http://127.0.0.1:3030. El servicio
`protys-init` carga la red integrada en Fuseki al arrancar. `docker/.env.example` tiene los valores de demostración
de las variables (puertos y credenciales de los contenedores).

La imagen del backend no incluye los procesos de la evaluación académica; la evaluación se ejecuta con el punto de entrada de la sección 5.

### Endpoints principales

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/dashboard` | Estadísticas generales |
| `GET` | `/api/ontology/modules` | Módulos cargados |
| `POST` | `/api/ontology/modules/upload` | Carga de un módulo OWL |
| `POST` | `/api/sparql/execute` | Ejecución de una consulta SPARQL |
| `GET` | `/api/sparql/templates/competency` | Plantillas de las preguntas P1–P5 |
| `GET` | `/api/alignment/rules` | Reglas SWRL |
| `POST` | `/api/alignment/reasoning/execute` | Razonamiento |
| `POST` | `/api/erp/connectors` | Registro de un conector ERP |
| `POST` | `/api/erp/connectors/{id}/materialize` | Materialización de un mapeo R2RML |
| `GET`, `POST`, `PUT`, `DELETE` | `/api/ontology/individuals` | Individuos de una clase (`classUri`) o uno por su IRI (`uri`) |
| `POST` | `/api/ontology/individuals/edits/{id}/undo` | Deshacer una edición |
| `POST` | `/api/standards` | Incorporación de un estándar |
| `POST` | `/api/wizard/step1/upload` | Asistente de incorporación, paso 1 |

## 5. Cómo reproducir la evaluación

La validación y clasificación usan OWL API 5.1.20 con HermiT 1.4.5.519; las reglas SWRL corren en un proceso separado con SWRLAPI 2.1.3, Drools 7.74.1.Final y OWL API 4.5.27. El protocolo completo y las configuraciones están en [research/README.md](research/README.md). Los resultados publicados en `research/release/` son los de `v1.0.0`; `research/integration/diferencias-versiones/` mide el efecto de los cambios de `ontologies/` de esta versión (las 21 consultas siguen coincidiendo con las esperadas).

Desde la raíz, con `JAVA_HOME` en un JDK 17. `E` es la carpeta de la ejecución, donde quedan las salidas crudas, y `A` la de la auditoría, la figura, el grafo materializado, el manifiesto y los archivos comprimidos. Ninguna de las dos se versiona.

```bash
E=research/evaluation/runs/mi-ejecucion
A=research/evaluation/auditorias/mi-ejecucion

# 0. JAR de la API (lo usa la demostración REST)
./backend/mvnw -q -DskipTests -f backend/pom.xml package

# 1. Comprobaciones funcionales: 243 aserciones y 21 consultas en cinco configuraciones,
#    y comprobación de sintaxis de los ejemplos, consultas y validaciones de research/model/
./research/reproduce.sh --functional --output $E
python3 research/evaluation/comprobar_sintaxis.py --salida $E

# 2. Demostración REST sobre la API (contenedor PostgreSQL y Fuseki propios, que se eliminan al terminar)
python3 research/evaluation/demonstrate.py --output $E

# 3. Piloto de capacidad (100 lotes)
python3 research/evaluation/capacity_pilot.py --output $E

# 4. Rendimiento: 30 réplicas por configuración, 120 en total
./research/reproduce.sh --benchmark --output $E

# 5. Auditoría independiente de las 120 réplicas
python3 research/evaluation/verificar_benchmark.py --repo . --evaluation-dir $E --output $A/benchmark-audit.json

# 6. Figura de resultados
python3.12 -m venv research/.cache/figures-venv
research/.cache/figures-venv/bin/python -m pip install -r research/evaluation/figures-requirements.txt
research/.cache/figures-venv/bin/python research/evaluation/crear_figura4_7.py \
  --runs $E/runs.jsonl --audit $A/benchmark-audit.json --output-directory $A/figures

# 7. Grafo materializado de la configuración integrada en Turtle y RDF/XML
python3 scripts/exportar_grafo_materializado.py --ejecucion $E --destino $A/grafo-materializado

# 8. Manifiesto de toda la evidencia y archivos comprimidos
python3 research/evaluation/empaquetar_evidencia.py --repo . --evaluation-dir $E \
  --audit $A/benchmark-audit.json --assets $A/archivos \
  --manifest $A/evidence-manifest.json --tag mi-ejecucion
```

Entre la ejecución y la auditoría no deben cambiar `ontologies/`, `research/model/`, `research/data/`, `research/runtime/` ni los guiones de `research/evaluation/`: el verificador compara sus hashes. `scripts/conservar_resultados.py --ejecucion $E` copia los resúmenes a `research/release/`, que tiene los de `v1.0.0`.

Para comparar una ejecución nueva con la publicada:

```bash
python3 scripts/comparar_ejecuciones.py research/release $E \
  --census-referencia research/model/census.json --census-candidata research/model/census.json
```

Con esta versión, la comparación muestra las rutas de la carpeta nueva y los cambios que mide `research/integration/diferencias-versiones/` (un triple más de entrada en la configuración integrada y sus consecuencias).

## 6. Dónde está cada resultado

| Resultado | Archivo | Campo |
|---|---|---|
| Clases, propiedades de objeto y de datos (base de ocho módulos y unión completa) | `research/model/census.json` | `base_eight_unique_counts`, `complete_explicit_union_counts` |
| 243 comprobaciones funcionales | `research/release/functional.json` | `assertions` |
| 21 consultas en cinco configuraciones (105 comparaciones) | `research/release/functional.json` | `golden_checks` |
| Triples de entrada y materializados por configuración | `research/release/functional.json`, `research/release/runs.jsonl` | `configurations[].result`, `input_triples`, `materialized_triples` |
| Tiempo y memoria de cada una de las 120 réplicas | `research/release/runs.jsonl` | `total_ms`, `observed_peak_process_tree_rss_bytes` |
| Estadísticas por configuración | `research/release/summary.json`, `summary.csv` | `descriptive_statistics` |
| Auditoría de las 120 réplicas | `research/release/benchmark-audit.json` | |
| Demostración REST (controles e intercambios HTTP) | `research/release/demonstration.json` | `checks`, `http_exchanges` |
| Piloto de capacidad | `research/release/capacity.json` | `pilots` |
| Figura de resultados | `research/release/figures/` | PNG, SVG, PDF y procedencia |
| Grafo materializado de la configuración integrada (5384 triples) y las 21 consultas corridas sobre él con `arq` | `research/release/grafo-materializado/` | |
| Entorno de la ejecución | `research/release/environment.json` | |
| Manifiesto de toda la evidencia cruda | `research/release/evidence-manifest.json` | `files`, `archives` |
| Comparación con la ejecución del 3-10-2026 (resultados funcionales, tiempo y memoria) | `research/release/comparacion/` | |

El resumen de los resultados está en [research/release/README.md](research/release/README.md).

Evidencia de esta versión:

| Resultado | Archivo | Campo |
|---|---|---|
| 76 pares de disyunciones propuestos; el único que vuelve inconsistente la red | `research/complementarias/disyunciones/salidas/pares/resultado.json`, `pares.csv` | |
| Módulo de 75 pares con el caso y con las instancias; 87 clases y 119 propiedades con y sin él | `research/complementarias/disyunciones/salidas/modulo/resultado.json` | `corridas`, `censo_ocho_modulos_base` |
| QC01–QC18, obtenidas y esperadas | `research/complementarias/consultas-qc/salidas/tabla.md`, `resumen.json` | `tabla` |
| Consultas que cambian al quitar cada regla | `research/complementarias/ablacion-reglas/salidas/ablacion-regla-x-consulta.csv`, `resumen.json` | `consultas_por_regla`, `reglas_sin_efecto_en_ninguna_consulta` |
| Tiempo y memoria con 20, 50, 100 y 200 lotes | `research/complementarias/curva-costo/salidas/tabla.md`, `resumen.json` | |
| Plataforma con las instancias: 4804 triples de entrada, 1447 inferidos, P6, P7 y Q20 | `research/integration/consultas-p6-p7/salidas/04-razonamiento-con-instancias.json`, `05-p6p7-razonadas.txt`, `06-catalogo-con-y-sin-instancias.txt` | `inputTripleCount`, `inferredTripleCount` |
| Conectores ERP con ADempiere y Odoo | `research/integration/erp/salidas/e2e/RESUMEN.txt` | |
| Gestión de individuos | `research/integration/gestion-individuos/salidas/` | |
| Diferencias con `v1.0.0` | `research/integration/diferencias-versiones/salidas/` | |
| Suites de pruebas | `research/integration/suites/` | |

## 7. Pruebas

```bash
cd backend && ./mvnw test                                 # API: 288 pruebas, 17 salteadas (las de sólo lectura con PostgreSQL)
cd frontend && npm ci && npm test -- --watchAll=false     # interfaz: 88 pruebas en 16 suites
python3 -m unittest discover -s scripts/tests             # guiones de scripts/: 29 pruebas
python3 -m unittest discover -s research/evaluation/tests # evaluación: 16 pruebas
sh research/integration/erp-solo-lectura/solo-lectura.sh  # las 17 de sólo lectura, con un PostgreSQL desechable
```

`sh research/integration/suites/correr.sh` corre todas y guarda las salidas en `research/integration/suites/`.

## 8. Alcance

- La API no tiene autenticación y publica sus puertos en 127.0.0.1.
- Los datos del caso son sintéticos; las mediciones de rendimiento se tomaron sobre ellos.

## 9. Cómo citar

Fraga, A. L. (2026). *PROTYS-WS y PROTYS(KB)* (v1.1.0) [Software]. Universidad Tecnológica Nacional, Facultad Regional Santa Fe. Los mismos datos, en formato legible por máquina, están en [CITATION.cff](CITATION.cff).
