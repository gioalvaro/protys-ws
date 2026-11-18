# Revisión de la rama `trabajo-tecnico-2026-11` (18-11-2026)

Una revisión externa dejó ocho hallazgos importantes y un lote de menores. Antes de cambiar nada se
confirmó cada uno en el código o con una prueba. Hay un commit por hallazgo y uno para los menores, sin
push. Las salidas guardadas de las fases anteriores (`../*/salidas/`) no se tocaron: todo lo nuevo está
en `salidas/` de esta carpeta.

## Resultado del cierre

| Comprobación | Resultado | Salida |
|---|---|---|
| Flujo ERP completo (`run.sh`, proyecto `protys-e2e`, código final) | 24 de 24 APROBADO. Los mismos números del INFORME: ADempiere 348 triples, Odoo 201, entrada 4318 → 4867, inferidos 1586, Q04 +15, Q20 +20, las otras 19 consultas iguales | `salidas/erp-e2e/RESUMEN.txt`, `salidas/erp-e2e/08-catalogo-diferencias.md` |
| Permisos de `protys_ro` después de esa corrida | `SELECT` sólo sobre las 11 tablas de ADempiere y las 10 de Odoo que leen los mapeos | `salidas/protys-ro-permisos.txt` |
| Secuencia del asistente por la API (`incorporar.sh`, plataforma `protys` de cero, código final) | 12 de 12 APROBADO; 25 reglas activas; entrada 4318 → 4662; inferidos 1167 → 1419; 21 consultas iguales; 8 controles H3 de g30 iguales a lo esperado | `salidas/asistente-api/RESUMEN.txt` |
| Suites con el código final | backend 254, 0 fallas, 3 salteadas; interfaz 75/75; scripts 24/24; evaluación 16/16; el build compila sin advertencias del compilador | `salidas/*-final.txt` |
| Sólo lectura de la conexión contra PostgreSQL desechable | 3 de 3 | `salidas/solo-lectura-pgjdbc.txt` |

Las 3 salteadas del backend son `ERPReadOnlyConnectionIntegrationTest`, que necesita la base desechable
que levanta `solo-lectura.sh`.

## Por hallazgo

### 1. Cancelar un completado interrumpido dejaba reglas globales activas — confirmado, corregido (`5f929d1`)
- **Confirmación.** `completeIncorporation` llamaba a `activateVerifiedRules` antes de registrar el
  módulo, y `cancelIncorporation` no deshacía nada de eso. La prueba nueva, corrida sin el arreglo,
  falla con el síntoma: `expected {R01=false, R02=true} but was {R01=true, R02=true, R27=true}`.
- **Cambio.** `StandardIncorporationService` y `StandardIncorporationSession` (columna nueva
  `ruleChangesJson`). Antes de aplicar, se guarda en la sesión qué reglas se crean, cuáles pasan de
  inactivas a activas, qué definiciones y qué triples sueltos se suman al grafo de reglas; en un
  reintento se suma a lo anterior. Al cancelar con la escritura permanente ya empezada se borran esas
  reglas y definiciones, se sacan esos triples y se vuelven a inactivas las activadas. Es idempotente.
- **Pruebas.** `WizardSessionRulesTest`: `cancelarUnCompletadoCortadoDejaLasReglasComoEstaban`
  (registro, estados y grafo de reglas iguales a la línea de base, también al cancelar dos veces) y
  `cancelarNoTocaReglasQueYaEstabanActivasNiDefinicionesPrevias`. Salida: `salidas/backend-pruebas.txt`.
- **README del asistente.** Corregido donde decía qué hace cancelar.
- **Límite.** Sólo se registran los triples sin nodos en blanco fuera de las definiciones. Las
  definiciones, que sí tienen nodos en blanco, se sacan regla por regla.

### 2. Completar reactiva reglas desactivadas a propósito — confirmado, hecho visible (`705f374`)
- **Comportamiento.** No cambia; un completado cortado lo revierte el punto 1.
- **Cambio.** El DTO tiene un campo nuevo, `reactivatedRules`. En el paso 4 es la vista previa; en el
  resultado del completado, lo que efectivamente se reactivó. La pantalla final del asistente las
  muestra como «Platform-wide change» antes de «Create Ontology», y el resumen posterior las repite. El
  README del asistente lo suma a sus limitaciones.
- **Pruebas.** `WizardSessionRulesTest.elPaso4AvisaQueReglasInactivasVaAReactivar` y una prueba de
  interfaz en `Wizard.test.jsx`.
