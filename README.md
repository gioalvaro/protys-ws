# PROTYS-WS

**Sistema web para la gestión de la red de ontologías PROTYS(KB)**

## 1. Qué es

PROTYS-WS es el prototipo que pone en operación PROTYS(KB), una red de ontologías para la interoperabilidad semántica entre sistemas de información de manufactura. Permite explorar, consultar y gestionar formalizaciones de subconjuntos de ISO 15531 (ISO/TC 184/SC 4) e ISO 14040 (ISO/TC 207/SC 5), con un piloto separado de ISO 10303 AP242, y ejecutar razonamiento OWL y reglas SWRL. Incluye conectores y ejemplos de mapeos R2RML para ERP; esta versión no acredita una integración con ERP industriales.

Esta versión (`v1.0.0`) es la versión evaluada, con la evaluación reproducible de la red y del caso. Contiene el código, la red, los datos del caso, el protocolo de evaluación y sus resultados.

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
| `scripts/` | Normalización de metadatos, conservación de resultados y comparación de ejecuciones |
| `sql/` | Esquemas ERP de ejemplo |

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
| Interfaz | Node.js 20 |
| Plataforma en contenedores | Docker y Docker Compose |

## 4. Instalación y arranque

### Desarrollo local

```bash
# API en http://localhost:8080/api, Swagger UI en http://localhost:8080/swagger-ui.html
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Interfaz en http://localhost:3000 (en otra terminal)
cd frontend
npm install
npm start
```

### Plataforma con Docker Compose

```bash
cd docker
cp .env.example .env      # valores de demostración; cambiarlos fuera de un entorno local
docker compose up -d
./init-fuseki.sh          # carga las ontologías en Fuseki
```

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
| `POST` | `/api/standards` | Incorporación de un estándar |
| `POST` | `/api/wizard/step1/upload` | Asistente de incorporación, paso 1 |

## 5. Cómo reproducir la evaluación

La validación y clasificación usan OWL API 5.1.20 con HermiT 1.4.5.519; las reglas SWRL corren en un proceso separado con SWRLAPI 2.1.3, Drools 7.74.1.Final y OWL API 4.5.27. El protocolo completo, las configuraciones y los límites están en [research/README.md](research/README.md).

Desde la raíz, con `JAVA_HOME` en un JDK 17:

```bash
# 0. JAR de la API (lo usa la demostración REST)
./backend/mvnw -q -DskipTests -f backend/pom.xml package

# 1. Comprobaciones funcionales: 243 aserciones y 21 consultas en cinco configuraciones,
#    y comprobación de sintaxis de los ejemplos, consultas y validaciones de research/model/
./research/reproduce.sh --functional
python3 research/evaluation/comprobar_sintaxis.py

# 2. Demostración REST sobre la API (contenedor PostgreSQL y Fuseki propios, que se eliminan al terminar)
python3 research/evaluation/demonstrate.py

# 3. Piloto de capacidad (100 lotes)
python3 research/evaluation/capacity_pilot.py

# 4. Rendimiento: 30 réplicas por configuración, 120 en total
./research/reproduce.sh --benchmark

# 5. Auditoría independiente de las 120 réplicas
python3 research/evaluation/verificar_benchmark.py --repo . --output research/release/benchmark-audit.json

# 6. Figura de resultados
python3.12 -m venv research/.cache/figures-venv
research/.cache/figures-venv/bin/python -m pip install -r research/evaluation/figures-requirements.txt
research/.cache/figures-venv/bin/python research/evaluation/crear_figura4_7.py \
  --runs research/evaluation/current/runs.jsonl \
  --audit research/release/benchmark-audit.json \
  --output-directory research/release/figures

# 7. Copia de los resúmenes a research/release/ con registro de copias idénticas,
#    y grafo materializado de la configuración integrada en Turtle y RDF/XML
python3 scripts/conservar_resultados.py
python3 scripts/exportar_grafo_materializado.py

# 8. Manifiesto de toda la evidencia y archivos comprimidos para adjuntar a la publicación
python3 research/evaluation/empaquetar_evidencia.py --repo . \
  --audit research/release/benchmark-audit.json \
  --assets ../protys-evidence/v1.0.0 \
  --manifest research/release/evidence-manifest.json --tag v1.0.0
```

Las salidas crudas quedan en `research/evaluation/current/`, que no se versiona. Entre la ejecución y la auditoría no deben cambiar `ontologies/`, `research/model/`, `research/data/`, `research/runtime/` ni los guiones de `research/evaluation/`: el verificador compara sus hashes.

Para comparar una ejecución nueva con la publicada:

```bash
python3 scripts/comparar_ejecuciones.py research/release research/evaluation/current \
  --census-referencia research/model/census.json --census-candidata research/model/census.json
```

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

## 7. Pruebas

```bash
cd backend && ./mvnw test                     # API: 48 pruebas
cd frontend && npm test -- --watchAll=false   # interfaz
python3 -m unittest discover -s scripts/tests # guiones de scripts/: 10 pruebas
```

## 8. Alcance

- La API no tiene autenticación.
- Los conectores ERP son demostradores.
- Las respuestas y las mediciones de rendimiento corresponden a los datos y conceptos formalizados de un caso simulado.
- Las dependencias de la interfaz se declaran por rango, sin archivo de bloqueo.

## 9. Cómo citar

Fraga, A. L. (2026). *PROTYS-WS y PROTYS(KB): versión evaluada* (v1.0.0) [Software]. Universidad Tecnológica Nacional, Facultad Regional Santa Fe. Los mismos datos, en formato legible por máquina, están en [CITATION.cff](CITATION.cff).
