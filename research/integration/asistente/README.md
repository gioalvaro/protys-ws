# Evidencia E-ASI: incorporar el fragmento de ISO 10303 con el asistente

**Resultado (actual, Fase 3b, 2026-11-17).** Con los cambios mínimos 1–4 de la lista de abajo, el
asistente **completa** la incorporación del piloto ISO 10303 y da lo mismo que la incorporación por
programa (`h3_extended`). Se probó por la API y por la interfaz, partiendo cada vez de la plataforma
limpia en configuración integrada.
- **Reglas.** Quedan 25 reglas activas: las 22 más R27–R29, igual que `h3_extended`.
- **Consistencia.** La red es `CONSISTENT` antes y después.
- **Catálogo.** Las 21 consultas devuelven exactamente lo mismo.
- **Controles del piloto.** Los 8 controles `H3_*` comparables (g30) dan lo esperado.
- **Control que no es equivalente.** El noveno, `H3_pre_extension_no_classification` (g31), no tiene
  equivalente en la plataforma: se ve abajo por qué.

Detalle en la «Bitácora de la Fase 3b», al final.

**Resultado anterior (Fase 3, 2026-11-16), que queda como antecedente.** Con el asistente de entonces no
se podía completar la incorporación. El fragmento pasaba la carga y la validación, pero el asistente no
permitía registrar R27–R29 ni cargar los datos del piloto. La sesión se canceló y se comprobó que la red
quedó igual. Lo que sigue hasta la bitácora describe ese estado.

## Qué es la incorporación por programa
La configuración `h3_extended` de `research/catalog.json` es la integrada (11 TBox, `functional.ttl`,
22 reglas) más tres cosas:
- `ontologies/iso10303-ap242-fragment.owl` (TBox);
- `ontologies/iso10303-alignment-rules.owl`, con las reglas R27, R28 y R29;
- `research/data/automotive.ttl`, con los datos del piloto.

Sus controles son los 9 `H3_*` de `research/release/functional.json` (por ejemplo, misma identidad de
los componentes, clases de origen conservadas y casos sin marca de piloto que no se clasifican).

## Cómo se probó
Plataforma levantada en configuración integrada (proyecto Compose `protys`, imágenes del commit
`14c7ac1`, backend en 127.0.0.1:18080). La plataforma cargó 1 módulo y 22 reglas, todas activas.
- **Por la API:** `secuencia.sh` → `salidas/api/`, con una respuesta por llamada y `RESUMEN.txt`.
- **Por la interfaz:** Chrome, en `salidas/interfaz/`, con una captura por pantalla.

| Paso | API | Interfaz | Resultado |
|---|---|---|---|
| 0 Línea de base | `00-*` | — | 1 módulo, 22 reglas, razonamiento `CONSISTENT`, 21 consultas guardadas |
| 1 Subir el fragmento | `01-paso1-subir.json` (HTTP 201, 46 triples) | `1-paso1-subir.jpg` | Funciona |
| 2 Validar consistencia | `02-paso2-validar.json` | `2-paso2-validar.jpg`, `3a-…` | Funciona: la red más el fragmento es consistente |
| 3 Reglas R27–R29 | `03-reglas-piloto-disponibles.txt`; `03-paso3-pedir-R27.json` (HTTP 422: «Select registered rule IDs; new global rules cannot be created by a session») | `3b-…`, `3c-paso3-fin-de-lista-sin-R27-R29.jpg`: la lista ofrece sólo las 22 reglas registradas y no hay forma de agregar otras | **No alcanza** |
| Datos del piloto | `06-paso1-subir-datos-piloto.json` (HTTP 422: «Content is not allowed in prolog») | — | **No alcanza** |
| 4 Cancelar | `04-cancelar.json` (HTTP 200) | `4-cancelada-vuelve-al-paso1.jpg` | Las 3 sesiones del día quedan `CANCELLED` (`salidas/interfaz/sesiones.txt`) |
| 5 Red sin cambios | `05-*`, `05-catalogo-diferencias.md` | `salidas/interfaz/grafos-despues-de-cancelar.csv` | Mismos módulos y grafos; consistencia `CONSISTENT` → `CONSISTENT`; las 21 consultas son idénticas |

