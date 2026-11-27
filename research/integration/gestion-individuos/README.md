# Gestión de individuos

Alta, lectura, modificación, baja y deshacer de individuos sobre el catálogo integrado, por la API
(`scripts/individuos_api.py`) y por la interfaz web (`scripts/individuos_web.js`, Playwright con Chromium), con la
validación OWL posterior a cada edición; comparación del estado de la plataforma antes y después de las ediciones, con
y sin razonamiento; y carga de un módulo en Turtle mal formado.

```sh
PLAYWRIGHT_MODULE=<ruta a playwright> CHROMIUM_PATH=<ruta a Chromium> sh research/integration/gestion-individuos/correr.sh
```

Proyecto Compose `protys-v110` (puertos 38080, 38081, 33030 y 35432); se recrea y al terminar se baja.

## Resultado

| Salida | Qué muestra |
|---|---|
| `salidas/05-individuos-api.txt` | Recorrido completo (201, 200, 404 después de eliminar), entradas inválidas (400), duplicados e individuos del caso (409), inexistentes (404), deshacer (200 y luego 404), un alta que contradice un rango `xsd:integer` (validación INCONSISTENT, deshecha después) y los tiempos de validación |
| `salidas/06-individuos-web.txt`, `capturas/W1`–`W8` | El mismo recorrido por la interfaz: clase con individuos, formulario de alta, alta validada, detalle, modificación, baja, deshacer |
| `salidas/07-comparacion-tras-ediciones.txt` | Estado de la plataforma igual al inicial después de todas las ediciones |
| `salidas/09-comparacion-razonada.txt` | Igual también después de razonar |
| `salidas/10-turtle-invalido.txt` | Turtle mal formado: HTTP 400 y ningún módulo nuevo |
| `salidas/11-backend.log` | Registro del backend de la corrida |

Los individuos de prueba usan IRIs bajo `http://example.org/individuos/`.
