# Búsqueda complementaria sobre ontologías y análisis de ciclo de vida

Fecha de ejecución: 26-11-2026. Base: OpenAlex (api.openalex.org). Período: 2014–2026. Orden: por citas.

## Glosario

- **ACV:** análisis de ciclo de vida ambiental (ISO 14040).
- **TA:** búsqueda sobre título y resumen (`title_and_abstract.search`).
- **T:** búsqueda sobre el título (`title.search`).

## Consultas

| N.º | Campo | Términos | Registros |
|---|---|---|---|
| C1 | TA | ontology "life cycle assessment" manufacturing | 13 |
| C2 | TA | ontology "life cycle assessment" product | 40 |
| C3 | TA | ontology "life cycle inventory" | 19 |
| C4 | TA | ontology LCA manufacturing | 9 |
| C5 | TA | ontology "ISO 14040" | 1 |
| C6 | T | ontology "life cycle" | 66 |
| C7 | T | ontology sustainable manufacturing | 7 |
| C8 | T | semantic "life cycle assessment" | 6 |
| C9 | TA | "knowledge graph" LCA manufacturing | 4 |
| C10 | TA | "semantic web" LCA manufacturing | 0 |
| C11 | T | ontology LCA | 4 |

Total: 169 registros, 127 distintos.

Forma de la dirección (C1): `https://api.openalex.org/works?filter=title_and_abstract.search:ontology%20%22life%20cycle%20assessment%22%20manufacturing,publication_year:2014-2026&per-page=100&page=1&sort=cited_by_count:desc&select=id,doi,title,publication_year,type,authorships,primary_location,cited_by_count`

## Selección

Por título y, cuando OpenAlex lo ofrece, por resumen.

| Clasificación | Registros |
|---|---|
| Descartado: ciclo de vida de software, de sistemas o de productos sin representación ambiental | 32 |
| Retenido: productos o procesos de manufactura | 25 |
| Descartado: construcción, edificación e infraestructura | 23 |
| Retenido: datos de ACV de uso general | 18 |
| Descartado: no es una publicación | 12 |
| Descartado: sin relación con el tema | 9 |
| Descartado: agroalimentación y otros dominios | 8 |

Retenidos: 43. Descartados: 84. Leídos, por disponibilidad del texto: 16 de los retenidos.

## Complemento

- Google Scholar, 22-11-2026: `"ISO 15531" "ISO 14040" ontology` (3 resultados) y `ontology "life cycle assessment" "ISO 15531"` desde 2014 (6 resultados). No es reproducible: el orden y el total varían.
- Búsqueda general en la web y referencias de los trabajos leídos. De estas vías provienen Borsato (2014), Wang et al. (2022), Reinbold et al. (2026) y Moreno et al. (2012).

## Archivos

- `registros_openalex_2026-11-26.json`: consultas, direcciones y los 127 registros con su clasificación.
- `clasificacion_2026-11-26.csv`: la misma clasificación en forma de tabla.
