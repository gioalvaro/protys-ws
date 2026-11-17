# Controles H3 del piloto: plataforma contra la evaluación por programa

Esperado: `research/evaluation/current/assertion-groups/{g30,g31}/H3_*.json` (resumido en `research/release/functional.json`).
Antes y después: las mismas consultas ASK de `research/data/fixtures/assertions/` por `POST /api/sparql/execute`.

| Control | Configuración evaluada | Esperado | Plataforma antes | Plataforma después | Resultado |
|---|---|---|---|---|---|
| H3_components_same_identity | g30 (h3_extended) | true | false | **true** | igual a lo esperado |
| H3_configuration_without_pilot_marker | g30 (h3_extended) | false | false | **false** | igual a lo esperado |
| H3_no_new_named_individuals | g30 (h3_extended) | true | false | **true** | igual a lo esperado |
| H3_product_definition_without_pilot_marker | g30 (h3_extended) | false | false | **false** | igual a lo esperado |
| H3_same_named_individual_set | g30 (h3_extended) | true | false | **true** | igual a lo esperado |
| H3_shape_without_pilot_product | g30 (h3_extended) | false | false | **false** | igual a lo esperado |
| H3_source_classes_preserved | g30 (h3_extended) | true | false | **true** | igual a lo esperado |
| H3_unmapped_negative | g30 (h3_extended) | false | false | **false** | igual a lo esperado |
| H3_pre_extension_no_classification | g31 (integrada + datos, sin extensión) | false | **false** | true | se compara con «antes» (sin el fragmento), pero sin los datos del piloto da falso de forma trivial: no es equivalente |

Controles g30 comparables: 8 de 8 iguales a lo esperado.