La primera corrida de `secuencia.sh` se cortó en el paso 3 por un error del propio script; su sesión
se canceló a mano antes de volver a correrla. Es la primera fila de `sesiones.txt`.

## Qué falta (huecos), con el cambio mínimo propuesto

1. **El paso 3 no puede incorporar reglas nuevas.** Sólo elige entre reglas ya registradas, y el
   servicio lo rechaza a propósito (`StandardIncorporationService.step3_defineAlignments`). R27–R29 no
   están registradas, porque la plataforma carga sólo las 22 de la configuración integrada.
   *Cambio mínimo:* que el paso 3 acepte, además de la selección, un archivo de reglas SWRL de la
   sesión, guardado en un grafo temporal propio. El paso 4 razonaría con las reglas elegidas más las de
   la sesión. Al completar, se registran y sus definiciones se **suman** al grafo de reglas.

2. **No hay otro camino seguro para registrar esas reglas.** `POST /api/alignment/rules/upload`
   (`AlignmentService.loadAlignmentRules`) **reemplaza** el grafo de definiciones completo (`putModel`).
   Subir ahí `iso10303-alignment-rules.owl` borraría las definiciones de R01–R26, que seguirían
   registradas en la base. Es un defecto aparte, y por eso no se usó.
   *Cambio mínimo:* sumar las definiciones al grafo (`add` en vez de `put`) y rechazar identificadores
   ya registrados con definición distinta.

3. **Los datos del piloto no entran por el asistente.**
   - El paso 1 lee sólo RDF/XML (`Lang.RDFXML`). Sin embargo, el controlador acepta `.ttl` y la interfaz
     anuncia «RDF, OWL, TTL, N3, or XML files»: un `.ttl` falla con «Content is not allowed in prolog».
   - Además, cada sesión admite un único archivo y lo trata como el estándar. Sin `automotive.ttl` no
     se pueden evaluar los 9 controles `H3_*`.
   *Cambio mínimo:*
   - elegir el lector según la extensión (`RDFLanguages.filenameToLang`), lo que además corrige la
     contradicción con lo que anuncia la interfaz;
   - permitir en la sesión un segundo archivo opcional de datos de instancia, validado junto con el
     estándar y guardado como su propio grafo al completar.

4. **Completar no activa las reglas** (según el código; no se ejecutó). `completeIncorporation`
   registra el módulo, pero no cambia la activación de las reglas. Aunque los huecos 1–3 se
   resolvieran, el razonamiento de la plataforma no correría R27–R29 hasta activarlas a mano en la
   pantalla de alineamiento.
   *Cambio mínimo:* al completar, activar las reglas verificadas en el paso 4, o pedirlo de forma
   explícita en la pantalla 5.

5. **Comprobación después de incorporar.** Con 1–4 resueltos, faltaría comparar contra
   `functional.json`:
   - las 21 consultas antes y después de completar, que se puede hacer con `secuencia.sh` y
     `diferencias-catalogo.py`;
   - los 9 controles `H3_*`. Hoy los corre el evaluador académico (`research/evaluation/`), no la API
     de la plataforma, así que haría falta escribir sus equivalentes como consultas sobre la plataforma.

## Qué podía afirmar la tesis con el asistente de la Fase 3 (antecedente)
- El asistente carga el fragmento AP242 en una sesión aislada y verifica que la red sigue consistente
  con él.
- Cancelar una sesión deja la red como estaba: mismos módulos y grafos, misma consistencia y mismas
  respuestas en las 21 consultas. Esto se probó con sesiones que no llegaron a completar. Con la
  Fase 3b, completar empezó a cambiar las reglas globales; hasta la revisión del 2026-11-18, cancelar un
  completado cortado a mitad no deshacía esos cambios (ver «Revisión del 2026-11-18» más abajo).

