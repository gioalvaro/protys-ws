# PROTYS-WS

**Sistema web para la gestión de la red de ontologías PROTYS(KB)**

PROTYS-WS es el sistema prototipo que operacionaliza PROTYS(KB), una red de ontologías diseñada para abordar la interoperabilidad semántica entre sistemas de información heterogéneos en la industria manufacturera. Permite explorar, consultar y gestionar formalizaciones de subconjuntos de ISO 15531 (ISO/TC 184/SC 4) e ISO 14040 (ISO/TC 207/SC 5), con un piloto separado de ISO 10303, y ejecutar razonamiento semántico mediante reglas SWRL. Incluye conectores ERP con mapeo R2RML: en noviembre de 2026 se probaron en sólo lectura contra ADempiere 3.9.4 y Odoo 17 con sus datos de demostración ([informe](research/integration/erp/INFORME.md)); eso no equivale a una integración industrial en producción.

Para revisar la evidencia de la tesis, comenzar por [research/release/README.md](research/release/README.md) y la [publicación `tesis-cierre-2026-10-03-v1`](https://github.com/gioalvaro/protys-ws/releases/tag/tesis-cierre-2026-10-03-v1). Esa etiqueta conserva el código y las 120 ejecuciones citadas en el manuscrito. Los ajustes posteriores de instalación y conservación de salidas se registran por separado; no sustituyen esos resultados ni resuelven la decisión pendiente C033/IC62.

El trabajo técnico posterior (noviembre de 2026, rama `trabajo-tecnico-2026-11`) tiene su evidencia en `research/integration/`. No reemplaza los resultados de esa etiqueta:

| Evidencia | Qué muestra |
|---|---|
| [`erp/INFORME.md`](research/integration/erp/INFORME.md) | Conexión en sólo lectura con ADempiere y Odoo, materialización R2RML, razonamiento y P1–P5 con datos de los dos ERP |
| [`explorador/README.md`](research/integration/explorador/README.md) | El explorador de clases abre el catálogo integrado completo (128 clases) |
| [`revision-2026-11-18/README.md`](research/integration/revision-2026-11-18/README.md) | Correcciones de la revisión externa de la rama, con la salida que respalda cada una |

Las 128 clases nombradas del catálogo integrado son las 87 de los ocho módulos de `ontologies/` (core, product, process, resource, enterprise, ISO 15531, ISO 14040 y alignment), más 30 de la extensión del caso de pintura y 11 de los tres perfiles de investigación (`research/model/*-profile.owl`). Cálculo en [`clases-catalogo.txt`](research/integration/revision-2026-11-18/salidas/clases-catalogo.txt).
| [`asistente/README.md`](research/integration/asistente/README.md) | El asistente incorpora el piloto ISO 10303 (fragmento AP242, datos y reglas R27–R29) por API e interfaz, con el mismo resultado que la configuración `h3_extended`; la plataforma se reinicia conservando lo incorporado |

Desarrollado como parte de la tesis doctoral:

> **"Modelo de Interoperabilidad Semántica entre Sistemas de Información de Ciclo de Vida de Productos para Empresas de Manufactura"**
>
> Alvaro Luis Fraga — UTN, Facultad Regional Santa Fe
>
> Directora vigente: Dra. María Marcela Vegetti (INGAR CONICET-UTN)
> 
> Dirección en etapas previas: Dr. Horacio Leone (INGAR CONICET-UTN)

## Arquitectura

La investigación incluye además una herramienta de generación semiautomática de ontologías desde documentación de estándares en lenguaje natural y fragmentos EXPRESS. El prototipo de Fraga, Vegetti y Leone (2017) utiliza UIMA Ruta para anotación y una biblioteca de transformación a OWL; la selección de términos, revisión y alineamientos incluyen intervención humana. Se conserva como recurso de la metodología de incorporación de ontologías. Su descripción y ejemplos están publicados en [SAOA 2017, páginas 53–66](https://clei.org/clei2017/sites/default/files/Mem/SAOA/SAOA-05.pdf). El paquete de evaluación de pintura de este repositorio verifica las ontologías, reglas y consultas que contiene; la presencia de la referencia no implica que ese paquete ejecute el conversor de 2017.

```
┌─────────────────────────────────────────────────────────┐
│                  Frontend  (React 18 + Tailwind CSS)     │
│  Dashboard │ Explorer │ SPARQL │ Alignment │ ERP │ Wizard│
├─────────────────────────────────────────────────────────┤
│                  REST API  (Spring Boot 3.2)             │
│  OntologyService │ SPARQLService │ AlignmentService      │
│  ReasoningService │ ERPConnectorService                  │
│  StandardIncorporationService │ FusekiService            │
├───────────────────────┬─────────────────────────────────┤
│  Apache Jena 4.10     │        PostgreSQL 15             │
│  Fuseki + TDB2        │        Metadatos JPA             │
├───────────────────────┴─────────────────────────────────┤
│              PROTYS(KB) — Red de Ontologías               │
│  Core │ Product │ Process │ Resource │ Enterprise         │
│  ISO 14040 │ ISO 15531 │ 22 SWRL + 4 ASK │ Instancias     │
└─────────────────────────────────────────────────────────┘
```

## Estructura del proyecto

```
protys-ws/
├── backend/                 # Spring Boot 3.2 + Jena 4.10
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/org/protys/ws/
│       ├── config/          # Jena, CORS, Security, DataInitializer
│       ├── controller/      # 7 controladores REST (42 endpoints)
│       ├── service/         # Ontologías, SPARQL, alineamiento, razonamiento, ERP (R2RML) y asistente
│       ├── model/           # Entidades JPA
│       ├── dto/             # Objetos de transferencia
│       ├── repository/      # Repositorios JPA
│       └── exception/       # Manejo global de errores
│
├── frontend/                # React 18 + Tailwind CSS 3.4
│   ├── package.json
│   ├── Dockerfile
│   └── src/
│       ├── components/      # Dashboard, Explorer, SPARQL, Alignment, ERP, Wizard
│       ├── hooks/           # Custom hooks (useDashboard, useModules, useSparql...)
│       └── services/api.js  # Cliente Axios
│
├── ontologies/              # PROTYS(KB) en OWL 2 / Turtle
│   ├── core-concepts.owl    # Nivel Principal: Product, Process, Resource, Enterprise (§3.3.1)
│   ├── product-module.owl   # ProductMod: Thing, Substance, Production_Item... (Fig. 3.8)
│   ├── process-module.owl   # ProcessMod: Natural/Artificial_Process, Process_Plan... (Fig. 3.7)
│   ├── resource-module.owl  # ResourceMod: Tool, Equip, Device, Material, Person, File... (Figs. 3.9–3.12)
│   ├── enterprise-module.owl # EnterpriseMod: Station, Cell, Shop, Factory, Location (Fig. 3.13)
│   ├── iso14040-module.owl  # ISO 14040 LCA, 15 clases (Fragmento de código 3.1)
│   ├── iso15531-module.owl  # ISO 15531 MANDATE, 18 clases (Fragmento de código 3.2)
│   ├── alignment-rules.owl  # Correspondencias del caso y 22 reglas SWRL de R01–R26
│   ├── alignment-queries.sparql      # Patrones de validación SPARQL ASK (R07, R16, R19, R26)
│   ├── paint-case-extension.owl      # Extensión del caso de estudio (ontología derivada, §4.4.1)
│   ├── iso10303-ap242-fragment.owl   # Fragmento local derivado de AP242 (§5.4.3)
│   ├── iso10303-alignment-rules.owl  # Reglas SWRL R27–R29
│   └── paint-*.ttl          # Instancias del caso de estudio
│
├── docker/                  # Docker Compose (4 servicios persistentes y un inicializador)
│   ├── docker-compose.yml
│   ├── load_canonical.py    # Carga inicial por la API; al reiniciar conserva lo registrado después
│   ├── integration/         # ADempiere y Odoo de demostración y mapeos R2RML (Fase 1)
│   ├── .env.example         # Credenciales (copiar a .env)
│   ├── fuseki-config.ttl
│   └── init-fuseki.sh
│
├── research/                # Modelo, datos, pruebas y evaluación reproducible
│   ├── README.md            # Protocolo, alcance y lectura de las salidas
│   ├── reproduce.sh         # Preparación y ejecución desde la raíz
│   ├── catalog.json         # Fuentes y configuraciones canónicas
│   ├── model/               # Perfiles, consultas y respuestas esperadas
│   ├── data/                # Casos funcionales y cargas de rendimiento
│   ├── runtime/             # Validación OWL y ejecución SWRL en procesos separados
│   └── evaluation/          # Evidencia de ejecución y mediciones
│
├── scripts/demo.py          # Arranque aislado, comprobaciones y parada de la aplicación
├── .github/workflows/       # Comprobaciones funcionales y compilación
└── sql/                     # Esquemas ERP ilustrativos que requieren adaptación
    ├── adempiere-schema.sql
    └── odoo-schema.sql
```

## Requisitos

Para la demostración completa en contenedores: Docker con Compose y Python 3.9 o posterior. Java, Node, PostgreSQL y Fuseki se preparan dentro de los contenedores; no se utilizan bases o almacenes existentes.

Para trabajar sin contenedores se necesitan Java 17, Python 3.9 o posterior y Node.js 24.18.0 con npm 11.16.0. El envoltorio `backend/mvnw` prepara Maven. Las dependencias de la interfaz quedan fijadas en `frontend/package-lock.json`; las versiones de los dos motores académicos se fijan en sus POM.

## Inicio rápido

### Aplicación completa con un caso simulado

Desde una copia limpia del repositorio:

```bash
python3 scripts/demo.py up
```

El comando construye las imágenes, crea un proyecto aislado con puertos locales disponibles y carga el caso funcional canónico mediante la API. Al finalizar imprime la dirección de la interfaz y la ruta de un archivo de estado. La primera preparación descarga dependencias y puede tardar varios minutos. Las fuentes cargadas y sus hashes quedan registrados por el inicializador; la carga no utiliza inferencias precalculadas ni datos de una evaluación anterior.

En la interfaz, abrir **Alignment & Reasoning** y ejecutar el razonamiento antes de consultar resultados inferidos. Para verificar automáticamente la materialización, la caché, la activación de R03 y las 21 consultas completas, usar el archivo de estado impreso:

```bash
python3 scripts/demo.py smoke --state .local-demo/IDENTIFICADOR/state.json
```

Cada comprobación crea una carpeta nueva con solicitudes, respuestas y un informe. Para detener sólo esa demostración, conservando sus datos y registros:

```bash
python3 scripts/demo.py down --state .local-demo/IDENTIFICADOR/state.json
```

La opción explícita `--remove-data` elimina además los volúmenes de esa demostración. El arranque y la parada no gestionan otros proyectos Docker. Esta ruta permite examinar la aplicación con datos simulados; no acredita integración industrial ni todas las funciones de los conectores y del asistente.

### Plataforma con Docker Compose

Para levantar la plataforma directamente, en un proyecto propio y con puertos locales elegidos:

```bash
export PROTYS_FRONTEND_PORT=18081 PROTYS_BACKEND_PORT=18080 PROTYS_FUSEKI_PORT=13030 PROTYS_POSTGRES_PORT=15432
docker compose -p protys -f docker/docker-compose.yml up -d --build --wait protys-frontend
# Interfaz en http://127.0.0.1:18081 · API en http://127.0.0.1:18080/api
```

`protys-init` carga el catálogo integrado (módulo canónico y 22 reglas SWRL: las de los 26 mecanismos R01–R26 que se expresan en SWRL; R07, R16, R19 y R26 son patrones SPARQL ASK) por la API. Para incorporar además el piloto ISO 10303 con el asistente, por la API: `research/integration/asistente/incorporar.sh`; o desde la interfaz, en **Wizard**. Al volver a levantar la plataforma, `protys-init` conserva lo que la plataforma registró por su API: módulos incorporados, reglas, grafos ERP y sesiones del asistente. Rechaza datos ajenos y cambios en lo canónico (`research/integration/asistente/reiniciar.sh` lo comprueba). Para empezar de cero se borran sólo los volúmenes `protys_fuseki-data` y `protys_postgres-data`.

### Paquete académico reproducible

Los datos, reglas y consultas se identifican mediante `research/catalog.json`. La ejecución mantiene separadas la validación y clasificación con OWL API 5.1.20/HermiT 1.4.5.519 y la ejecución de SWRLAPI 2.1.3/Drools 7.74.1.Final con OWL API 4.5.27. La versión de HermiT sustituye a 1.4.5.456, que produjo una incompatibilidad en la combinación inicialmente propuesta.

Desde la raíz del repositorio, con `JAVA_HOME` apuntando a un JDK 17:

```bash
./research/reproduce.sh --functional
# El comando imprime el directorio nuevo de esta ejecución.
# Cuando su fase funcional resulte PASS, continuar en ese mismo directorio:
./research/reproduce.sh --output research/evaluation/runs/IDENTIFICADOR --benchmark
```

El punto de entrada prepara los dos procesos, verifica la procedencia del descriptor de dependencia conservado y crea salidas identificadas en `research/evaluation/runs/`. Cada fase sólo se ejecuta una vez en un directorio; una repetición, incluso después de un fallo parcial, exige uno nuevo. `research/evaluation/current/` conserva la evaluación histórica y no se reemplaza. Una medición no se reutiliza como resultado de otra configuración. Las réplicas describen ejecución computacional sobre datos simulados. El [protocolo académico](research/README.md) distingue los casos funcionales, las configuraciones y los límites de la evaluación.

### Desarrollo local

Esta ruta requiere preparar los motores y disponer de PostgreSQL y Fuseki propios. La demostración anterior reúne esos pasos para una primera lectura. Para desarrollo, preparar los motores sin ejecutar una evaluación:

```bash
./research/reproduce.sh --prepare-only
```

```bash
# Backend
cd backend
./mvnw clean verify
./mvnw spring-boot:run
# API en http://localhost:8080/api
# Swagger UI en http://localhost:8080/swagger-ui.html

# Frontend (en otra terminal)
cd frontend
npm ci
npm start
# Aplicación en http://localhost:3000
```

## Endpoints principales

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/dashboard` | Estadísticas generales del sistema |
| `GET` | `/api/ontology/modules` | Listar módulos ontológicos cargados |
| `POST` | `/api/ontology/modules/upload` | Cargar un módulo OWL |
| `POST` | `/api/sparql/execute` | Ejecutar consulta SPARQL |
| `GET` | `/api/sparql/templates/competency` | Plantillas CQ1–CQ5 |
| `GET` | `/api/alignment/rules` | Listar reglas SWRL |
| `POST` | `/api/alignment/reasoning/execute` | Ejecutar razonamiento |
| `POST` | `/api/erp/connectors` | Registrar conector ERP |
| `POST` | `/api/erp/connectors/{id}/materialize` | Materializar mapeo R2RML |
| `POST` | `/api/standards` | Iniciar pipeline de incorporación de estándares (§5.3) |
| `POST` | `/api/wizard/step1/upload` | Asistente, paso 1: subir el estándar (RDF/XML, OWL, Turtle, N3, N-Triples) |
| `POST` | `/api/wizard/step1/data/{id}` | Asistente, paso 1: datos de instancia opcionales de la sesión |
| `POST` | `/api/wizard/step2/validate/{id}` | Asistente, paso 2: perfil OWL 2 DL y consistencia |
| `POST` | `/api/wizard/step3/alignments/{id}` | Asistente, paso 3: elegir reglas registradas |
| `POST` | `/api/wizard/step3/rules/{id}` | Asistente, paso 3: reglas SWRL nuevas de la sesión |
| `POST` | `/api/wizard/step4/verify/{id}` | Asistente, paso 4: razonar con la selección |
| `POST` | `/api/wizard/complete/{id}` | Asistente, paso 5: registrar módulos y activar las reglas verificadas |

## PROTYS(KB)

La red de ontologías se organiza en cuatro niveles:

- **Nivel Principal** — Conceptos fundamentales del PLM: Producto, Proceso, Recurso, Empresa
- **Nivel de Refinamiento** — Ontologías individuales con granularidad de dominio
- **Nivel de Estándares** — Formalizaciones de ISO 14040 (LCA) e ISO 15531 (MANDATE), más un fragmento de ISO 10303 AP242 como demostración de extensibilidad (§5.4.3)
- **Nivel de Alineamiento** — Correspondencias contextuales entre conceptos conservados de cada estándar y 26 mecanismos (R01–R26; 22 expresados en SWRL sobre individuos nombrados y cuatro como patrones SPARQL ASK: R07, R16, R19 y R26). El piloto de ISO 10303 añade R27–R29. La consistencia lógica y los casos de prueba se comprueban por separado de la pertinencia de las correspondencias de dominio.

IRI de identificación del modelo (su disponibilidad pública no se presume): `http://w3id.org/protys/ontology/` (cada módulo usa su sub-ruta con sufijo `#`, p. ej. `http://w3id.org/protys/ontology/core#Product`):

| Módulo | IRI |
|--------|-----|
| Core | `http://w3id.org/protys/ontology/core` |
| Product | `http://w3id.org/protys/ontology/product` |
| Process | `http://w3id.org/protys/ontology/process` |
| Resource | `http://w3id.org/protys/ontology/resource` |
| Enterprise | `http://w3id.org/protys/ontology/enterprise` |
| ISO 14040 | `http://w3id.org/protys/ontology/iso14040` |
| ISO 15531 | `http://w3id.org/protys/ontology/iso15531` |
| ISO 10303 AP242 | `http://w3id.org/protys/ontology/iso10303` |
| Alineamiento | `http://w3id.org/protys/ontology/alignment` |
| Extensión caso de estudio | `http://w3id.org/protys/ontology/paint-ext` |

Las instancias (ABox) usan `http://w3id.org/protys/instances/` y los datos materializados desde ERP usan `http://w3id.org/protys/erp/`.

## Caso de estudio: fabricación de pintura

El sistema incluye datos sintéticos de un caso simulado de fabricación de pintura base agua, construido con aportes cualitativos de colegas del instituto. No representa una evaluación de una planta industrial en funcionamiento:

- Siete etapas: recepción e inspección, dispersión, mezcla, ajuste de viscosidad, filtrado, control de calidad y envasado de lotes aprobados.
- Una ficha del molino `PearlMill_01`, con cámara de 50 L y potencia de 30 kW; las cargas mayores se representan mediante subcargas secuenciales.
- Inventario parcial de emisiones de la electricidad consumida por las operaciones, con factores declarados como supuestos. La intensidad se expresa en kg CO₂e/L; no constituye una huella de ciclo de vida completa.
- Casos funcionales pequeños con respuestas esperadas y datos independientes para medir el costo computacional. El catálogo y los conteos de cada versión se conservan en `research/`.
- Ejemplos históricos de conexión ERP mediante R2RML, separados del escenario canónico de la evaluación nueva.

## Preguntas de competencia

El sistema incluye plantillas SPARQL para las cinco preguntas de competencia definidas en la tesis:

| Pregunta de la tesis | Alcance comprobable en el caso | Consulta |
|---------------------|--------------------------------|----------|
| P1 | Recursos necesarios para un proceso en las etapas formalizadas | Q04 |
| P2 | Recursos de un plan de proceso y su uso registrado | Q02 y Q16 |
| P3 | Productos que usan un recurso | Q02 |
| P4 | Actividades que componen un plan de proceso | Q18 |
| P5 | Producto generado por un plan de proceso | Q02 |

El caso formaliza la fase de producción. Las respuestas no se extienden a fases del ciclo de vida que no estén representadas en sus datos. Las salidas esperadas y obtenidas se cotejan por consulta; disponer de 21 consultas no basta, por sí solo, para acreditar las cinco preguntas.

## Tests

El flujo automático de `.github/workflows/verify.yml` prepara ambos motores, ejecuta las pruebas del backend y los casos funcionales, reinstala, prueba y compila la interfaz, y arranca Docker para comprobar la API a través de Nginx. Las 120 réplicas de rendimiento se mantienen como una evaluación controlada separada.

```bash
./research/reproduce.sh --prepare-only
cd backend
./mvnw test                                   # 278 pruebas, 17 salteadas (ver abajo)
cd ../frontend && npm test -- --watchAll=false  # 84 pruebas
cd .. && python3 -m unittest discover -s scripts/tests            # 24 pruebas, incluye la carga inicial
python3 -m unittest discover -s research/evaluation/tests         # 16 pruebas

# Sólo lectura de la conexión ERP contra un PostgreSQL desechable (las 17 salteadas de arriba)
research/integration/revision-2026-11-18/solo-lectura.sh

# Tests individuales
./mvnw test -Dtest=OntologyControllerTest
./mvnw test -Dtest=SPARQLControllerTest
./mvnw test -Dtest=ERPControllerTest
./mvnw test -Dtest=OntologyServiceTest
./mvnw test -Dtest=ERPConnectorServiceTest
```

Ronda 5 de la revisión (`198de8f`, árbol versionado limpio): interfaz 84 de 84
(`research/integration/revision-2026-11-18/salidas/ronda5/frontend-pruebas.txt`) y build sin errores
(`research/integration/revision-2026-11-18/salidas/ronda5/frontend-build.txt`). El cambio fue sólo de interfaz: backend,
scripts y evaluación no se volvieron a correr; sus últimas salidas son las de las rondas 4 y 4b.

Ronda 4b de la revisión (`f508753`, árbol versionado limpio): backend 278 pruebas, 0 fallas, 17 salteadas
(`research/integration/revision-2026-11-18/salidas/ronda4b/backend-pruebas.txt`). Las 17 salteadas son
`ERPReadOnlyConnectionIntegrationTest`: con `solo-lectura.sh`, 17 de 17
(`research/integration/revision-2026-11-18/salidas/ronda4b/solo-lectura-pgjdbc.txt`). El cambio fue sólo de backend:
interfaz, scripts, evaluación y build no se volvieron a correr; sus últimas salidas son las de la cuarta ronda.

Corrida de la cuarta ronda de la revisión (`f716af0`, árbol versionado limpio): backend 276 pruebas, 0 fallas, 16 salteadas
(`research/integration/revision-2026-11-18/salidas/ronda4/backend-pruebas.txt`); interfaz 75 de 75; scripts 24 de 24; evaluación 16 de 16; el build igual
que abajo (`research/integration/revision-2026-11-18/salidas/ronda4/`). Las 16 salteadas son `ERPReadOnlyConnectionIntegrationTest`: con
`solo-lectura.sh`, 16 de 16 (`research/integration/revision-2026-11-18/salidas/ronda4/solo-lectura-pgjdbc.txt`).

Corrida de la tercera ronda de la revisión (`425f270`, árbol versionado limpio): backend 272 pruebas, 0 fallas, 13 salteadas
(`research/integration/revision-2026-11-18/salidas/ronda3/backend-pruebas.txt`); interfaz 75 de 75; scripts 24 de 24; evaluación 16 de 16; el build igual
que abajo (`research/integration/revision-2026-11-18/salidas/ronda3/`). Las 13 salteadas son `ERPReadOnlyConnectionIntegrationTest`: con
`solo-lectura.sh`, 13 de 13 (`research/integration/revision-2026-11-18/salidas/ronda3/solo-lectura-pgjdbc.txt`).

Corrida de la segunda ronda de la revisión (`460f152`, árbol versionado limpio): backend 263 pruebas, 0 fallas, 9 salteadas
(`research/integration/revision-2026-11-18/salidas/ronda2/backend-pruebas.txt`); interfaz 75 de 75; scripts 24 de 24; evaluación 16 de 16; el build igual
que abajo (`research/integration/revision-2026-11-18/salidas/ronda2/`). Las 9 salteadas son `ERPReadOnlyConnectionIntegrationTest`: con
`solo-lectura.sh`, 9 de 9 (`research/integration/revision-2026-11-18/salidas/ronda2/solo-lectura-pgjdbc.txt`).

Corrida final de la primera ronda (con cambios sin commitear en el árbol): backend 254 pruebas, 0 fallas,
3 salteadas (`research/integration/revision-2026-11-18/salidas/backend-pruebas-final.txt`); interfaz 75 de 75 (`research/integration/revision-2026-11-18/salidas/frontend-pruebas-final.txt`); build
igual que abajo (`research/integration/revision-2026-11-18/salidas/frontend-build-final.txt`); scripts 24 de 24 (`research/integration/revision-2026-11-18/salidas/scripts-pruebas-final.txt`);
evaluación 16 de 16 (`research/integration/revision-2026-11-18/salidas/evaluacion-pruebas-final.txt`).

Corrida del 2026-11-18 con los arreglos 1–5 de la revisión, con la salida completa de cada suite: backend 248 pruebas, 0 fallas, 3 salteadas
(`research/integration/revision-2026-11-18/salidas/backend-pruebas.txt`); interfaz 74 de 74 (`research/integration/revision-2026-11-18/salidas/frontend-pruebas.txt`); `CI=true npm run build`
compila sin advertencias del compilador, aunque Node muestra un aviso de obsolescencia (`fs.F_OK`) de
la herramienta de build (`research/integration/revision-2026-11-18/salidas/frontend-build.txt`); scripts 23 de 23 (`research/integration/revision-2026-11-18/salidas/scripts-pruebas.txt`);
evaluación 16 de 16 (`research/integration/revision-2026-11-18/salidas/evaluacion-pruebas.txt`). Las 3 salteadas son
`ERPReadOnlyConnectionIntegrationTest`, que necesita un PostgreSQL desechable y corre con `solo-lectura.sh`
(`research/integration/revision-2026-11-18/salidas/solo-lectura-pgjdbc.txt`: 3 de 3, primera ronda).

## Límites del prototipo

- **API sin autenticación.** `SecurityConfig` deja abierto `/api/**`. Docker Compose publica todos los puertos sólo en `127.0.0.1`; la plataforma no está pensada para exponerse en una red.
- **Ruta del mapeo leída del servidor.** Un conector ERP guarda `r2rmlMappingPath`, una ruta de archivo dentro del contenedor del backend, y el servicio lo lee de ahí. Quien registra un conector elige qué archivo del servidor se lee.
- **Esquema de la base de la plataforma.** `application-docker.yml` declara `ddl-auto: validate`, pero `docker/docker-compose.yml` lo reemplaza por `update` (`SPRING_JPA_HIBERNATE_DDL_AUTO`). Así se crean las columnas nuevas, como `ruleChangesJson`; con `validate`, una base anterior a esa columna no arrancaría. No hay migraciones versionadas.
- **Contraseña del conector en texto plano.** Se guarda tal cual en PostgreSQL (la API no la devuelve: es de sólo escritura en el JSON). Las credenciales del entorno de prueba son de demostración (`docker/integration/.env.example`).

## Stack tecnológico

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Backend | Spring Boot | 3.2.1 |
| Triplestore | Apache Jena / Fuseki | 4.10.0 |
| Validación y clasificación | HermiT | 1.4.5.519 |
| OWL API | OWL API | 5.1.20 |
| Motor de reglas aislado | SWRLAPI / Drools | 2.1.3 / 7.74.1.Final |
| OWL API del proceso de reglas | OWL API | 4.5.27 |
| R2RML | `R2RMLProcessor` propio sobre Jena | pasa los 63 casos R2RML de la suite W3C RDB2RDF |
| Base de datos | PostgreSQL | 15 |
| Frontend | React | 18.3.1 (entorno probado) |
| Estilos | Tailwind CSS | 3.4.19 (entorno probado) |
| Gráficos | Recharts | 2.15.4 |
| Contenedores | Docker Compose | Con soporte de `dockerfile_inline`; versión probada en el informe de mantenimiento |

## Licencia

Este proyecto es parte de una tesis doctoral desarrollada en la Universidad Tecnológica Nacional (UTN), Facultad Regional Santa Fe, en colaboración con INGAR (CONICET-UTN). Distribuido bajo licencia MIT.

El entorno histórico de la interfaz se documenta en `research/release/README.md`. El archivo de versiones y las comprobaciones de instalación posteriores se registran en [docs/MANTENIMIENTO_2026-10-03.md](docs/MANTENIMIENTO_2026-10-03.md). Las dependencias académicas y las de la interfaz se identifican por separado.
