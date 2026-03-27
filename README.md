# PROTYS-WS

**Sistema web para la gestión de la red de ontologías PROTYS(KB)**

PROTYS-WS es el sistema prototipo que operacionaliza [PROTYS(KB)](http://protys.ingar.conicet.gob.ar/ontology#), una red de ontologías diseñada para resolver la interoperabilidad semántica entre sistemas de información heterogéneos en la industria manufacturera. Permite explorar, consultar y gestionar ontologías basadas en estándares ISO del comité TC184/SC4, ejecutar razonamiento semántico mediante reglas SWRL y conectar sistemas ERP mediante mapeos R2RML.

Desarrollado como parte de la tesis doctoral:

> **"Red de Ontologías para la Interoperabilidad Semántica en Sistemas de Información de Manufactura"**
>
> Alvaro Luis Fraga — UTN, Facultad Regional Santa Fe
>
> Directora: Dra. María Marcela Vegetti (INGAR CONICET-UTN)

## Arquitectura

```
┌─────────────────────────────────────────────────────────┐
│                  Frontend  (React 18 + Tailwind CSS)     │
│  Dashboard │ Explorer │ SPARQL │ Alignment │ ERP │ Wizard│
├─────────────────────────────────────────────────────────┤
│                  REST API  (Spring Boot 3.2)             │
│  OntologyService │ SPARQLService │ AlignmentService      │
│  ERPConnectorService │ StandardIncorporationService      │
├───────────────────────┬─────────────────────────────────┤
│  Apache Jena 4.10     │        PostgreSQL 15             │
│  Fuseki + TDB2        │        Metadatos JPA             │
├───────────────────────┴─────────────────────────────────┤
│              PROTYS(KB) — Red de Ontologías               │
│  Core │ Product │ Process │ Resource │ Enterprise         │
│  ISO 14040 │ ISO 15531 │ 26 reglas SWRL │ Instancias     │
└─────────────────────────────────────────────────────────┘
```

## Estructura del proyecto

```
protys-ws/
├── backend/                 # Spring Boot 3.2 + Jena 4.10
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/ve/edu/uc/protys/
│       ├── config/          # Jena, CORS, Security, DataInitializer
│       ├── controller/      # 6 controladores REST (38 endpoints)
│       ├── service/         # 6 servicios de negocio
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
│   ├── core-concepts.owl
│   ├── product-module.owl
│   ├── process-module.owl
│   ├── resource-module.owl
│   ├── enterprise-module.owl
│   ├── iso14040-module.owl
│   ├── iso15531-module.owl
│   ├── alignment-rules.owl  # 26 reglas SWRL (R01–R26)
│   └── paint-*.ttl          # Instancias del caso de estudio
│
├── docker/                  # Docker Compose (4 servicios)
│   ├── docker-compose.yml
│   ├── fuseki-config.ttl
│   └── init-fuseki.sh
│
└── sql/                     # Esquemas ERP de ejemplo
    ├── adempiere-schema.sql
    └── odoo-schema.sql
```

## Requisitos

- Java 17+
- Maven 3.8+
- Node.js 20+
- Docker y Docker Compose
- PostgreSQL 15 (o mediante Docker)

## Inicio rápido

### Con Docker (recomendado)

```bash
# Levantar todos los servicios
cd docker
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
| `POST` | `/api/wizard/step1/upload` | Asistente de incorporación de estándar |

## PROTYS(KB)

La red de ontologías se organiza en cuatro niveles:

- **Nivel Principal** — Conceptos fundamentales del PLM: Producto, Proceso, Recurso, Empresa
- **Nivel de Refinamiento** — Ontologías individuales con granularidad de dominio
- **Nivel de Estándares** — Formalizaciones de ISO 14040 (LCA) e ISO 15531 (MANDATE)
- **Nivel de Alineamiento** — 26 reglas SWRL para correspondencias inter-estándar

Namespace: `http://protys.ingar.conicet.gob.ar/ontology#`

## Caso de estudio: fabricación de pintura

El sistema incluye datos de un caso real de fabricación de pintura base agua:

- 7 materiales con indicadores ambientales (ISO 14040)
- 5 equipos con capacidades y OEE (ISO 15531 / MANDATE)
- 7 etapas de proceso con trazabilidad completa
- 3 operadores con certificaciones formalizadas
- Integración con ADempiere 4.x y Odoo 16.x vía R2RML

## Preguntas de competencia

El sistema incluye plantillas SPARQL para las cinco preguntas de competencia definidas en la tesis:

| CQ | Descripción |
|----|-------------|
| CQ1 | Materiales y propiedades ambientales (ISO 14040) |
| CQ2 | Equipos y capacidades de manufactura (ISO 15531) |
| CQ3 | Trazabilidad producto–proceso–recurso |
| CQ4 | Impacto ambiental a lo largo del ciclo de vida |
| CQ5 | Recursos organizacionales disponibles |

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
| Reasoner | HermiT | 1.4.5.456 |
| OWL API | OWL API | 5.1.20 |
| R2RML | CARML Engine | 0.4.3 |
| Base de datos | PostgreSQL | 15 |
| Frontend | React | 18.2 |
| Estilos | Tailwind CSS | 3.4 |
| Gráficos | Recharts | 2.10 |
| Contenedores | Docker Compose | 3.8 |

## Licencia

Este proyecto es parte de una tesis doctoral desarrollada en la Universidad Tecnológica Nacional (UTN), Facultad Regional Santa Fe, en colaboración con INGAR (CONICET-UTN). Distribuido bajo licencia MIT.
