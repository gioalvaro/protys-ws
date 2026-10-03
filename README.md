# PROTYS-WS

**Sistema web para la gestión de la red de ontologías PROTYS(KB)**

PROTYS-WS es el sistema prototipo que operacionaliza PROTYS(KB), una red de ontologías diseñada para abordar la interoperabilidad semántica entre sistemas de información heterogéneos en la industria manufacturera. Permite explorar, consultar y gestionar formalizaciones de subconjuntos de ISO 15531 (ISO/TC 184/SC 4) e ISO 14040 (ISO/TC 207/SC 5), con un piloto separado de ISO 10303, y ejecutar razonamiento semántico mediante reglas SWRL. Incluye conectores y ejemplos experimentales de mapeos para ERP; la evaluación nueva no acredita una integración con ERP industriales.

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
│       ├── controller/      # 7 controladores REST (39 endpoints)
│       ├── service/         # 7 servicios de negocio (incluye ReasoningService)
│       ├── model/           # Entidades JPA
│       ├── dto/             # Objetos de transferencia
│       ├── repository/      # Repositorios JPA
│       └── exception/       # Manejo global de errores
│
├── frontend/                # React 18.2 + Tailwind CSS 3.4
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
│   ├── alignment-rules.owl  # Correspondencias contextuales del caso + reglas SWRL R01–R26
│   ├── alignment-queries.sparql      # Patrones de validación SPARQL ASK (R07, R16, R19, R26)
│   ├── paint-case-extension.owl      # Extensión del caso de estudio (ontología derivada, §4.4.1)
│   ├── iso10303-ap242-fragment.owl   # Fragmento local derivado de AP242 (§5.4.3)
│   ├── iso10303-alignment-rules.owl  # Reglas SWRL R27–R29
│   └── paint-*.ttl          # Instancias del caso de estudio
│
├── docker/                  # Docker Compose (4 servicios)
│   ├── docker-compose.yml
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
└── sql/                     # Esquemas ERP de ejemplo
    ├── adempiere-schema.sql
    └── odoo-schema.sql
```

## Requisitos

- Java 17 para la evaluación conservada
- Maven 3.8+
- Node.js 20+
- Docker y Docker Compose
- PostgreSQL 15 (o mediante Docker)

## Inicio rápido

### Paquete académico reproducible

Los datos, reglas y consultas se identifican mediante `research/catalog.json`. La ejecución mantiene separadas la validación y clasificación con OWL API 5.1.20/HermiT 1.4.5.519 y la ejecución de SWRLAPI 2.1.3/Drools 7.74.1.Final con OWL API 4.5.27. La versión de HermiT sustituye a 1.4.5.456, que produjo una incompatibilidad en la combinación inicialmente propuesta.

Desde la raíz del repositorio, con `JAVA_HOME` apuntando a un JDK 17:

```bash
./research/reproduce.sh --functional
# Cuando las comprobaciones funcionales resulten PASS:
./research/reproduce.sh --benchmark
```

El punto de entrada prepara los dos procesos, verifica la procedencia del descriptor de dependencia conservado y mantiene las salidas en `research/evaluation/current/`. Una medición no se reutiliza como resultado de otra configuración. Las réplicas describen ejecución computacional sobre datos simulados. El [protocolo académico](research/README.md) distingue los casos funcionales, las configuraciones y los límites de la evaluación.

### Distribución auxiliar con Docker

La distribución de la interfaz y los servicios en Docker se conserva como alternativa de desarrollo. La imagen histórica del backend no incluye aún los procesos del runtime académico nuevo; su razonamiento en contenedor requiere preparación y verificación adicionales. La evaluación debe seguir el punto de entrada académico y la ruta local del prototipo comprobada para esta versión.

```bash
# Configurar credenciales (opcional en desarrollo, obligatorio en producción)
cd docker
cp .env.example .env   # editar FUSEKI_ADMIN_PASSWORD, POSTGRES_PASSWORD, etc.

# Levantar todos los servicios
docker-compose up -d

# Cargar ontologías en Fuseki
chmod +x init-fuseki.sh
./init-fuseki.sh

# Verificar
curl http://localhost:8080/api/dashboard      # Backend
curl http://localhost:3030/protys/sparql       # Fuseki
open http://localhost:3000                     # Frontend
```

### Desarrollo local

```bash
# Backend
cd backend
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# API en http://localhost:8080/api
# Swagger UI en http://localhost:8080/swagger-ui.html

# Frontend (en otra terminal)
cd frontend
npm install
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
| `POST` | `/api/wizard/step1/upload` | Asistente de incorporación de estándar (paso a paso) |

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

```bash
cd backend
mvn test

# Tests individuales
mvn test -Dtest=OntologyControllerTest
mvn test -Dtest=SPARQLControllerTest
mvn test -Dtest=ERPControllerTest
mvn test -Dtest=OntologyServiceTest
mvn test -Dtest=ERPConnectorServiceTest
```

## Stack tecnológico

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Backend | Spring Boot | 3.2.1 |
| Triplestore | Apache Jena / Fuseki | 4.10.0 |
| Validación y clasificación | HermiT | 1.4.5.519 |
| OWL API | OWL API | 5.1.20 |
| Motor de reglas aislado | SWRLAPI / Drools | 2.1.3 / 7.74.1.Final |
| OWL API del proceso de reglas | OWL API | 4.5.27 |
| R2RML | CARML Engine | 0.4.3 |
| Base de datos | PostgreSQL | 15 |
| Frontend | React | 18.2 |
| Estilos | Tailwind CSS | 3.4 |
| Gráficos | Recharts | 2.10 |
| Contenedores | Docker Compose | 3.8 |

## Licencia

Este proyecto es parte de una tesis doctoral desarrollada en la Universidad Tecnológica Nacional (UTN), Facultad Regional Santa Fe, en colaboración con INGAR (CONICET-UTN). Distribuido bajo licencia MIT.