## Qué no podía afirmar con el asistente de la Fase 3 (antecedente)
- Que el piloto ISO 10303 se incorporó con el asistente: las reglas de vínculo y los datos del piloto
  no entran por él.
- Que el asistente acepta Turtle o N3, como dice su pantalla.

## Bitácora de la Fase 3b: completar el asistente
Decisión (2026-11-16): implementar los cambios mínimos 1–5 y volver a recorrer el asistente con el
piloto. Lo de arriba queda como evidencia del estado anterior. Cada paso se anota acá al terminarlo.

### Paso 1 — Subida de reglas que suma en vez de reemplazar (hueco 2)
- **Qué se cambió.** `AlignmentService.loadAlignmentRules` ya no hace `putModel` del archivo subido sobre
  el grafo de definiciones. Ahora lee el grafo y lo combina con un ayudante nuevo,
  `mergeRuleDefinitions`, que trata cada `swrl:Imp` con todo lo que cuelga de ella (`ruleDefinition`):
  - si la regla no estaba, la suma;
  - si estaba con una definición isomorfa, no la vuelve a sumar (reintentar no duplica las listas RDF);
  - si estaba con otra definición, la rechaza sin tocar el grafo ni la base.
  El mismo ayudante lo usa el paso 3.
- **Prueba.** `AlignmentRuleUploadTest`, en Java 17:
  `mvn17.sh -q test -Dtest=AlignmentRuleUploadTest,AlignmentDefinitionTest`.
  - Con el cambio pasan 3 de 3.
  - Con el `putModel` anterior fallan 2: se pierde R01 al subir R27, y se acepta otra definición de R27.
    La prueba de no duplicar pasa igual, porque reemplazar tampoco duplica.

### Paso 2 — Paso 1 del asistente por extensión y datos de instancia opcionales (hueco 3)
- **Lectura por extensión.** El paso 1 elige el lector con `RDFLanguages.filenameToLang`; si la
  extensión no se reconoce, usa RDF/XML como antes. El controlador acepta lo que anuncia la pantalla:
  `.owl`, `.rdf`, `.xml`, `.ttl`, `.n3` y `.nt`.
- **Datos de instancia.** Nuevo `POST /api/wizard/step1/data/{id}`. Guarda el archivo en
  `…/temp/wizard/<id>/data` y anula la validación anterior; los pasos 2 y 4 razonan con el estándar más
  esos datos.
- **Al completar.** Los datos se copian a `…/incorporated/<id>/data` y se registran como un módulo
  propio, «<estándar> — datos de instancia», sin `incorporation_session_id` porque esa columna es única.
  - El módulo de datos se registra **antes** que el del estándar: así un reintento sigue reconociendo
    el estándar como marca de completado.
  - Si un completado se corta a mitad, cancelar quita el módulo de datos y su grafo. Desde la revisión
    del 2026-11-18 también deshace los cambios en las reglas globales: borra las reglas que el
    completado creó (registro y definición), vuelve a inactivas las que activó y saca los triples que
    sumó al grafo de reglas (`WizardSessionRulesTest.cancelarUnCompletadoCortadoDejaLasReglasComoEstaban`,
    salida en `../revision-2026-11-18/salidas/backend-pruebas.txt`). Antes de esa revisión quedaban
    activas.
- **Prueba.** `WizardInstanceDataTest`, con 6 pruebas nuevas:
  - Turtle en el paso 1;
  - datos en su grafo y dentro de la validación;
  - subir datos anula la validación;
  - completar crea dos módulos y limpia los grafos temporales;
  - cancelar borra los datos;
  - cancelar un completado cortado a mitad.

  Corrida: `mvn17.sh -q test -Dtest='Wizard*Test,StandardWizardControllerTest'`. Pasan 39 de 39: las 6
  nuevas, las 3 heredadas que se repiten en la clase nueva y las 30 que ya existían.

