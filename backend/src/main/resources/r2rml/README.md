# Bosquejos de mapeo ERP

`adempiere-mappings.ttl` y `odoo-mappings.ttl` son diseños de correspondencias para ADempiere y Odoo escritos en una
mezcla de R2RML y RML, con condiciones en `exp:condition` (extensión del proyecto, porque `rr:condition` no es parte
de R2RML). Las pruebas unitarias del conector los usan como ruta de mapeo de ejemplo.

Los mapeos que ejecuta la plataforma contra las bases de ADempiere y Odoo son los de
`docker/integration/mappings/` (`adempiere.r2rml.ttl`, `odoo.r2rml.ttl`); su ejecución y resultados están en
`research/integration/erp/`.