- **En la secuencia de cierre** el campo vino vacío, porque las 22 reglas ya estaban activas
  (`salidas/asistente-api/04-paso4-verificar.json`, `05-completar.json`).
- **No se hizo:** no hay captura de pantalla del aviso. Está cubierto sólo por la prueba de interfaz.

### 3. Filtro de SQL del procesador R2RML — confirmado, corregido (`a7702ec`)
- **Cambio.** Clase nueva `SqlQueryGuard`, usada por `R2RMLProcessor` y por el formato XML heredado de
  `ERPConnectorService`.
  - Acepta `SELECT` y `WITH … SELECT`.
  - Fuera de literales, comillas, comentarios y cadenas con `$`, rechaza `INTO`, `FOR UPDATE`/`SHARE`
    (con `NO KEY` y `KEY`) y `;`.
  - Rechaza también las CTE que modifican datos (`INSERT`, `UPDATE`, `DELETE`, `MERGE` dentro de un
    `WITH`), que no estaban en el pedido.
  - El mensaje dice qué se rechazó.
  - El Javadoc dice que no es una barrera de seguridad.
- **Límites por consulta.** Tiempo máximo y tope de filas configurables (`protys.erp.query-timeout-seconds`,
  60 s, y `protys.erp.max-rows`, 500 000). Superar el tope es un error, nunca se trunca.
- **Diferencia con lo pedido.** Se acepta **un único `;` al final** y se descarta. Los casos W3C D011 y
  D015 terminan así y esperan resultado: rechazarlo rompería la suite. Cualquier otro `;` se rechaza.
- **Pruebas.** `SqlQueryGuardTest` (19), que cubre:
  - CTE aceptada;
  - `INTO`, `FOR UPDATE`, `FOR SHARE` y `;` intermedio rechazados;
  - tiempo máximo de 1 s que corta una consulta larga;
  - tope de filas.
- **Suite W3C:** 63 de 63 (`salidas/r2rml-w3c.txt`, `salidas/r2rml-filtro-pruebas.txt`).

### 4. «Sólo lectura» de la conexión de la aplicación — confirmado, corregido (`bfec591`)
- **Comprobación empírica.** Contra un PostgreSQL desechable, con un usuario que puede escribir:
  - con autocommit activo, `setReadOnly(true)` no tiene efecto en pgjdbc 42.6.0 (PostgreSQL 15.19), y
    el `INSERT` se aceptó;
  - con la conexión nueva del conector (sin autocommit, sólo lectura, cierre con rollback), el mismo
    `INSERT` falla con SQLSTATE 25006.

  Salida: `salidas/solo-lectura-pgjdbc.txt`; script: `solo-lectura.sh`; prueba:
  `ERPReadOnlyConnectionIntegrationTest`.
- **Cambio.** `ERPConnectorService.openReadOnly`, que usan `/test`, `/introspect` y `/materialize`.
- **Rol.** `protys_ro` pasa a tener `SELECT` sólo sobre las tablas de los mapeos
  (`docker/integration/odoo/inicializar.sh`, `docker/integration/adempiere-db/10-restaurar-semilla.sh`).
  El paso 9 de `run.sh` no cambió.
- **INFORME de ERP.** Separa las dos garantías: el rol no puede escribir y la conexión es de sólo
  lectura.
- **Efecto lateral.** `erp/scripts/relevar-esquemas.sh` lee dos tablas fuera de los mapeos
  (`s_resourcetype`, `pp_order_bomline`). Con el permiso restringido fallaría, así que ahora usa el
  superusuario de cada base, como `huella-esquema.sh`.
- **Sin cambios en `/introspect`.** Sigue informando 992 y 336 tablas, porque el catálogo de
  PostgreSQL lista todas aunque el rol no pueda leerlas.
- **Verificación de los 24 pasos.** Se hizo en el cierre, con todo el código final.

### 5. `run.sh` podía borrar los volúmenes de la plataforma principal — confirmado, corregido (`75df87a`)
- **`run.sh`.** Con `COMPOSE_PROJECT_NAME=protys` aborta antes de cualquier `down` y de tocar sus
  salidas (`salidas/run-sh-guarda-protys.txt`).
