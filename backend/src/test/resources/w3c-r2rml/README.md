# Casos de prueba W3C de R2RML

Copia sin modificar de los casos de la suite *RDB2RDF Test Cases* del W3C
(https://www.w3.org/2001/sw/rdb2rdf/test-cases/), bajados de
`https://dvcs.w3.org/hg/rdb2rdf-tests/raw-file/default/<base>/<archivo>` el 2026-11-16.
Cada carpeta `Dnnn-*` trae `manifest.ttl`, el `create.sql` de la base y, por caso, el mapeo
(`r2rml*.ttl`) y el resultado esperado (`mapped*.nq`). Los casos de *Direct Mapping* se ignoran.

Licencia: W3C Test Suite License / W3C 3-clause BSD License
(https://www.w3.org/Consortium/Legal/2008/04-testsuite-copyright.html).

Los usa `R2RMLW3CTestSuiteTest`, que deja el resultado por caso en
`target/w3c-r2rml-report.txt`.
