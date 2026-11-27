# Suites de pruebas

```sh
sh research/integration/suites/correr.sh
```

Con JAVA_HOME en un JDK 17 y Node 24.18.0 con npm 11.16.0 en el PATH. La interfaz se instala con `npm ci` desde
`frontend/package-lock.json`.

| Salida | Comando | Resultado |
|---|---|---|
| `backend-pruebas.txt` | `cd backend && ./mvnw test` | 288 pruebas, 0 fallas, 0 errores, 17 salteadas |
| `interfaz-pruebas.txt` | `cd frontend && npm test -- --watchAll=false` | 16 suites, 88 pruebas, todas correctas |
| `interfaz-build.txt` | `cd frontend && npm run build` | compilación correcta |
| `guiones-pruebas.txt` | `python3 -m unittest discover -s scripts/tests` | 29 pruebas, todas correctas |
| `evaluacion-pruebas.txt` | `python3 -m unittest discover -s research/evaluation/tests` | 16 pruebas, todas correctas |
| `extractos-contenidos.txt`, `.json` | `python3 research/model/comprobar_extractos.py` | los 11 extractos de `research/model/examples/` contenidos en sus módulos |
| `sintaxis-ejemplos.txt`, `.json` | `python3 research/evaluation/comprobar_sintaxis.py` | 80 archivos, 0 con error |

Las 17 salteadas son `ERPReadOnlyConnectionIntegrationTest`, que necesita un PostgreSQL y corre aparte en
`../erp-solo-lectura/` (17 de 17 correctas).

`comprobar_extractos.py` compara cada triple del extracto con la unión de los módulos que nombra su cabecera. Los
nodos en blanco (restricciones, uniones, listas) se comparan por su forma completa.