- **`huella-esquema.sh` y `relevar-esquemas.sh`.** Usan `protys-e2e` por defecto, igual que `run.sh`.
- **Los otros scripts.** `reiniciar.sh` no borra volúmenes (sólo `stop` y `up`). `incorporar.sh` y
  `secuencia.sh` sólo usan la API.
- **Riesgo nuevo encontrado.** `run.sh`, `incorporar.sh` y `secuencia.sh` borran y reescriben su
  carpeta de evidencia. Ahora aceptan `OUT` para escribir en otra; así se corrió el cierre.

### 6. Conteos de pruebas sin salida guardada — confirmado, corregido (`63c36c5`, conteos finales en `aac96ef`)
- **Dos corridas guardadas.** Una con los arreglos 1–5 (`salidas/backend-pruebas.txt`,
  `frontend-pruebas.txt`, `frontend-build.txt`, `scripts-pruebas.txt`, `evaluacion-pruebas.txt`). Otra
  final, con los menores (`*-final.txt`).
- **README principal.** Muestra los números finales.
- **README del asistente.** Marca los del 2026-11-17 como sin salida guardada y enlaza la corrida nueva.
- **«Build sin advertencias».** Se precisó: el compilador no da advertencias, pero Node muestra un aviso
  de obsolescencia (`fs.F_OK`) de la herramienta de build.

### 7. Salidas guardadas editadas al reescribir los commits — confirmado, documentado (`13c2a27`)
- **Recuperación.** Los identificadores anteriores se recuperaron de la rama local
  `trabajo-tecnico-2026-11-respaldo`.
- **README del asistente («Paso 5 — Mensajes de commit»).** Tiene ahora:
  - la tabla hash anterior → hash actual de los 30 commits;
  - las siete salidas con la línea en que se reemplazó un identificador;
  - la línea 9 de `versiones.txt`, cambiada en la reescritura;
  - la regla para adelante: las salidas no se editan; las equivalencias van en tabla.
- **Comprobación.** Cada par tiene el mismo árbol salvo esa línea (`salidas/hashes-reescritura.txt`).
- **Esas salidas no se volvieron a tocar.**

### 8. «8 de 8 controles H3» — confirmado, redacción corregida (`a06a1fa`)
- **Hecho.** Cuatro controles pasan de `false` a `true` con la incorporación. Los otros cuatro esperan
  `false` y ya daban `false` antes: confirman que no hay efectos indebidos, pero no distinguen el antes
  del después.
- **Cambio.** Corregido en los cinco lugares del README del asistente. `06-controles-H3.md` no se tocó.

### Menores (`aac96ef`)
| Punto | Qué se hizo | Respaldo |
|---|---|---|
| Claves de join numéricas | Se comparan por valor; la prueba (INTEGER 8 frente a DOUBLE 8.0) falla sin el arreglo | `R2RMLProcessorTypesTest`, `salidas/backend-pruebas-final.txt` |
| `TIMESTAMP WITH TIME ZONE` | `xsd:dateTime` con zona; también `timestamptz` de pgjdbc, que se informa como TIMESTAMP. Se conserva el instante, no el desplazamiento escrito (aclarado en la ronda 3) | ídem |
| Javadoc «vocabulario completo» | Ya se corrigió en `a7702ec`: dice qué términos implementa y qué restringe | — |
| `{"dataGraphs": null}` | 400 con mensaje, también `[null]` | `AlignmentControllerTest` |
| Acumulador muerto de `inputTriples` | Quitado | — |
| Recargar reglas | Una regla existente conserva `createdAt` y su estado: no se reactiva. La respuesta trae cada regla con su estado. Se eligió no reactivar antes que avisar | `AlignmentRuleUploadTest` |
| Módulo inexistente | 404 con `{"error": …}` en `/classes` y `/validate` | `OntologyControllerTest` |
| `load_canonical.py` | Identificador de regla = nombre local como `getLocalName()` de Jena; comparado con Jena 4.10 en cuatro IRIs | `scripts/tests/test_load_canonical.py`, `salidas/scripts-pruebas-final.txt` |
| `App.jsx` | Con la barra abierta, X en pantallas angostas y menú en anchas | `NavToggleIcon.test.jsx` |
| `06-catalogo-diferencias.md` | La aclaración ya estaba en el encabezado del archivo. Igual se sumó en el README | — |
| 128 frente a 87 clases | 87 de los ocho módulos de `ontologies/`, + 30 de la extensión de pintura, + 11 de los tres perfiles de investigación | `contar-clases.py`, `salidas/clases-catalogo.txt` |
| 22 reglas SWRL de los 26 mecanismos | Aclarado en el README | — |
| Huella | El INFORME dice que compara esquema y permisos, no datos | — |
| Corrida funcional no versionada | El README del asistente dice que `runs/` está en `.gitignore` y cómo regenerarla y compararla | — |
| Límites del prototipo | Sección nueva en el README: API sin autenticación (sólo 127.0.0.1), ruta del mapeo leída del servidor, contraseña del conector en texto plano | verificado en `SecurityConfig`, `ERPConnector` y `docker-compose.yml` |

