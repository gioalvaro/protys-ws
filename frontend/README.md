# PROTYS-WS Frontend

Interfaz React 18.3.1 del prototipo PROTYS-WS. Incluye pantallas para explorar ontologías, ejecutar consultas SPARQL, gestionar alineamientos y recorrer el asistente de configuración. Las pantallas ERP representan funciones del prototipo; su presencia no acredita un conector industrial validado.

## Entorno fijado

- Node.js **24.18.0**, indicado en `.nvmrc`.
- npm **11.16.0**, indicado en `packageManager` y `engines`.
- Dependencias directas con versiones exactas y árbol completo en `package-lock.json`.
- React Scripts 5.0.1 y TypeScript 4.9.5, compatible con su restricción de dependencias.
- YAML 2.8.2 como dependencia de desarrollo explícita para satisfacer el peer opcional del cargador PostCSS de Tailwind.

Esta configuración corresponde al mantenimiento de instalación reproducible. No reemplaza la descripción del entorno histórico usado para los resultados de la tesis. `.npmrc` rechaza instalaciones con versiones de Node o npm diferentes; no usar `--legacy-peer-deps` para eludir conflictos.

## Instalación y desarrollo

Desde la raíz del repositorio, con [nvm](https://github.com/nvm-sh/nvm) instalado:

```bash
cd frontend
nvm install
nvm use
npm --version
npm ci
npm start
```

La interfaz de desarrollo se sirve en `http://localhost:3000`. Requiere el backend en `http://localhost:8080`; el cliente usa `http://localhost:8080/api` por defecto. `npm ci` instala el árbol del archivo de bloqueo y reemplaza el directorio `node_modules` de esa copia de trabajo.

Para un backend distinto, definir la URL **antes** de iniciar o compilar:

```bash
REACT_APP_API_URL=http://localhost:8080/api npm start
```

Las variables `REACT_APP_*` se incorporan al JavaScript durante la compilación; no deben contener secretos. Cambiarlas en el contenedor Nginx ya construido no modifica el cliente.

## Verificación y compilación

```bash
CI=true npm run test:ci
CI=true REACT_APP_API_URL=/api npm run build
```

`test:ci` ejecuta las pruebas existentes una sola vez. `npm test` conserva el modo interactivo. La compilación genera `build/`; esta carpeta no se versiona. La comprobación de las pruebas y la compilación no sustituye una prueba del navegador contra el backend activo.

## Docker y API

El `Dockerfile` instala con `npm ci` y compila en Node 24.18.0. Las imágenes base de Node y Nginx están fijadas por versión y digest. `.dockerignore` excluye las dependencias locales, las compilaciones previas y los archivos de entorno.

Nginx escucha en el **puerto 80** del contenedor. Su control de salud consulta `/health`. El backend debe estar disponible en la misma red Docker con el nombre `protys-backend`, puerto 8080; Nginx redirige `/api/`, `/swagger-ui/` y `/v3/api-docs` hacia él. Las rutas de React usan el fallback a `index.html`.

La imagen compila con `REACT_APP_API_URL=/api` por defecto para que el navegador use el mismo origen y el proxy Nginx. Se puede cambiar mediante el argumento de compilación `REACT_APP_API_URL`; no mediante una variable de ejecución. Para iniciar el conjunto de servicios, seguir las instrucciones de la raíz del repositorio.

## Estructura y bibliotecas

- `src/components/`: panel, explorador, consultas, alineamientos, ERP y asistente.
- `src/hooks/`: acceso a datos con React Query.
- `src/services/api.js`: cliente Axios y rutas REST.
- `src/services/validationState.js`: interpretación de estados de validación.
- `src/index.css` y `tailwind.config.js`: estilos Tailwind.
- `src/**/*.test.*`: pruebas de estados y componentes.

React Router gestiona la navegación; Recharts representa gráficos, Heroicons aporta iconos y React Toastify muestra notificaciones. La consola SPARQL actual usa un área de texto. Las dependencias de CodeMirror se conservan en el árbol existente y no implican que ese editor esté integrado en la pantalla.

## Límites de mantenimiento

Se conserva la arquitectura React 18 con React Scripts 5.0.1. Algunas dependencias de esa cadena muestran avisos de deprecación. La actualización completa del sistema de compilación requiere una revisión separada; no está incluida en esta reparación de arranque.

## Licencia

Proprietary - PROTYS Team