### Paso 3 — Reglas de la sesión, paso 4 y completar que activa reglas (huecos 1 y 4)
- **Reglas de la sesión.** Nuevo `POST /api/wizard/step3/rules/{id}`.
  - Recibe un archivo SWRL y lo guarda en `…/temp/wizard/<id>/rules`.
  - Rechaza identificadores ya registrados, nombres que no se pueden ejecutar, repetidos y archivos sin
    `swrl:Imp`.
  - Anula una verificación anterior.
  - La selección de reglas registradas (`step3/alignments`) sigue igual.
- **Paso 4.** Razona con las reglas seleccionadas más las de la sesión: sus definiciones se combinan con
  `mergeRuleDefinitions`, así un reintento no las duplica. El comprobante lista las dos; las de la
  sesión todavía sin id.
- **Completar.** Antes de registrar el módulo del estándar (la marca de completado):
  - suma las definiciones nuevas al grafo de reglas, con el ayudante del paso 1;
  - registra las reglas de la sesión;
  - deja activas **todas las reglas verificadas** en el paso 4;
  - guarda el comprobante con los ids definitivos.
  El resultado trae `activatedRules`. Todo se puede repetir sin efectos dobles.
- **Cancelar** borra también el grafo temporal de reglas. (Revisión del 2026-11-18: si el completado se
  cortó después de activar reglas, cancelar además deshace esa activación; ver arriba.)
- **Prueba.** `WizardSessionRulesTest`, con 7 pruebas nuevas:
  - grafo temporal sin registro previo;
  - rechazos;
  - el paso 4 corre R01+R27;
  - cargar reglas obliga a verificar de nuevo;
  - completar registra, suma y activa;
  - reintentar no duplica;
  - cancelar no registra nada.
- **Suite completa en Java 17:** 218 pruebas, 0 fallas (antes de la Fase 3b eran 196). Salida en
  `salidas/fase3b/pruebas-backend.txt`.

### Paso 4 — Interfaz del asistente
- **Paso 1.** Campo opcional «Instance data (optional)». Se sube después del estándar, a la misma
  sesión (`step1UploadData`). Si falla, la sesión queda marcada para cancelar, igual que una subida
  incompleta.
- **Paso 3.** Campo «Session rules (SWRL)», que llama a `step3UploadRules` y muestra las reglas cargadas.
- **Comprobante del paso 4.** Antes exigía que toda regla ejecutada tuviera id; ahora acepta:
  - las reglas registradas, con id e iguales a la selección;
  - las reglas de la sesión, sin id e iguales a las cargadas.
  Si falta una regla de la sesión, no deja completar.
- **Paso 5.** Lista «Active after completion». Al completar, el asistente vuelve al paso 1, como antes y
  como esperan las pruebas existentes, y muestra un aviso con las reglas que quedaron activas
  (`activatedRules`).
- **Límite conocido.** Las reglas de la sesión no se guardan en el navegador. Si la página se recarga a
  mitad de la sesión, hay que volver a cargarlas en el paso 3: la nueva carga reemplaza a la anterior
  en el servidor.
- **Prueba.** `WizardFlow.test.jsx`, con 4 pruebas nuevas:
  - los datos van a la misma sesión;
  - si fallan los datos, la sesión queda para cancelar;
  - las reglas de la sesión se verifican y se listan como activas;
  - un comprobante sin la regla de la sesión no deja completar.

  Suite de interfaz: 14 archivos, 69 pruebas, todas pasan (antes eran 65). Salida en
  `salidas/fase3b/pruebas-interfaz.txt`.

