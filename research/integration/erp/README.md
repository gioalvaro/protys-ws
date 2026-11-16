# Evidencia E-ERP: PROTYS-WS conectado a ADempiere y Odoo

**Qué respalda.** PROTYS-WS lee datos de manufactura de una base de ADempiere 3.9.4 y de una base
de Odoo 17.0 (las dos con datos de demostración), los convierte con mapeos R2RML en individuos de
PROTYS(KB) (un grafo nombrado por fuente), razona sobre la red más esos grafos y responde P1–P5
y una consulta que combina los dos ERP, sin modificar los esquemas de los ERP.

## Cómo reproducirlo

```bash
research/integration/erp/run.sh          # de cero, unos 5 minutos; deja el proyecto levantado
KEEP_UP=0 research/integration/erp/run.sh  # igual, y baja todo al terminar
```

Requiere Docker con Compose, `curl` y `python3`. Usa el proyecto Compose `protys-e2e` con puertos
propios (backend en 127.0.0.1:18080) y borra sólo los volúmenes de ese proyecto. Termina con un
resumen APROBADO/FALLADO por paso (`salidas/e2e/RESUMEN.txt`).

## Versiones
Fijadas en `docker/integration/docker-compose.erp.yml`. La corrida registra las efectivas en
`salidas/e2e/02-versiones.txt`.
- ADempiere 3.9.4 LTS: semilla oficial `adempiere_postgresql_seed.tar.gz`, sha256
  `ed4b2b3d…bd1c`, sobre `postgres:15.10@sha256:d609c300…d3ea`.
- Odoo: `odoo:17.0@sha256:8779f815…a8fa` (Odoo Server 17.0-20260908), base `postgres:15.10` (mismo digest).
- Backend: Java 17.0.21 (imagen construida desde este repositorio; commit en `02-versiones.txt`).
- Pruebas de backend: Temurin 17 (`eclipse-temurin:17-jdk@sha256:54658156…979f`).

## Archivos
| Archivo | Contenido |
|---|---|
| `esquemas.md` | tablas reales de manufactura de cada ERP, columnas usadas y filas |
| `correspondencias.md` | tabla ERP → clases y propiedades de PROTYS(KB) |
| `tablas-tesis-5.8-5.9.md` | Tablas 5.8 y 5.9 de la tesis y su contraste con lo mapeado |
| `decision-mapeo.md` | por qué un procesador R2RML propio (a1) |
| `queries/` | P1–P5, la consulta conjunta y la de tipos inferidos, en SPARQL |
| `scripts/` | relevamiento de esquemas, huella de esquema, verificación de vocabulario, cadena R07 → R24 (`verificar-brecha.py`), catálogo sin y con ERP (`diferencias-catalogo.py`) |
| `salidas/` | salidas crudas: relevamiento SQL, `/introspect`, vocabulario |
| `salidas/e2e/` | salida cruda de la última corrida de `run.sh`, numerada por paso; incluye la entrada preparada del razonador (`07-evidencia-razonamiento/`) y el cuadro del catálogo (`08-catalogo-diferencias.md`) |
| `salidas/w3c-r2rml-report.txt` | resultado por caso de la suite W3C R2RML |
| `salidas/pruebas-backend.txt` | resumen de la suite de backend |
| `INFORME.md` | informe de la fase |

Los mapeos ejecutados están en `docker/integration/mappings/` y el procesador en
`backend/src/main/java/org/protys/ws/service/R2RMLProcessor.java`.