## Qué quedó sin hacer
- **Captura del aviso de reactivación.** No hay captura de pantalla del aviso del punto 2; está cubierto
  sólo por la prueba de interfaz.
- **`reiniciar.sh`.** No se volvió a correr sobre el código final.
- **Evaluación funcional de la tesis.** No se volvió a correr: no depende del backend que cambió.
- **Estado de las plataformas.**
  - `protys` quedó levantada en http://127.0.0.1:18081 con el piloto incorporado por la secuencia de
    cierre. Antes de esa secuencia se borraron sólo `protys_fuseki-data` y `protys_postgres-data`.
  - `protys-e2e` quedó detenida, con sus volúmenes.

## Ronda 2 (18-11-2026)

Segunda revisión de los commits `7fc4cf6..d334e3a`: dos puntos importantes y unos pocos menores. Toda la
evidencia de esta ronda se generó con el árbol versionado limpio (`460f152`), salvo lo que se indica, y está
en `salidas/ronda2/`. Las salidas de la primera ronda no se tocaron.
Las dos comprobaciones del punto A llevan `df79352` y `3eea75e`, no `460f152`: se corrieron a propósito antes
del arreglo, para registrar lo que pasaba sin él.

### A. El filtro de SQL se salteaba con una cadena `E'…'`

**Comprobación empírica, primero y textual.** Contra el PostgreSQL desechable de `solo-lectura.sh`, con un
usuario que puede escribir y sin pasar por el filtro, por la conexión del conector de la primera ronda
(sin autocommit y en sólo lectura), cerrada con rollback (`salidas/ronda2/comprobacion-varias-sentencias.txt`,
commit `df79352`):

```
executeQuery [SELECT 1; COMMIT; INSERT INTO prueba VALUES (99)] -> rechazada, SQLSTATE 0100E: Multiple ResultSets were returned by the query.; filas en prueba=1
execute [SELECT 1; COMMIT; INSERT INTO prueba VALUES (99)] -> aceptada; filas en prueba=1
executeQuery [SELECT 1; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT] -> rechazada, SQLSTATE 0100E: Multiple ResultSets were returned by the query.; filas en prueba=1
execute [SELECT 1; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT] -> aceptada; filas en prueba=1
executeQuery [SELECT 1; SET TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)] -> rechazada, SQLSTATE 25001: ERROR: transaction read-write mode must be set before any query; filas en prueba=0
execute [SELECT 1; SET TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)] -> rechazada, SQLSTATE 25001: ERROR: transaction read-write mode must be set before any query; filas en prueba=0
```

Las dos primeras variantes **escribían**, también con `executeQuery`, que es lo que usa el procesador: el
error 0100E llega después de ejecutar todas las sentencias. Combinado con el hueco del filtro
(`SqlQueryGuard.check("SELECT E'\\'' ; COMMIT; DELETE FROM t; --'")` se aceptaba entera; confirmado), un
mapeo con un usuario que pudiera escribir podía escribir. Con `protys_ro` no, porque el rol no tiene permiso.

**Configuraciones de conexión evaluadas** (`salidas/ronda2/comprobacion-configuraciones.txt`, commit `3eea75e`):

| Configuración | `COMMIT; INSERT` | `COMMIT; BEGIN READ WRITE; INSERT` | `SET TRANSACTION READ WRITE; INSERT` | `COMMIT; SET SESSION CHARACTERISTICS … READ WRITE; INSERT` |
|---|---|---|---|---|
| características de sesión `READ ONLY` al abrir | rechazada (25006) | **escribe** | rechazada (25001) | rechazada (25006) |
| `options=-c default_transaction_read_only=on` | rechazada (25006) | **escribe** | rechazada (25001) | rechazada (25006) |
| `readOnlyMode=always` | **escribe** | **escribe** | rechazada (25001) | **escribe** |
| `preferQueryMode` simple, extended o extendedForPrepared | **escribe** | **escribe** | rechazada (25001) | **escribe** |