### Paso 5 — Recorrido completo por la API y la interfaz
**Plataforma.**
- Imágenes reconstruidas desde el commit `6d33abb`; el mismo Compose fija `postgres:15` por digest.
- Proyecto `protys` en puertos alternativos: backend 127.0.0.1:18080, interfaz 18081, Fuseki 13030.
- Antes de cada recorrido se borraron sólo `protys_fuseki-data` y `protys_postgres-data`, así la carga
  inicial dejó la configuración integrada limpia: 1 módulo (4318 triples) y 22 reglas activas.
- No se usó `down -v`, para no tocar los volúmenes de los ERP.

**Por la API.** `incorporar.sh` → `salidas/fase3b/api/` (una respuesta por llamada, `RESUMEN.txt`).

| Paso | Resultado |
|---|---|
| 0 Línea de base | 1 módulo, 22 reglas (22 activas), razonamiento `CONSISTENT` |
| 1 Subir el fragmento AP242 | HTTP 201, 46 triples |
| 1b Subir `automotive.ttl` como datos de la sesión | HTTP 201, 203 triples |
| 2 Validar red + fragmento + datos | `CONSISTENT` |
| 3a Cargar R27–R29 como reglas de la sesión | HTTP 201: R27, R28, R29 |
| 3b Seleccionar las 22 reglas registradas | HTTP 200, 22 |
| 4 Verificar | 25 reglas ejecutadas (22 + 3); 1419 triples inferidos |
| 5 Completar | HTTP 201: módulo del estándar, módulo de datos y 25 reglas activas |
| 6a Reglas activas en la plataforma | 25, R27–R29 incluidas, como `h3_extended` |
| 6b Razonamiento global | `CONSISTENT` → `CONSISTENT`; entrada 4318 → 4662 triples; inferidos 1167 → 1419 |
| 6c Las 21 consultas del catálogo | idénticas (`06-catalogo-diferencias.md`) |
| 6d Controles `H3_*` | los 8 de g30 iguales a lo esperado (`06-controles-H3.md`) |

- **Grafos.** Después de completar no quedan grafos temporales.
- **Grafo de reglas.** Pasó de 1374 a 1469 triples (+95, las definiciones de R27–R29) y conserva las
  anteriores. Es el arreglo del paso 1 funcionando en la plataforma real.
- **Controles H3, antes y después.** Antes de incorporar, los controles g30 que esperan `true` dan
  `false`, porque sin el piloto no hay datos. Después dan lo esperado. Los controles comparan con la
  evaluación por programa (`research/evaluation/current/assertion-groups/g30`, resumida en
  `research/release/functional.json`).

**Por la interfaz.** Chrome, plataforma limpia de nuevo. Capturas en `salidas/fase3b/interfaz/`:
1. `1-paso1-estandar-y-datos.jpg`: el fragmento y `automotive.ttl` en el paso 1.
2. `2a-paso2-subido.jpg` y `2b-paso2-validado-paso3.jpg`: validación aprobada.
3. `3-paso3-reglas-sesion-y-22-seleccionadas.jpg`: «Loaded session rules: R27, R28, R29» y las 22
   casillas marcadas.
4. `4a-paso4-seleccion-configurada.jpg` y `4b-paso4-verificado-reglas-de-sesion.jpg`: R27–R29 como
   «session rule» y la lista «Active after completion».
5. `5a-paso5-crear.jpg` y `5b-completado-reglas-activas.jpg`: «Last incorporation:
   iso10303-ap242-fragment» y «Rules active after completion (25)».

Comprobación posterior en `salidas/fase3b/interfaz/api-despues/RESUMEN.txt`:
- tres módulos, entre ellos «iso10303-ap242-fragment — datos de instancia», con 203 triples;
- 25 reglas activas;
- razonamiento `CONSISTENT`, con 4662 triples de entrada y 1419 inferidos;
- ningún grafo temporal;
- 0 de 21 consultas cambian contra la línea de base de la corrida por la API;
- los 8 controles g30 iguales a lo esperado.

Es el mismo resultado que por la API. La lectura de la consola de Chrome no mostró errores, pero empezó
después de cargar la página, así que no cubre la carga.

