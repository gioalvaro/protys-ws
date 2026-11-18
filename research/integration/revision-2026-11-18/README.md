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
| `TIMESTAMP WITH TIME ZONE` | `xsd:dateTime` con desplazamiento; también `timestamptz` de pgjdbc, que se informa como TIMESTAMP | ídem |
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
