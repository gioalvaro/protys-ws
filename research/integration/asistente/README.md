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
`52f229e`, backend en 127.0.0.1:18080). La plataforma cargó 1 módulo y 22 reglas, todas activas.
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
  respuestas en las 21 consultas.

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
  - Si un completado se corta a mitad, cancelar quita el módulo de datos y su grafo.
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
- **Cancelar** borra también el grafo temporal de reglas.
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
- Imágenes reconstruidas desde el commit `e564ec8`; el mismo Compose fija `postgres:15` por digest.
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

**Reiniciar después de incorporar (encontrado, no corregido).** Con el piloto ya incorporado, volver a
correr `docker compose up -d` hace fallar `protys-init`: «Foreign or incomplete module metadata; no state
was modified» (`salidas/fase3b/reinicio/reinicio.txt`).
- La causa: `docker/load_canonical.py` exige que en la base estén sólo el módulo y las 22 reglas de la
  carga inicial.
- La carga inicial no modifica nada, y el backend y la interfaz siguen funcionando; pero `docker compose
  up` devuelve código 1.
- Cambio mínimo posible: que `verify_existing` acepte módulos y reglas incorporados por el asistente
  (por ejemplo, módulos con grafo bajo `…/incorporated/`) además de los suyos.
- No se tocó, por decisión del 2026-11-16.

## Qué puede afirmar la tesis (actualizado tras la Fase 3b)
- El asistente incorpora el fragmento AP242 con sus reglas de vínculo R27–R29 y los datos del piloto, por
  la API y por la interfaz.
- El resultado coincide con la incorporación por programa (`h3_extended`) en lo comprobado:
  - las mismas 25 reglas activas;
  - red consistente;
  - las 21 consultas del catálogo sin cambios;
  - los 8 controles H3 comparables iguales a lo esperado.
- Esto vale **con los cambios de la Fase 3b** (commits `10d01ba`, `752d190`, `31cbc3a`, `e564ec8`), no
  con el asistente evaluado en la versión de la tesis.

## Qué no puede afirmar
- Que el control `H3_pre_extension_no_classification` se reprodujo en la plataforma.
- Que la plataforma se puede reiniciar sin intervención después de incorporar un estándar (ver arriba).
- Que el asistente de la versión evaluada (`tesis-cierre-2026-10-03-v1`) podía hacer esta
  incorporación: no podía (Fase 3).