Ninguna configuración impide un `BEGIN READ WRITE` explícito dentro de una consulta de varias sentencias.

**Cambios** (`1095e5e`, `5c8a864`):
- `SqlQueryGuard.codeOnly`: en `E'…'`/`e'…'` la barra invertida escapa el carácter siguiente; en las cadenas
  comunes y `U&'…'` sólo `''` escapa, y una barra invertida se rechaza porque su sentido depende de
  `standard_conforming_strings` (ante la duda, rechazar).
- `ERPConnectorService.openReadOnly`: en PostgreSQL agrega `options=-c default_transaction_read_only=on`
  (la más simple de las dos que bloquean más variantes; no requiere una sentencia extra).
- Redacción: el Javadoc de `ERPConnectorService` y de `SqlQueryGuard` y el INFORME de ERP (sección «Sólo
  lectura: dos garantías distintas» y «Qué puede afirmar») dicen qué rechaza la conexión, que no puede
  impedir `BEGIN READ WRITE` en varias sentencias y que la garantía que no depende de la consulta es el rol.

**Pruebas.** `SqlQueryGuardTest` (21): la reproducción rechazada, `E'a\'b'` aceptada como una sola cadena,
`E'\\'` seguida de `;` intermedio rechazada, barra en cadena común rechazada.
`ERPReadOnlyConnectionIntegrationTest`: `COMMIT; INSERT`, `SET TRANSACTION READ WRITE` y `SET SESSION
CHARACTERISTICS … READ WRITE` no dejan filas por la conexión del conector; `BEGIN READ WRITE` lo rechaza el
filtro y, con un rol que sólo puede leer, tampoco deja filas. Suite W3C: 63 de 63
(`salidas/ronda2/backend-pruebas.txt`). Con la conexión final, la comprobación repetida muestra la variante 1
rechazada (25006) y la 2 todavía escribiendo (`salidas/ronda2/solo-lectura-pgjdbc.txt`).

**Diferencia con lo pedido.** La prueba pedida «las tres variantes no dejan filas» no se puede cumplir para
`BEGIN READ WRITE` con la conexión sola; la prueba afirma lo comprobado: que la frenan el filtro y el rol.

### B. Evidencia generada sobre código sin commitear

- Las salidas viejas (`salidas/solo-lectura-pgjdbc.txt`, `salidas/*-final.txt`) no se tocaron.
- `solo-lectura.sh` (`e2ba9cf`): sin el `|| true`; devuelve el código de salida de Maven y conserva la
  limpieza. Comprobado rompiendo la prueba en una copia sin commitear: el script terminó con 1, marcó
  `+cambios-sin-commit` y no dejó contenedores (esa corrida no se guardó como evidencia). El encabezado ahora
  marca cambios en cualquier parte del árbol versionado y se borra el reporte anterior de surefire.
- Las rutas locales de la máquina (`/Users/…`) se reemplazaron por `<repo>` o `~` en las salidas nuevas
  antes de commitearlas, como en la primera ronda.
- Corridas sobre `460f152`, árbol limpio: `salidas/ronda2/solo-lectura-pgjdbc.txt` (9 de 9),
  `backend-pruebas.txt` (263, 0 fallas, 9 salteadas), `frontend-pruebas.txt` (75/75), `frontend-build.txt`,
  `scripts-pruebas.txt` (24/24), `evaluacion-pruebas.txt` (16/16).
- Un traspié propio: la primera corrida de `run.sh` de esta ronda registró `460f152+cambios-sin-commit`
  porque edité los README mientras arrancaba. Se cortó, se apartaron los cambios y se repitió con el árbol
  limpio; sólo la corrida repetida está en `salidas/ronda2/erp-e2e/`.

### Menores (`460f152`)

