# Capturas de la interfaz para el Capítulo 5 (18-11-2026)

Capturas tomadas sobre el código de la etiqueta `tesis-mantenimiento-2026-11-18-v1`
(commit `cf1c08bc98818b94a997ca5a63b11b10445c5560`). La etiqueta no se movió; las capturas se commitean después.

## Condiciones comunes

| Dato | Valor |
|---|---|
| Árbol | `git rev-parse HEAD` = `cf1c08b…`, `git status --porcelain` vacío antes de construir y antes de commitear |
| Plataforma (series A y B) | proyecto Compose `protys`, `docker compose -f docker/docker-compose.yml up -d --build` a las 2026-11-18T18:27:33Z, después de borrar sólo los volúmenes `protys_fuseki-data` y `protys_postgres-data`; `protys-init` terminó con código 0 |
| Imagen del backend (y de Fuseki) | `protys-backend:maintenance`, id `sha256:f9273842c56ca5b6822a480dc0790dbd1a9d3ad5525bf0893a2f2ce637fe7900`. La imagen no lleva el commit como etiqueta; se construyó con el árbol en `cf1c08b` y se comprobó que el jar contiene el arreglo de la ronda 4b (`pg_catalog.current_setting` en `ERPConnectorService`) |
| Imagen de la interfaz | `protys-protys-frontend`, id `sha256:8ca6ec88376ba6bafc64bbe182008530ba36fd2378f1c7e545d6d559c9dcc14d`. El `--build` sobre `cf1c08b` reutilizó la caché de Docker, que se invalida si cambia el contenido de `frontend/`: es lo que produce ese árbol |
| Plataforma (serie C) | proyecto `protys-e2e` de `research/integration/erp/run.sh` (commit registrado por el script: `cf1c08b…`); backend `sha256:026c47f800b279f9c5e6ee43af026bfcfbb5770cfbe5229b32a6f955b852b295`, interfaz `sha256:342c032607714d97e301263aa56c7f1565f604c14c473dc9e02d7440e3e05149` |
| URL base | `http://127.0.0.1:18081` (las dos plataformas, una por vez) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, manejado con Playwright; ventana 1440×900, `deviceScaleFactor` 2 (PNG de 2880×1800), zoom 100 %, `colorScheme: light`, sin barra de direcciones ni marcos |
| Espera | red inactiva, sin indicadores de carga y unos segundos más para que se cierren los avisos emergentes; puntero fuera del contenido |

Los guiones de Playwright no se incluyen: tenían rutas de la máquina. Los pasos de cada captura están abajo.

## Serie A — red integrada, sin el piloto

| Archivo | Hora UTC | Pasos |
|---|---|---|
| `A1a-tablero.png` | 18:32:09 | `/` (Dashboard). Muestra módulos (1), clases (128), individuos (165), tripletas (4318), reglas activas (22), ERP conectados (0) y «Backend connected». El tablero **no tiene un conteo de consultas** |
| `A1b-tablero-graficos.png` | 18:32:11 | Dashboard desplazado hasta «Triples per Module»: estado de la API, gráficos y resumen |
| `A2-explorador-jerarquia.png` | 18:37:35 | Ontology Explorer → «Canonical_Catalog_Integrated» → abrir Proceso, Artificial_Process, Recurso y Device; desplazado hasta Proceso |
| `A3-explorador-detalle-clase.png` | 18:35:30 | Ontology Explorer → «Canonical_Catalog_Integrated» → «Device» en «Search Classes» → abrir Recurso → elegir Device (`resource#Device`, superclase Resource) |
| `A4a-consulta-Q06.png` | 18:36:15 | SPARQL Console → Templates → Q06 → agrandar el campo de la consulta con su control de tamaño hasta ver el texto → Execute; vista de la consulta (el texto sigue más abajo) |
| `A4b-consulta-Q06.png` | 18:36:16 | La misma pantalla, al final: «Results (3)» |
| `A5a-consulta-Q04.png` | 18:37:03 | Igual que A4a con Q04 (P1) |
| `A5b-consulta-Q04.png` | 18:37:04 | La misma pantalla, desplazada hasta «Results (40)»: primeras filas |
| `A6-alineamiento-reglas.png` | 18:32:19 | Alignment → desplegar «SWRL» de R01; desplazado hasta «Alignment Rules» |
| `A7-razonamiento-controles.png` | 18:33:40 | Alignment → Execute Reasoning → esperar a que el botón deje de decir «Executing...». Inferidas materializadas 1167, entrada 4318, y los tres controles de registros |
| `A8-erp-conectores.png` | 18:32:21 | `/erp`: «No connectors configured». En `protys` no se registró ningún ERP |