**Nombre del módulo.** La interfaz toma el nombre del estándar del nombre del archivo
(`iso10303-ap242-fragment`); la API lo recibe explícito.

**Lo que no es equivalente.** `H3_pre_extension_no_classification` (g31) se evalúa en la configuración
integrada **con** los datos del piloto **sin** el fragmento AP242. En la plataforma, antes de incorporar
no hay datos del piloto, así que da `false` (lo esperado) de forma trivial. Después de incorporar da
`true`, y es lo correcto: con la extensión, `Component00` sí se clasifica. No se cuenta como control
reproducido.

**Reiniciar después de incorporar (encontrado el 2026-11-16, corregido el 2026-11-17).** Con el piloto ya
incorporado, volver a correr `docker compose up -d` hacía fallar `protys-init`: «Foreign or incomplete
module metadata; no state was modified» (`salidas/fase3b/reinicio/reinicio.txt`). Como la interfaz espera
a que `protys-init` termine bien, tampoco volvía a levantar.
- Causa: `docker/load_canonical.py` exigía que en la base estuvieran sólo el módulo y las 22 reglas de la
  carga inicial.
- Arreglo (pulido, paso 1): `verify_existing` conserva lo que la plataforma registró por su API después
  de la carga inicial:
  - módulos registrados, cada uno con su grafo (asistente, explorador);
  - reglas registradas que tengan definición en el grafo de reglas;
  - grafos de conectores ERP (`…/protys/erp/`) y de sesiones abiertas del asistente.
- Sigue rechazando:
  - grafos que nadie registró;
  - definiciones de reglas sin registro y reglas registradas sin definición;
  - el módulo canónico cambiado;
  - cualquiera de las 22 reglas canónicas cambiada o sin registrar.
- Pruebas: 12 en `scripts/tests/test_load_canonical.py`, sin Docker.
- Comprobación real: `reiniciar.sh` para y vuelve a levantar todo sobre la plataforma con el piloto
  incorporado. `protys-init` responde `PRESERVED` (2 módulos y R27–R29 agregados), quedan 25 reglas
  activas y la interfaz responde 200 (`salidas/fase3b/reinicio/reinicio-tras-arreglo.txt`).

## Qué puede afirmar la tesis (actualizado tras la Fase 3b)
- La plataforma se reinicia sin intervención después de incorporar (con el arreglo del 2026-11-17).
- El asistente incorpora el fragmento AP242 con sus reglas de vínculo R27–R29 y los datos del piloto, por
  la API y por la interfaz.
- El resultado coincide con la incorporación por programa (`h3_extended`) en lo comprobado:
  - las mismas 25 reglas activas;
  - red consistente;
  - las 21 consultas del catálogo sin cambios;
  - los 8 controles H3 comparables iguales a lo esperado.
- Esto vale **con los cambios de la Fase 3b** (commits `1f5b909`, `fe739b1`, `89e201c`, `6d33abb`), no
  con el asistente evaluado en la versión de la tesis.

## Qué no puede afirmar
- Que el control `H3_pre_extension_no_classification` se reprodujo en la plataforma.

## Limitaciones del asistente
- **Efecto global al completar.** Completar deja activas todas las reglas verificadas en el paso 4,
  también las registradas que estaban inactivas a propósito: quedan activas para toda la plataforma,
  no sólo para el estándar incorporado. Desde la revisión del 2026-11-18 el paso 4 las lista
  (`reactivatedRules`) y la pantalla final las muestra como «Platform-wide change» antes de confirmar;
  el resultado del completado repite la lista. Pruebas: `WizardSessionRulesTest.elPaso4AvisaQueReglasInactivasVaAReactivar`
  y `Wizard.test.jsx` («rules that completion will reactivate…»), salidas en
  `../revision-2026-11-18/salidas/backend-pruebas.txt` y `frontend-pruebas.txt`.
