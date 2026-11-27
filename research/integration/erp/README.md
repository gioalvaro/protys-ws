# Conectores ERP con ADempiere y Odoo

PROTYS-WS lee datos de manufactura de una base de ADempiere 3.9.4 (cliente GardenWorld) y de una de Odoo 17.0
(módulo `mrp` con datos de demostración) con un usuario de sólo lectura, los convierte con mapeos R2RML en individuos
de PROTYS(KB) (un grafo nombrado por fuente), razona sobre la red más esos grafos y responde P1–P5 y una consulta que
combina los dos ERP. Los esquemas de los ERP no cambian.

```sh
sh research/integration/erp/correr.sh          # unos 5 minutos; KEEP_UP=1 deja la plataforma levantada
```

Requiere Docker con Compose, `curl` y `python3`. Usa el proyecto Compose `protys-v110-erp` (backend en
127.0.0.1:48080, interfaz en 48081, Fuseki en 43030, PostgreSQL de la plataforma en 45432 y bases de ADempiere y Odoo
en 45433 y 45434) y borra sólo sus volúmenes.

## Resultado (`salidas/e2e/RESUMEN.txt`: APROBADO en los 24 pasos)

| Paso | Valor |
|---|---|
| Materialización R2RML | ADempiere 348 triples, Odoo 201, de 14 triples maps cada uno |
| Razonamiento | CONSISTENT; entrada 4318 → 4867 (+549, los triples materializados); 1586 inferidos |
| Cadena R07 → R24 | 6 controles correctos (`07-brecha-cobertura.txt`) |
| P1 (y Q04 sin cambios) | 15 filas (ADempiere 9, Odoo 6) |
| P2 | 24 filas (18, 6) |
| P3 | 14 filas (10, 4) |
| P4 | 17 filas (12, 5) |
| P5 | 11 filas (8, 3) |
| Consulta conjunta (`CRUZADA`) | 10 filas (8, 2) |
| Q01–Q21 sin y con ERP | `08-catalogo-diferencias.md` |
| Rol de conexión | CREATE y DELETE rechazados en los dos ERP (`09-solo-lectura-*.txt`) |
| Esquemas | huellas idénticas antes y después (`03-huella-antes.txt`, `10-huella-despues.txt`) |

## Versiones

Fijadas en `docker/integration/docker-compose.erp.yml`; la corrida registra las efectivas y el sha256 de los mapeos
en `salidas/e2e/02-versiones.txt`.

- ADempiere 3.9.4 LTS: semilla oficial `adempiere_postgresql_seed.tar.gz` (sha256 `ed4b2b3d…bd1c`) sobre
  `postgres:15.10@sha256:d609c300…d3ea`.
- Odoo: `odoo:17.0@sha256:8779f815…a8fa` (Odoo Server 17.0-20260908), base `postgres:15.10` (mismo digest).

## Archivos

| Archivo | Contenido |
|---|---|
| `esquemas.md` | Tablas de manufactura de cada ERP, columnas usadas y filas |
| `correspondencias.md` | Tabla ERP → clases y propiedades de PROTYS(KB) |
| `queries/` | P1–P5, la consulta conjunta y la de tipos inferidos |
| `run.sh` | Prueba de punta a punta, paso por paso |
| `scripts/` | Relevamiento de esquemas, huella de esquema, verificación de vocabulario, cadena R07 → R24 (`verificar-brecha.py`) y catálogo sin y con ERP (`diferencias-catalogo.py`) |
| `salidas/e2e/` | Salida de `run.sh`, numerada por paso; incluye la entrada preparada del razonador (`07-evidencia-razonamiento/`) |
| `salidas/esquemas-sql.txt` | Columnas y tipos de cada tabla, por SQL con el rol `protys_ro` |
| `salidas/verificar-vocabulario*.txt` | Términos de los mapeos y de las consultas comprobados contra `ontologies/` |

Los mapeos están en `docker/integration/mappings/` y el procesador en
`backend/src/main/java/org/protys/ws/service/R2RMLProcessor.java`. La suite W3C R2RML (63 casos,
`R2RMLW3CTestSuiteTest`) corre con las pruebas de backend (`../suites/backend-pruebas.txt`); sus recursos están en
`backend/src/test/resources/w3c-r2rml/` tal como los publica el W3C.
