# Alcance de los bosquejos ERP

Estos dos archivos son diseños documentales históricos para ADempiere y Odoo. Se conservan las clases y consultas SQL propuestas; no se han verificado los esquemas SQL, conexiones, ejecución de un procesador de mapeos ni resultados sobre un ERP industrial.

Se corrigió `rr:` al espacio de nombres oficial `http://www.w3.org/ns/r2rml#`. Las condiciones propuestas se identifican con `exp:condition`, una extensión del proyecto, porque `rr:condition` no pertenece al vocabulario R2RML. Los archivos siguen mezclando RML histórico y condiciones de diseño: no se declara su conformidad con R2RML. Una comprobación del parser Turtle sólo demuestra sintaxis RDF válida.

El fragmento R2RML documental del manuscrito usa `rr:logicalTable`, `rr:sqlQuery`, `rr:subjectMap` y `rr:column` conforme a la [recomendación W3C](https://www.w3.org/TR/r2rml/). Su sintaxis tampoco acredita una integración ejecutada. La evaluación nueva de los capítulos3 y4 y la demostración REST aislada utilizan exclusivamente el catálogo y los datos sintéticos de `research/`; estos bosquejos ERP no intervienen en sus resultados.