- Que el asistente de la versión evaluada (`tesis-cierre-2026-10-03-v1`) podía hacer esta
  incorporación: no podía (Fase 3).

## Pulido para compartir (2026-11-17)

Pedido: correr todos los controles, arreglar lo que haga falta y pulir la interfaz para compartir.

### Paso 1 — Reiniciar después de incorporar (`0610397`)
Ver «Reiniciar después de incorporar» más arriba.

### Paso 2 — Recorrida de las 6 pantallas en el navegador y arreglos
Recorrí Dashboard, Explorador, SPARQL, Alineamiento, ERP y Asistente con la plataforma incorporada.

| # | Problema encontrado | Arreglo |
|---|---|---|
| 1 | SPARQL: una tabla de resultados ancha arrastraba la página entera hacia el costado, por debajo de la barra lateral | `min-w-0` en el contenedor principal (`App.jsx`); celdas largas en tres líneas con el texto completo en el título |
| 2 | SPARQL: las variables sin valor se mostraban como `null` | se muestran como «—» |
| 3 | La barra lateral decía «Connected» siempre, aunque el backend no respondiera | consulta `/api/dashboard/health`: «Backend connected», «Backend unreachable» o «Checking backend…» |
| 4 | Dashboard: el gráfico de torta tenía una sola porción («Modules: 3») y el de barras una sola barra | dos gráficos por módulo, con triples y con clases e individuos, a partir de `moduleStats`, que la API ya devolvía |
| 5 | Dashboard: marcas de tiempo crudas, con nanosegundos | `2026-11-17 15:09:28` |
| 6 | Página inexistente con el título «Dashboard» | título «Page not found» |
| 7 | Explorador: al elegir un módulo, el aviso «Select a class…» quedaba centrado en un panel alto, fuera de la vista | el panel ya no se estira y queda fijo arriba |
| 8 | Alineamiento: reglas en orden de carga (R10, R11, R15, R04…) | orden natural (R01…R29) |
| 9 | **Backend**: la expresión SWRL guardada de cada regla era el identificador de un nodo en blanco y la descripción quedaba vacía, aunque las reglas tienen `rdfs:label` | `SwrlText` arma el texto legible (`ProcessOperation(?var_op) ^ … -> hasGHGImpactKgCO2e(?var_op, ?var_ghg)`) y toma la etiqueta como descripción, al subir reglas y al completar el asistente; la interfaz lo muestra plegado |
| 10 | **Backend**: la subida de reglas leía sólo RDF/XML, aunque la interfaz ofrece `.ttl` y `.n3` | lector por extensión, como el asistente |
| 11 | **nginx**: límite de 1 MB por defecto; la interfaz prometía 100 MB y el backend acepta 50 MB | `client_max_body_size 50m` en `/api/` y la interfaz dice «max 50 MB» |
| 12 | **nginx**: `index.html` sin `Cache-Control`; después de actualizar, el navegador seguía con la versión vieja | `Cache-Control: no-cache` (los `.js`/`.css` con hash se siguen guardando un año) |
| 13 | Detalles: iconos emoji mezclados con los de Heroicons; botones sólo con icono y sin nombre accesible; en pantallas angostas la barra lateral tapaba el contenido; textos de ERP y del asistente | iconos Heroicons; `aria-label`; barra lateral superpuesta con fondo que la cierra; textos corregidos |

Pruebas nuevas: `SwrlTextTest` (3), 2 en `AlignmentRuleUploadTest`, 2 en `Dashboard.test.jsx`,
`BackendStatus.test.jsx` (1) y 1 en `Alignment.test.jsx`.
- Backend: 223/223 (antes 218).
- Interfaz: 73/73 (antes 69).
- `npm run build` con `CI=true` compila sin avisos.

### Paso 3 — Controles completos con el código actual (`ecb48d8`)

