# Mantenimiento de instalación y conservación de evidencia

Fecha de inicio: 3 de octubre de 2026. Las ejecuciones posteriores registran su fecha y hora UTC.

Esta revisión prepara una instalación nueva de PROTYS-WS y conserva la evidencia de la tesis. La etiqueta [tesis-cierre-2026-10-03-v1](https://github.com/gioalvaro/protys-ws/releases/tag/tesis-cierre-2026-10-03-v1), cuyo commit es `a0428104c94942f3030139454d36bfed3e66873f`, sigue identificando el código y las 120 réplicas citadas en los capítulos. El mantenimiento no reemplaza esas mediciones.

## Cambios comprobables

- La interfaz tiene un archivo de dependencias versionado, instalación estricta con `npm ci` y versiones declaradas de Node y npm.
- La imagen de la aplicación incluye los motores OWL y SWRL, con sus dependencias separadas. Fuseki usa Jena 4.10.0; su registro de mensajes utiliza un classpath separado del validador académico.
- La carga inicial registra el módulo y las 22 reglas por la API. Identifica las fuentes y carga únicamente hechos declarados. Una repetición comprueba la procedencia y conserva activaciones e inferencias; rechaza estados parciales y grafos ajenos.
- El lanzador crea un proyecto Docker identificado, puertos locales libres y volúmenes propios. Conserva la definición de Compose utilizada y su hash. Antes de comprobar o modificar la aplicación verifica el contenedor, el puerto y su salud.
- El proxy conserva el puerto de la dirección original. Las comprobaciones HTTP envían también el encabezado de origen que utiliza el navegador, para detectar rechazos que una consulta directa puede ocultar. La lista de orígenes autorizados del backend se mantiene.
- Cada comprobación conserva solicitudes, respuestas y errores. Si se pierde la respuesta al desactivar una regla, intenta restaurarla y mantiene el resultado fallido de la comprobación.
- Las evaluaciones nuevas escriben en directorios nuevos. Una fase repetida o parcial se rechaza antes de sustituir archivos. Se conservan por separado el entorno funcional y el de rendimiento.
- El tablero cuenta reglas activas y conectores por su estado registrado, deduplica los grafos cargados y distingue instancias de recursos del esquema. No inventa un total acumulado de inferencias ni una fecha de actividad.
- La interfaz utiliza los campos efectivamente devueltos por la API para fechas, estadísticas y actividad. La respuesta del endpoint de salud se diferencia de una comprobación de cada servicio externo.
- La consola muestra las columnas y filas del resultado SPARQL real y los identificadores de las preguntas asociadas. Sus mensajes distinguen SELECT vacío, ASK falso y resultados presentes. CSV conserva el orden y escapa los valores; JSON conserva la respuesta completa y sus términos RDF tipados. La API admite esos dos formatos y rechaza claramente XML y JSONLD, cuyos exportadores históricos se retiraron.
- El flujo automático comprueba backend, escenarios científicos, conservación de evidencia, interfaz e instalación Docker con consultas a través de Nginx. Conserva los registros incluso si una comprobación falla.

## Comprobaciones locales

Se reinstaló la interfaz en una copia de fuentes sin dependencias locales previas: `npm ci`, nueve conjuntos con 35 pruebas y compilación de producción. El árbol fijado por el archivo de bloqueo no presenta dependencias inválidas en `npm ls`.

Desde la copia limpia de entrega, el backend pasó 61 pruebas, sin errores ni omisiones. Los controles de conservación de salidas pasaron 16 pruebas y el lanzador seis. El JDK del host fue Temurin 17.0.10+7; el contenedor preparado utilizó Temurin 17.0.20.1. Se registra esta diferencia de entorno, que no cambia las versiones de las bibliotecas académicas.

La revisión independiente de exportación cotejó también cinco transformaciones sin consultar Fuseki: respuestas reales, términos RDF tipados, valores que requieren escape CSV, SELECT vacío y ASK falso. Los intentos previos con una tabla sin columnas y una exportación incompleta se conservaron como fallos, separados de la evidencia de aceptación.

Una nueva evaluación funcional pasó 243 verificaciones en 89 grupos y 105 comparaciones completas de respuestas, distribuidas en cinco configuraciones. Se conservaron los resultados, las versiones y los hashes antes y después; los modelos y los binarios ejecutados no cambiaron durante la evaluación. No se repitieron las 120 réplicas de rendimiento.

La comprobación por HTTP a través de Nginx pasó 32 verificaciones, incluidas las 21 consultas, la caché y el cambio efectivo de R03. Quince comprobaciones adicionales verificaron la repetición del inicializador y su rechazo de un grafo ajeno sin borrarlo. La primera inspección detectó un chequeo de salud con resolución incorrecta de `localhost`; se corrigió a `127.0.0.1`. Los registros de ese intento se conservaron. Un segundo despliegue limpio pasó nuevamente las 32 verificaciones y mostró las fechas y estadísticas corregidas. La prueba del botón en el navegador detectó después el puerto omitido por el proxy; se corrigió la dirección original y se incorporó el origen del navegador al ejecutor. La aceptación final se comprueba desde una imagen nueva, no sólo mediante una recarga de configuración.

Se compararon exactamente 632 archivos de modelos, casos y publicación contra el respaldo completo previo: sin cambios. Los registros de mantenimiento se guardan separados de los resultados originales.

## Reproducción

La ruta recomendada de la aplicación y la parada que conserva sus datos están en el [README principal](../README.md). El [protocolo académico](../research/README.md) explica cómo preparar los motores, ejecutar las verificaciones funcionales y continuar con una medición nueva.

Para examinar los resultados originales de la tesis debe utilizarse la etiqueta científica. Ejecutar la rama de mantenimiento produce evidencia nueva; no certifica retroactivamente que los archivos de esa rama correspondan al manifiesto del primer cierre.

## Alcance

La instalación utiliza datos simulados. Estas verificaciones no acreditan todas las funciones del asistente, conectores industriales, disponibilidad FAIR completa o un estudio de usabilidad. La bibliografía global y los pendientes propios de los capítulos 1 y 2 permanecen registrados.

C033/IC62 sigue siendo una decisión científica pendiente sobre los extremos temporales que debe exigir el caso de limpieza. Esta revisión no cambia ese contrato ni modifica modelos, datos, reglas, respuestas esperadas o las cifras publicadas para resolverlo implícitamente.