Orden y estado:
- **Q06 depende de las inferencias.** Antes de razonar devolvía 0 filas (comprobado al explorar la pantalla, después de levantar la plataforma y antes de razonar), así que A4 y A5 se tomaron después de A7.
- **A7 fue la segunda ejecución del razonamiento.** La primera, a las 18:32, se capturó mientras corría y se descartó.
- **A2 y A3 se repitieron después de razonar.** En la primera toma, el puntero dejaba una fila resaltada (A2) y el panel de detalle quedaba fuera de la vista (A3). La jerarquía y el detalle salen de los grafos de módulo, que el razonamiento no modifica.

## Serie B — incorporación del piloto ISO 10303 con el asistente

Archivos (los de `research/integration/asistente/incorporar.sh`): `ontologies/iso10303-ap242-fragment.owl` (estándar),
`research/data/automotive.ttl` (datos de instancia) y `ontologies/iso10303-alignment-rules.owl` (R27–R29). Se hizo un
solo recorrido, entre las 18:38:35 y las 18:39:40. En cada paso se guardaron vistas sucesivas de 1440×900, y de
ellas salen estas capturas.

| Archivo | Hora UTC | Pasos |
|---|---|---|
| `B1-paso1-estandar-y-datos.png` | 18:38:35 | `/wizard` → elegir el fragmento AP242 y, en «Instance data (optional)», `automotive.ttl` (antes de «Upload & Continue») |
| `B2-paso2-validado.png` | 18:38:55 | «Upload & Continue» → «Validate & Continue». El resultado del paso 2 se muestra al abrir el paso 3: «✓ OWL profile and consistency checked», con los tres controles de registros |
| `B3a-paso3-reglas-seleccion.png` | 18:39:06 | Paso 3: las 22 reglas registradas, marcadas una por una (22 de 22) |
| `B3b-paso3-reglas-sesion.png` | 18:39:08 | Paso 3, al final: «Session rules (SWRL)» con `iso10303-alignment-rules.owl` cargado y «Loaded session rules: R27, R28, R29» |
| `B4a-paso4-verificado.png` | 18:39:30 | «Configure & Continue» → «Verify & Continue». Pantalla de cierre con «Executed Rules» |
| `B4b-paso4-verificado.png` | 18:39:31 | La misma, desplazada: R27–R29 como reglas de la sesión, «Active after completion» y controles |
| `B5-paso5-confirmar.png` | 18:39:31 | La misma, al final: «Summary» y «Create Ontology», antes de confirmar |
| `B6-completado.png` | 18:39:39 | «Create Ontology»: «Last incorporation: iso10303-ap242-fragment» y las 25 reglas activas |
| `B7-explorador-con-piloto.png` | 18:40:45 | Ontology Explorer → elegir «iso10303-ap242-fragment». La lista de módulos tiene Canonical_Catalog_Integrated, iso10303-ap242-fragment e «iso10303-ap242-fragment — datos de instancia» |

Lo que la interfaz no muestra:
- **Paso 4, triples inferidos.** El paso 4 no muestra un conteo de triples inferidos, sólo las reglas ejecutadas y los controles; no se agregó ningún número.
- **Paso 4, antes de verificar.** La pantalla muestra «CLASSES 0 / PROPERTIES 0» y las reglas por su identificador interno. No se capturó; queda anotado.

## Serie C — conectores ERP (proyecto `protys-e2e`)

Antes de empezar se detuvo `protys` con `stop`, sin `down -v`. Después se corrió
`OUT=/tmp/erp-capturas research/integration/erp/run.sh`, que dio 24 de 24 APROBADO. El script no levanta la interfaz,
así que se agregó con
`COMPOSE_PROJECT_NAME=protys-e2e docker compose -f docker/docker-compose.yml -f docker/integration/docker-compose.erp.yml up -d --no-deps protys-frontend`,
con los mismos puertos; así no se vuelve a correr la carga inicial. Al terminar, `protys-e2e` se detuvo y `protys` se
volvió a levantar.

| Archivo | Hora UTC | Pasos |
|---|---|---|
| `C1-erp-conectores.png` | 18:43:41 | `/erp`: `adempiere-demo` (ADEMPIERE) y `odoo-demo` (ODOO), los dos MATERIALIZED. La tarjeta **no muestra el usuario**; la API (`/api/erp/connectors`) informa `protys_ro` en los dos |
| `C2a-consulta-cruzada.png` | 18:43:52 | SPARQL Console → pegar `research/integration/erp/queries/CRUZADA.rq` → agrandar el campo → Execute; vista de la consulta |
| `C2b-consulta-cruzada.png` | 18:43:54 | La misma pantalla, desplazada hasta «Results (10)»: recursos de los grafos `…/erp/adempiere` y `…/erp/odoo` |

## Estado en que quedó `protys`

- Módulos: Canonical_Catalog_Integrated, iso10303-ap242-fragment e «iso10303-ap242-fragment — datos de instancia».
- 25 reglas registradas, las 25 activas (R27–R29 incluidas).
- Inferidas materializadas: 1167, del último razonamiento, que es anterior a la incorporación. `/api/alignment/reasoning/stats` informa una entrada de 4567 triples.
- No hay conectores ERP registrados.
