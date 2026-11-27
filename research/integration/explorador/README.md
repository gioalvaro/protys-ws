# Explorador

`jerarquia.py` pide por la API (`/api/ontology/classes`) la jerarquía de clases del módulo
`Canonical_Catalog_Integrated` y la recorre.

```sh
sh research/integration/explorador/correr.sh
```

Proyecto Compose `protys-v110`. Salidas: `salidas/api-classes.json` (respuesta) y `salidas/jerarquia.txt`
(65 raíces, 128 clases distintas, profundidad máxima 4; las mismas 128 que cuenta
`../erp-solo-lectura/salidas/clases-catalogo.txt`).
