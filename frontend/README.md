# PROTYS-WS Frontend

Interfaz React 18.3.1 del prototipo PROTYS-WS. Pantallas: tablero, explorador de ontologías con gestión de individuos, consola SPARQL, alineamientos y razonamiento, conectores ERP y asistente de incorporación de estándares.

## Entorno fijado

- Node.js **24.18.0**, indicado en `.nvmrc`.
- npm **11.16.0**, indicado en `packageManager` y `engines`.
- Dependencias directas con versiones exactas y árbol completo en `package-lock.json`.
- React Scripts 5.0.1 y TypeScript 4.9.5, compatible con su restricción de dependencias.
- YAML 2.8.2 como dependencia de desarrollo explícita para satisfacer el peer opcional del cargador PostCSS de Tailwind.

`.npmrc` rechaza instalaciones con otras versiones de Node o npm (`engine-strict=true`).

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

La interfaz de desarrollo se sirve en `http://localhost:3000`. Requiere el backend en `http://localhost:8080`; el cliente usa `http://localhost:8080/api` por defecto. `npm ci` instala el árbol de `package-lock.json`.

Para un backend distinto, definir la URL **antes** de iniciar o compilar:

```bash
REACT_APP_API_URL=http://localhost:8080/api npm start
```

Las variables `REACT_APP_*` se incorporan al JavaScript durante la compilación.

## Verificación y compilación

```bash
CI=true npm run test:ci
CI=true REACT_APP_API_URL=/api npm run build
```

`test:ci` ejecuta las pruebas una vez (16 suites, 88 pruebas); `npm test` las ejecuta en modo interactivo. La compilación genera `build/`, que no se versiona. Las pruebas de la interfaz contra la plataforma, con capturas, están en `research/integration/capturas-interfaz/` y `research/integration/gestion-individuos/`.

## Docker y API

El `Dockerfile` instala con `npm ci` y compila en Node 24.18.0. Las imágenes base de Node y Nginx están fijadas por versión y digest. `.dockerignore` excluye las dependencias locales, las compilaciones previas y los archivos de entorno.

Nginx escucha en el **puerto 80** del contenedor. Su control de salud consulta `/health`. El backend debe estar disponible en la misma red Docker con el nombre `protys-backend`, puerto 8080; Nginx redirige `/api/`, `/swagger-ui/` y `/v3/api-docs` hacia él. Las rutas de React usan el fallback a `index.html`.

La imagen compila con `REACT_APP_API_URL=/api` por defecto para que el navegador use el mismo origen y el proxy Nginx. Se puede cambiar mediante el argumento de compilación `REACT_APP_API_URL`. El conjunto de servicios se inicia como indica el README de la raíz.

## Estructura y bibliotecas

- `src/components/`: tablero, explorador (con individuos), consultas, alineamientos, ERP y asistente.
- `src/hooks/`: acceso a datos con React Query.
- `src/services/api.js`: cliente Axios y rutas REST.
- `src/services/validationState.js`: interpretación de estados de validación.
- `src/index.css` y `tailwind.config.js`: estilos Tailwind.
- `src/**/*.test.*`: pruebas de estados y componentes.

React Router gestiona la navegación; Recharts representa gráficos, Heroicons aporta iconos y React Toastify muestra notificaciones. La consola SPARQL usa un área de texto.

## Licencia

MIT; ver LICENSE en la raíz.