| Control | Comando | Resultado |
|---|---|---|
| Pruebas de backend (Java 17) | `./mvnw test` | 223/223 |
| Pruebas de interfaz | `npm test -- --watchAll=false` | 73/73 |
| Compilación de producción | `CI=true npm run build` | compila sin avisos |
| Pruebas de scripts (incluye la carga inicial) | `python3 -m unittest discover -s scripts/tests` | 23/23 |
| Pruebas de conservación de salidas | `python3 -m unittest discover -s research/evaluation/tests` | 16/16 |
| Plataforma de cero + asistente por API | volúmenes `protys_fuseki-data` y `protys_postgres-data` borrados; `incorporar.sh` | 12/12 APROBADO (`salidas/fase3b/api/RESUMEN.txt`) |
| Reinicio con el piloto incorporado | `reiniciar.sh` | `PRESERVED`, 25 reglas activas, interfaz 200 (`salidas/fase3b/reinicio/reinicio-tras-arreglo.txt`) |
| Fase 1 de punta a punta (ADempiere y Odoo) | `research/integration/erp/run.sh` | 24/24 APROBADO (`../erp/salidas/e2e/RESUMEN.txt`) |
| Reinicio con grafos ERP materializados | `COMPOSE_PROJECT_NAME=protys-e2e reiniciar.sh` | `PRESERVED` con `erp/adempiere` y `erp/odoo` (`salidas/pulido/reinicio-con-grafos-erp.txt`) |
| Demostración de CI (`verify.yml`, docker-demonstration) | `scripts/demo.py up` + `smoke` + `down --remove-data` | PASS (`salidas/pulido/demo-smoke-report.json`) |

Las salidas regeneradas de `incorporar.sh` y de `erp/run.sh` sólo difieren de las anteriores en marcas
de tiempo, identificadores de sesión y tiempos de ejecución. Los resultados son los mismos.

En la plataforma de cero, las reglas ya muestran su texto SWRL y su etiqueta, también R27–R29, que
registró el asistente (`salidas/pulido/alineamiento-reglas-swrl.jpg`).

**Evaluación funcional de la tesis** (`reproduce.sh --functional`, en `eclipse-temurin:17-jdk` con Python 3,
corrida `research/evaluation/runs/20261117T155256Z-…`): **PASS**, 89 grupos de aserciones `CONSISTENT`.
Comparada con `research/release/functional.json` (`salidas/pulido/funcional-vs-release.md`):
- Consultas, comprobaciones contra respuestas esperadas y aserciones: sin diferencias.
- Cambian sólo los conteos de triples por configuración: entrada +1 y materializados hasta +36.
- Esas diferencias vienen de las propuestas COM-01 (`c9423a5`) y H-56 (`eb80440`). Ya estaban en `main`
  antes de esta rama y tocan `ontologies/`. Esta rama no modifica `ontologies/` (`git log main..HEAD -- ontologies` vacío).
- No se ajustó nada para igualarlas.

### Paso 4 — README del repositorio
- Conexión real con ERP (antes decía que no la había).
- R2RML con el procesador propio: el README nombraba CARML, que ya no está en el `pom.xml`.
- 42 endpoints (antes 39) y los del asistente.
- Cómo levantar la plataforma con Docker Compose, cómo se reinicia y cómo empezar de cero.
- Cantidad de pruebas por suite.
- Tabla con la evidencia de noviembre de 2026.

### Paso 5 — Mensajes de commit
El 2026-11-17 se reescribieron los mensajes de los 30 commits de la rama, para dejar sólo la descripción
del cambio. El código de cada commit quedó igual; lo único que cambió fue una línea de
`salidas/fase3b/versiones.txt`, que describe la herramienta usada para las capturas. Como los
identificadores cambian al reescribir, los que se citan en estos README, en los informes y en las
primeras líneas de las salidas guardadas se reemplazaron por los nuevos. Cada uno sigue apuntando al
mismo árbol de código; ninguna otra parte de las salidas se tocó.