| Punto | Qué se hizo | Respaldo |
|---|---|---|
| Zona horaria en PostgreSQL | `timestamptzDePostgresqlConservaElInstante` (en las salidas de las rondas 2 y 3 figura con su nombre anterior, `…ConservaElDesplazamiento`): `'2026-11-18 10:15:30-03'` → `"2026-11-18T13:15:30Z"^^xsd:dateTime`. Se conserva el instante, no el desplazamiento escrito (`-03` pasa a `Z`); falla sin la rama de `R2RMLProcessor` (comprobado en una copia sin commitear) | `salidas/ronda2/solo-lectura-pgjdbc.txt` |
| Tiempo máximo en PostgreSQL | `elTiempoMaximoCortaUnaConsultaLentaEnPostgresql`: `pg_sleep(5)` con límite de 1 s corta en 1 s con el mensaje de tiempo excedido; falla sin la rama del SQLSTATE 57014 | ídem |
| 404 en el servicio | `OntologyServiceTest.unModuloQueNoExisteLanzaModuleNotFound` | `salidas/ronda2/backend-pruebas.txt` |
| Rechazos conocidos del filtro | Javadoc de `SqlQueryGuard` y línea en el INFORME: paréntesis inicial, `WITH` con columna `insert`/`update`/`delete`/`merge` sin comillas, barra invertida fuera de `E'…'` (comprobados) | — |
| Conteos | README del asistente y README principal con las tres corridas, cada una con su archivo | — |
| `relevar-esquemas.sh` | Línea en el INFORME | — |
| `reiniciar.sh` | Sobre `460f152`: `PRESERVED`, 25 reglas activas, interfaz 200; la columna `rule_changes_json` existe | `salidas/ronda2/reinicio.txt` |
| Límites declarados | Reversión por nombre local y triples con nodos en blanco (README del asistente); `ddl-auto: validate` reemplazado por `update` en Compose (README principal) | — |

Además se corrigió un error propio de la primera ronda: al insertar «Limitaciones del asistente» había
quedado bajo ese título un punto de «Qué no puede afirmar».

### Cierre de la ronda 2

| Comprobación | Resultado | Salida |
|---|---|---|
| Flujo ERP (`run.sh`, `protys-e2e`, `460f152`) | 24 de 24 APROBADO; ADempiere 348 triples, Odoo 201; entrada 4318 → 4867; inferidos 1586; Q04 +15, Q20 +20, las otras 19 iguales; registrado `commit=460f152…` sin cambios pendientes | `salidas/ronda2/erp-e2e/RESUMEN.txt` |

### Qué no se hizo en esta ronda
- No se cambió la reversión por nombre local, el registro de triples con nodos en blanco ni
  `application-docker.yml`: quedan como límites declarados.
- La conexión del conector sigue sin poder impedir `BEGIN READ WRITE` en una consulta de varias sentencias;
  queda escrito, con el rol como garantía.
- Los conteos de los README se actualizaron en un commit de documentación posterior a las corridas
  (no cambia código ni salidas).

## Ronda 3 (18-11-2026)

Tercera revisión, sobre la ronda 2 (`df79352..769dbf8`): un punto importante y tres menores. La evidencia de
cierre se generó con el árbol versionado limpio, sin archivos sin seguimiento (`425f270`), y está en
`salidas/ronda3/`. Las salidas de las rondas anteriores no se tocaron.

### A. El filtro de SQL todavía se salteaba

**Comprobación previa, textual.** Antes del arreglo, en el PostgreSQL desechable de `solo-lectura.sh`
(15.19; la revisión había usado 15.10), con un usuario que puede escribir, se pasó cada entrada por el
filtro y después por la conexión del conector, con `executeQuery` como el procesador
(`salidas/ronda3/comprobacion-salteos.txt`, commit `3435bc0`):

```
SALTEO A [SELECT 1 AS a_$x$ ; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 1 AS b_$x$] filtro -> aceptada; executeQuery por la conexión del conector -> rechazada, SQLSTATE 0100E: Multiple ResultSets were returned by the query.; filas en prueba=1
SALTEO B [SELECT $é$ ' $é$ ; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 1 --'] filtro -> aceptada; executeQuery por la conexión del conector -> rechazada, SQLSTATE 0100E: Multiple ResultSets were returned by the query.; filas en prueba=1
SALTEO C [SELECT 1 --\r; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 2] filtro -> aceptada; executeQuery por la conexión del conector -> rechazada, SQLSTATE 0100E: Multiple ResultSets were returned by the query.; filas en prueba=1
```

Las tres se aceptaban y escribían, como decía la revisión. (En la corrida de cierre esa comprobación
se repite: el filtro las rechaza; la línea «filas en prueba=1» sigue apareciendo porque la comprobación
manda la consulta a la conexión sin pasar por el filtro.)

