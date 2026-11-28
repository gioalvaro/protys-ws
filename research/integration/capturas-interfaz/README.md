# Capturas de la interfaz

`scripts/capturas.js` recorre la interfaz con Playwright y Chromium (ventana de 1440×900 con escala 2, PNG de
2880×1800, tema claro) y guarda una captura por paso en `capturas/`, con la acción que la produjo en
`salidas/serie-*.json`.

```sh
PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/capturas-interfaz/correr.sh
```

`correr.sh` levanta la plataforma en `protys-v110` para las series A y B, corre `../erp/correr.sh` con la plataforma
y los ERP en `protys-v110-erp` para la serie C, y baja los dos proyectos al terminar.

| Serie | Capturas | Qué muestra |
|---|---|---|
| A | `A1a`–`A8` | Red integrada: tablero, jerarquía del explorador y detalle de `Device`, Q06 y Q04 en la consola SPARQL (consulta y resultados: 3 y 40 filas), reglas de alineamiento, razonamiento con sus controles (22 reglas activas, 1167 inferidos, 4318 triples de entrada) y la pantalla de ERP sin conectores |
| B | `B1`–`B7` | Incorporación del piloto ISO 10303 con el asistente: fragmento AP242 y datos, validación, selección de las 22 reglas y carga de R27–R29, verificación con las 25 reglas ejecutadas, confirmación, incorporación registrada y el explorador con el módulo nuevo |
| P | `A2b`, `A3b`, `A9a`–`A9c` | Explorador de v1.1.1 con el selector «Classes \| Properties»: vista de clases, detalle de Procedure con las propiedades cuyo dominio declarado la incluye (hasParameter, por una unión), detalle de produces (dominio, rango e inversa), de involve (subpropiedad hasProcess) y de hasParameter (dominio Procedure ∪ Procedure_Activity) |
| C | `C1`–`C2b` | Los dos conectores materializados (ADempiere 348 triples, Odoo 201) y la consulta que cruza los dos ERP (10 filas) |

Las versiones de las imágenes de la corrida están en `salidas/00-versiones.txt`.

La serie P se corre sola, sin regenerar ni borrar las series A, B y C:

```sh
PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/capturas-interfaz/correr-explorador.sh
```

Usa el proyecto `protys-v110` y deja el commit, las imágenes y las versiones en `salidas/00-versiones-P.txt` y el
detalle de cada paso en `salidas/serie-P.json`. La corrida publicada es la del commit `3ee72b8`, sin cambios sin commit.
