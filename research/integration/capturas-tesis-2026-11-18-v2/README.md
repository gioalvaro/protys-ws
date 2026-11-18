# Capturas de la interfaz para el Capítulo 5, v2 (18-11-2026)

Estas capturas reemplazan a cinco de la v1 (`../capturas-tesis-2026-11-18/`, commit `6e28126`). Las demás capturas
de la v1 siguen valiendo, y esa carpeta no se modificó. El código es el de la etiqueta
`tesis-mantenimiento-2026-11-18-v2` (`198de8f`). Frente a la v1 cambian sólo dos cosas, las dos en `frontend/`:
la pantalla de conectores ERP y la tipografía de los campos de código. El backend es el mismo.

## Por qué se rehicieron

- **`C1-erp-conectores.png` de la v1 tiene un defecto de la interfaz, no de los datos.** La pantalla comparaba el
  estado con `'connected'` en minúscula, pero la API devuelve `REGISTERED`, `CONNECTED`, `MATERIALIZED` o `ERROR`.
  Por eso los dos conectores salían en rojo con una cruz, aunque estaban `MATERIALIZED`. Además leía
  `connectionString`, un campo que la API no tiene, y «Connection:» quedaba vacío. El formulario de alta tampoco
  funcionaba: mandaba `type: "sap"` y `connectionString`, y la API respondía 400 `INVALID_ERP_REQUEST`
  (comprobado con el mismo cuerpo contra `POST /api/erp/connectors`). En la corrida de evidencia los conectores
  se registran por la API (`research/integration/erp/run.sh`), así que el defecto no afecta a los resultados.
- **`A4a`, `A5a` y `C2a` de la v1:** Fira Code une «www» en una ligadura, y a resolución completa se lee
  `http:// www .w3.org`. Ahora los campos y bloques de código no usan ligaduras.
- **`C2b`:** se rehizo junto con `C2a`. Muestra «Results (10)» y las diez filas en una sola pantalla.

## Condiciones comunes

Las mismas de la v1:
- Chromium 156.0.8078.4 sin interfaz, manejado con Playwright.
- Ventana 1440×900 con `deviceScaleFactor` 2, que da PNG de 2880×1800.
- Zoom 100 % y `colorScheme: light`.
- Sin barra de direcciones ni marcos.
- Barra lateral abierta y puntero fuera del contenido.
- Antes de cada captura: red inactiva, sin indicadores de carga y unos segundos más para que se cierren los avisos
  emergentes.

URL base: `http://127.0.0.1:18081`.

| Plataforma | Árbol | Imágenes |
|---|---|---|
| `protys` (A4a, A5a) | interfaz reconstruida con `docker compose -p protys -f docker/docker-compose.yml up -d --build --no-deps protys-frontend`, con el árbol limpio en `198de8f`; sin borrar volúmenes ni repetir la carga inicial | interfaz `sha256:18bec5e37b257617ff058c2e2fc9404b9de6c8be9acdae87b3b7a6d6d852e872`; backend sin cambios desde la v1, `sha256:f9273842c56ca5b6822a480dc0790dbd1a9d3ad5525bf0893a2f2ce637fe7900` |
| `protys-e2e` (C1, C2a, C2b) | `OUT=/tmp/erp-capturas-v2 research/integration/erp/run.sh` (24 de 24 APROBADO; el script registra el commit `6cc4592`, que tiene el código de `198de8f` más las salidas de la ronda 5); después, `up -d --build --no-deps protys-frontend` en el mismo proyecto | interfaz `sha256:3f7fbeab14d5631d1c95539aca0389497415a54ebae54408dcf74b0288b099a2`; backend `sha256:24f86f3470ab9cc2cf665e8c6c0ed18492c636c6ad908c17fb3c66f81b8ccc58` |

Los identificadores del backend no coinciden entre proyectos aunque el contenido sea el mismo. El identificador
de una imagen incluye sus etiquetas, y Compose les agrega el nombre del proyecto.

## Capturas

| Archivo | Plataforma | Hora UTC | Pasos |
|---|---|---|---|
| `A4a-consulta-Q06.png` | `protys`, **con el piloto ISO 10303 incorporado** (3 módulos, 25 reglas activas) | 19:01:32 | SPARQL Console → Templates → Q06 → agrandar el campo de la consulta con su control de tamaño hasta ver el texto → Execute; vista de la consulta. El resultado fue «Results (3)», igual que en la v1: Q06 no depende del piloto |
| `A5a-consulta-Q04.png` | ídem | 19:01:45 | Igual que A4a con Q04. El resultado fue «Results (40)», igual que en la v1 |
| `C1-erp-conectores.png` | `protys-e2e` | 19:04:32 | `/erp`. `adempiere-demo` y `odoo-demo` aparecen en «Materialized», con tilde y en verde, y muestran servidor (`adempiere-db:5432`, `odoo-db:5432`), base, usuario `protys_ro`, grafo de destino y última materialización (348 y 201 triples). No se muestra la contraseña |
| `C2a-consulta-cruzada.png` | `protys-e2e` | 19:04:42 | SPARQL Console → pegar `research/integration/erp/queries/CRUZADA.rq` → agrandar el campo → Execute; vista de la consulta |
| `C2b-consulta-cruzada.png` | `protys-e2e` | 19:04:45 | La misma pantalla, desplazada hasta el final: «Results (10)», las 8 filas de `…/erp/adempiere` y las 2 de `…/erp/odoo` |

Notas:
- **Q04 y Q06.** Antes de capturar se comprobó que con el piloto Q06 sigue dando 3 filas y Q04, 40.
- **Ligaduras.** Se comprobó recortando, a resolución completa, la línea `PREFIX owl:` de A4a en la v1 y en la v2.
- **Hora de la última materialización (C1).** Es la del servidor, sin zona: la API devuelve `LocalDateTime` y la
  pantalla la muestra tal cual.
- **Alta desde la pantalla.** Con la interfaz nueva se probó en `protys`. Se registró un conector de prueba
  hacia un servidor inexistente: `POST` respondió 201 y la tarjeta salió en «Registered». La prueba de conexión
  mostró «Connection test failed», y el borrado desde la pantalla respondió 204. No quedó ningún conector, y la
  contraseña no apareció en la pantalla.

## Estado en que quedó `protys`

Igual que al terminar la v1, con la interfaz nueva:
- Módulos: Canonical_Catalog_Integrated, iso10303-ap242-fragment e «iso10303-ap242-fragment — datos de instancia».
- 25 reglas, las 25 activas.
- Ningún conector ERP.

`protys-e2e` quedó detenida.