**Cambios** (`675a0c0`):
- `SqlQueryGuard.check` cuenta las sentencias con el divisor de pgjdbc,
  `org.postgresql.core.Parser.parseJdbcSql(sql, scs, false, true, false, true)`, y rechaza si no da
  exactamente una. Firma confirmada con `javap` sobre pgjdbc 42.6.0 (la del `pom.xml`). Se cuenta con
  `standard_conforming_strings` activo y apagado y vale el mayor, para no depender del servidor; esto
  va un poco más allá de lo pedido (`true` solo). El divisor da 6 para A, B y C y 1 para `SELECT 1`,
  `SELECT 1;`, `SELECT 1; `, `SELECT ';' AS a, $t$ ; $t$ AS b /* ; */ -- ;`, la CTE y
  `(SELECT 1) UNION (SELECT 2)`, así que no hizo falta cambiar el orden con el `;` final. Se aplica
  siempre, también con H2: la suite W3C sigue en 63 de 63.
- `postgresql` pasó de alcance `runtime` a `compile` en el `pom.xml`, porque el filtro usa una clase
  del driver.
- Analizador propio, para que el rechazo no dependa sólo del divisor y diga el motivo correcto:
  - antes de `$` y de `E'`, carácter de identificador = letra, dígito, `_`, `$` o carácter ≥ U+0080;
  - etiqueta de dólar: vacía, o empieza con letra, `_` o carácter ≥ U+0080 (nunca con dígito);
  - `--` termina en `\n` o en `\r`.
- **Además, no pedido:** `ERPConnectorService.openReadOnly` rechaza una URL con
  `preferQueryMode=simple` o `extendedForPrepared`, por el resultado del punto A.4 (abajo): en esos
  modos el control del divisor deja de tener respaldo en el servidor.

**Pruebas.**
- `SqlQueryGuardTest` (26): A, B y C rechazadas; el analizador propio ya deja el `;` a la vista en las
  tres; el espacio duro pegado a `E'` y `$1$` rechazados; los cinco casos válidos aceptados, y el de
  paréntesis rechazado por la regla de la primera palabra, como dice la prueba.
  `elDivisorDePgjdbcSigueComportandoseComoSeVerifico` falla con un mensaje que nombra
  `parseJdbcSql` si el driver cambia de comportamiento; si cambia la firma, no compila.
- `ERPReadOnlyConnectionIntegrationTest` (13):
  - `losSalteosSeRechazanAlAnalizarElMapeoYNoEscriben`: mapeo R2RML con cada salteo como
    `rr:sqlQuery`, por `R2RMLProcessor.parse`; se rechaza y `prueba` queda sin filas.
  - Comprobado en una copia sin commitear: sacando el divisor y el arreglo correspondiente del
    analizador, la prueba falla en los tres casos con `escribió con: … expected: <0> but was: <1>`.
  - `laConexionDelConectorRechazaLosModosQueMandanVariasSentenciasJuntas`.
- Salidas: `salidas/ronda3/solo-lectura-pgjdbc.txt` (13 de 13) y `salidas/ronda3/backend-pruebas.txt`
  (W3C 63 de 63).

**A.4: el supuesto que sostiene el control.** Se armó una cadena que el divisor, con
`standard_conforming_strings` apagado, cuenta como una sentencia, y que el servidor (con esa opción
activa) ve como dos. Se la mandó en un único mensaje por la API de bajo nivel de pgjdbc
(`QueryExecutor.wrap` y `execute`, con `QUERY_SUPPRESS_BEGIN` y autocommit activo), con un usuario
que puede escribir (`comprobacionUnParseConDosSentencias`, `salidas/ronda3/solo-lectura-pgjdbc.txt`):

```
PARSE extendido [SELECT 'a\'; INSERT INTO prueba VALUES (99) --'] sentencias según el divisor (scs=off)=1; standard_conforming_strings del servidor=on -> rechazada, SQLSTATE 42601: ERROR: cannot insert multiple commands into a prepared statement; filas en prueba=0
PARSE simple [SELECT 'a\'; INSERT INTO prueba VALUES (99) --'] sentencias según el divisor (scs=off)=1; standard_conforming_strings del servidor=on -> aceptada; filas en prueba=1
```

- En el protocolo extendido, que pgjdbc usa por defecto, el servidor rechaza el `Parse` con dos
  sentencias y no escribe. Es lo que dice la documentación del protocolo de PostgreSQL, en la sección
  «Extended Query»: la cadena de un mensaje Parse no puede tener más de una sentencia SQL.
