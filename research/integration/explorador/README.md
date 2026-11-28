# Explorador

Evidencia del explorador de la plataforma sobre el módulo `Canonical_Catalog_Integrated` (RF-02: clases, propiedades
e individuos).

```sh
sh research/integration/explorador/correr.sh
```

Proyecto Compose `protys-v110` (puertos 38080, 38081, 33030 y 35432); `correr.sh` reconstruye las imágenes sin caché
desde el árbol de trabajo, registra el commit en `salidas/v1.1.1-00-versiones.txt` y baja el proyecto al terminar.

## v1.1.0 (evidencia E-13)

`jerarquia.py` pide por la API (`GET /api/ontology/modules/{id}/classes`) la jerarquía de clases y la recorre.
Salidas: `salidas/api-classes.json` (respuesta), `salidas/jerarquia.txt` (65 raíces, 128 clases distintas,
profundidad máxima 4; las mismas 128 que cuenta `../erp-solo-lectura/salidas/clases-catalogo.txt`) y
`salidas/01-compose-up.txt`. Se conservan como se obtuvieron; `correr.sh` ya no las reescribe.

La profundidad de `jerarquia.py` se cuenta por nodos: una raíz tiene profundidad 1. Profundidad 4 equivale a tres
niveles de `rdfs:subClassOf` debajo de las raíces (3 contando aristas). El árbol tiene 131 nodos para 128 clases porque
dos clases (CleaningOperation y QualityControl) tienen dos superclases nombradas y aparecen bajo cada una, y con
QualityControl se repite su subclase Inspection.

## v1.1.1

Corrida del commit `6b58c68` (`salidas/v1.1.1-00-versiones.txt`, sin cambios sin commit).

| Script | Qué hace | Salidas |
|---|---|---|
| `jerarquia.py` | El mismo recorrido de clases, sobre esta versión | `salidas/api-classes-v1.1.1.json`, `salidas/jerarquia-v1.1.1.txt` |
| `propiedades.py` | Pide `GET /api/ontology/modules/{id}/properties` y recorre los dos árboles | `salidas/api-properties.json`, `salidas/propiedades.txt` |
| `contar-propiedades.py` | Conteo con rdflib sin pasar por la plataforma, sobre la lista de archivos de la configuración `integrated` leída de `research/catalog.json` (la que carga `docker/load_canonical.py`), y comparación con el servicio | `salidas/conteo-independiente.txt`, `salidas/conteo-independiente.json` |
| `comparar-clases.py` | `/classes` de v1.1.0 frente a esta versión | `salidas/clases-v1.1.0-vs-v1.1.1.txt`, `.json` |

Resultados:

- Propiedades de objeto: 116, con 103 raíces y profundidad máxima 2 contada por nodos (1 por aristas: hay un solo
  nivel de `rdfs:subPropertyOf`). Propiedades de datos: 107, todas raíces, profundidad 1 (0 por aristas). Propiedades
  de anotación declaradas: 0. Por nodos, una raíz tiene profundidad 1; por aristas, 0.
- El conteo independiente coincide con el servicio en las tres cantidades, en el conjunto de IRI, en las raíces y en la
  profundidad.
- Los ocho módulos base de `ontologies/` dan 87 clases, 81 propiedades de objeto y 38 de datos, módulo por módulo igual
  a la Tabla 3.11 de la tesis. El módulo integrado (128 clases, 116 de objeto, 107 de datos) agrega los perfiles de
  `research/model/` y `ontologies/paint-case-extension.owl`; el total de la tesis con el piloto (134, 119, 109) se
  obtiene sumando `ontologies/iso10303-ap242-fragment.owl` (6 clases, 3 de objeto, 2 de datos), que no forma parte del
  módulo integrado.
- Ninguna propiedad tiene una característica declarada sin `owl:ObjectProperty` ni `owl:DatatypeProperty`, y no hay
  predicados usados sin declarar (fuera de los vocabularios RDF, RDFS, OWL, XSD y SWRL).
- `/classes` da el mismo árbol que en v1.1.0 (65 raíces, 128 clases, profundidad 4). Cambian `objectProperties` y
  `dataProperties`, que antes eran siempre `null`, y `propertyCount` en cuatro clases (Procedure, Procedure_Activity,
  ElementaryFlow y ProductFlow): ahora cuenta también las propiedades cuyo dominio declarado es una unión que incluye la
  clase (`process#hasParameter` e `iso14040#refersToMaterial`).
