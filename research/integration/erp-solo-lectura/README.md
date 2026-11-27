# Conexión de sólo lectura

`solo-lectura.sh` levanta un PostgreSQL desechable publicado en 127.0.0.1 y corre
`ERPReadOnlyConnectionIntegrationTest` (17 pruebas) con el JDK local, con un usuario que sí puede escribir: la
conexión que abre el conector lee normalmente y rechaza las escrituras, varias sentencias juntas, funciones que
escriben, desvíos por `search_path`, `BEGIN READ WRITE`, escrituras después de `COMMIT` o de `setReadOnly(false)` y
parámetros de la URL que intentan dejar la sesión en modo de escritura.
`contar-clases.py` cuenta las clases declaradas por módulo del catálogo integrado.

```sh
sh research/integration/erp-solo-lectura/solo-lectura.sh
```

- `salidas/solo-lectura.txt`: salida de Maven; `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`.
- `salidas/clases-catalogo.txt`: clases por módulo; 128 clases distintas en el catálogo integrado.