- En el protocolo simple (`preferQueryMode=simple`) la misma cadena escribe. Por eso la conexión del
  conector ahora exige el modo extendido.
- **Un tropiezo propio, corregido antes de guardar:** el primer intento de esta comprobación no pasaba
  `QUERY_SUPPRESS_BEGIN`, así que el ejecutor de bajo nivel abría un `BEGIN` que nadie confirmaba. Por eso
  el modo simple mostraba «aceptada» sin filas. Con el registro de sentencias de un PostgreSQL aparte
  se vio que el servidor sí había ejecutado las dos sentencias y que se deshacían al cerrar.

**Redacción** (`ab13a07` y el Javadoc en `675a0c0`). El INFORME de ERP («Sólo lectura: dos garantías
distintas») ahora dice:
- el filtro tuvo cuatro huecos, todos por diferencias entre su analizador y el de PostgreSQL: uno en
  la ronda 2 y tres en la 3;
- ahora hay además un control con el divisor del driver;
- ese control se apoya en el protocolo extendido.

Se mantiene lo que ya estaba bien: el filtro es un control de forma, la conexión no puede impedir un
`BEGIN READ WRITE` explícito y la garantía que no depende de la consulta es el rol `protys_ro`. Los
Javadoc de `SqlQueryGuard` y `ERPConnectorService` dicen lo mismo.

### B. Menores (`11cdec5`)

| Punto | Qué se hizo | Respaldo |
|---|---|---|
| `solo-lectura.sh` y archivos sin seguimiento | El encabezado se calcula al empezar con `git status --porcelain --untracked-files=all`, que respeta `.gitignore`, antes de crear el archivo de salida. Comprobado con `backend/comprobacion-sin-seguimiento.txt` sin `git add`: marcó `11cdec5+cambios-sin-commit` | `salidas/ronda3/solo-lectura-archivo-sin-seguimiento.txt` (commit `425f270`) |
| Zona horaria | El comentario de `R2RMLProcessor`, el Javadoc de la prueba y las dos filas de este informe dicen que se conserva el instante, no el desplazamiento escrito (`10:15:30-03` → `13:15:30Z`). El nombre de la prueba (`…ConservaElDesplazamiento`) no se cambió: la fila lo aclara | — |
| Commits de dos salidas de la ronda 2 | Línea en la sección «Ronda 2»: `comprobacion-varias-sentencias.txt` y `comprobacion-configuraciones.txt` llevan `df79352` y `3eea75e` porque se corrieron a propósito antes del arreglo | — |

### Cierre de la ronda 3

| Comprobación | Resultado | Salida |
|---|---|---|
| Sólo lectura (`solo-lectura.sh`) | 13 de 13 | `salidas/ronda3/solo-lectura-pgjdbc.txt` |
| Backend | 272 pruebas, 0 fallas, 13 salteadas (las de PostgreSQL desechable); W3C 63 de 63; `SqlQueryGuardTest` 26 | `salidas/ronda3/backend-pruebas.txt` |
| Interfaz | 75 de 75 | `salidas/ronda3/frontend-pruebas.txt` |
| Build | compila; Node muestra el aviso de obsolescencia `fs.F_OK` | `salidas/ronda3/frontend-build.txt` |
| Scripts | 24 de 24 | `salidas/ronda3/scripts-pruebas.txt` |
| Evaluación | 16 de 16 | `salidas/ronda3/evaluacion-pruebas.txt` |
| Flujo ERP (`run.sh`, `protys-e2e`, `01c10c0`: el código de `425f270` más las salidas de cierre) | 24 de 24; 348 + 201 triples, entrada 4318 → 4867, inferidos 1586, Q04 +15, Q20 +20; los mapeos pasan el filtro sin cambios | `salidas/ronda3/erp-e2e/RESUMEN.txt` |

Los conteos del README principal y del README del asistente se actualizaron en un commit de documentación
posterior a las corridas (no cambia código ni salidas).

### Qué no se hizo en esta ronda
- La conexión del conector sigue sin poder impedir un `BEGIN READ WRITE` explícito por sí sola. Lo
  frenan tres cosas: el filtro, que tiene dos controles; el servidor, en el protocolo extendido; y el rol.
- No se renombró la prueba `timestamptzDePostgresqlConservaElDesplazamiento` (se renombró en la ronda 4).
- El divisor es una API interna de pgjdbc: al actualizar el driver hay que volver a correr
  `SqlQueryGuardTest`.
