"""Reinicio de la plataforma: docker/load_canonical.py conserva lo que la plataforma registró
por su API (asistente, explorador, ERP) y sigue rechazando lo ajeno o lo canónico cambiado.
Sin Docker: las consultas a Fuseki se reemplazan por grafos en memoria."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from rdflib import BNode, Graph, Literal, Namespace, URIRef
from rdflib.namespace import RDF

REPO = Path(__file__).resolve().parents[2]
os.environ.setdefault("PROTYS_REPOSITORY", str(REPO))
spec = importlib.util.spec_from_file_location("load_canonical", REPO / "docker/load_canonical.py")
lc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lc)

SWRL = Namespace("http://www.w3.org/2003/11/swrl#")
A = "http://w3id.org/protys/ontology/alignment#"
CANONICAL_GRAPH = "http://w3id.org/protys/ontology/Canonical_Catalog_Integrated#"
INCORPORATED = "http://w3id.org/protys/ontology/incorporated/e123"


def rule(identifier, body_class="https://e.org/C", head_class="https://e.org/D"):
    """Una regla SWRL mínima, con átomos en nodos en blanco como las que guarda el backend."""
    g = Graph()
    r, x = URIRef(A + identifier), URIRef(A + "x")
    g.add((r, RDF.type, SWRL.Imp))
    for prop, cls in ((SWRL.body, body_class), (SWRL.head, head_class)):
        lst = g.resource(BNode())
        atom = g.resource(BNode())
        atom.add(RDF.type, SWRL.ClassAtom)
        atom.add(SWRL.classPredicate, URIRef(cls))
        atom.add(SWRL.argument1, x)
        lst.add(RDF.type, SWRL.AtomList)
        lst.add(RDF.first, atom)
        lst.add(RDF.rest, RDF.nil)
        g.add((r, prop, lst.identifier))
    g.add((x, RDF.type, SWRL.Variable))
    return g


class VerifyExistingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.combined, cls.rules, cls.manifest = lc.prepare()

    def setUp(self):
        self.marker = {"source_manifest": self.manifest, "module_id": "m0", "module_graph": CANONICAL_GRAPH}
        self.modules = [{"id": "m0", "name": lc.MODULE_NAME, "namedGraph": CANONICAL_GRAPH}]
        self.registered = [{"ruleId": i} for i in self.manifest["expected_rule_ids"]]
        self.graphs = {lc.MARKER_GRAPH: Graph(), CANONICAL_GRAPH: self.combined,
                       lc.RULE_GRAPH: self.rules + Graph(), lc.INFERENCE_GRAPH: Graph()}

    def incorporate(self):
        """Lo que deja el asistente: módulo del estándar, módulo de datos y la regla R27 activa."""
        self.modules += [{"id": "m1", "name": "fragmento", "namedGraph": INCORPORATED},
                         {"id": "m2", "name": "fragmento — datos", "namedGraph": INCORPORATED + "/data"}]
        self.graphs[INCORPORATED] = Graph()
        self.graphs[INCORPORATED + "/data"] = Graph()
        self.graphs[lc.RULE_GRAPH] += rule("R27")
        self.registered.append({"ruleId": "R27"})

    def run_verify(self):
        names = {"results": {"bindings": [{"g": {"value": name}} for name in self.graphs]}}
        out = io.StringIO()
        with patch.object(lc, "query", return_value=names), \
                patch.object(lc, "graph", side_effect=lambda uri: self.graphs[uri]), \
                contextlib.redirect_stdout(out):
            lc.verify_existing(self.marker, self.combined, self.rules, self.manifest,
                               self.modules, self.registered)
        return json.loads(out.getvalue())

    def assert_rejected(self, fragment):
        with self.assertRaises(RuntimeError) as failure:
            self.run_verify()
        self.assertIn(fragment, str(failure.exception))

    def test_arranque_canonico_sin_cambios_se_conserva(self):
        result = self.run_verify()
        self.assertEqual("PRESERVED", result["status"])
        self.assertEqual([], result["added_modules"])
        self.assertEqual([], result["added_rules"])

    def test_conserva_lo_incorporado_con_el_asistente(self):
        self.incorporate()
        result = self.run_verify()
        self.assertEqual("PRESERVED", result["status"])
        self.assertEqual([INCORPORATED, INCORPORATED + "/data"], result["added_modules"])
        self.assertEqual(["R27"], result["added_rules"])

    def test_conserva_grafos_erp_y_sesiones_abiertas_del_asistente(self):
        self.graphs["http://w3id.org/protys/erp/odoo"] = Graph()
        self.graphs["http://w3id.org/protys/ontology/temp/wizard/abc"] = Graph()
        self.assertEqual(2, len(self.run_verify()["platform_graphs"]))

    def test_rechaza_grafo_ajeno(self):
        self.graphs["https://ajeno.example/g"] = Graph()
        self.assert_rejected("Foreign named graph present (https://ajeno.example/g)")

    def test_rechaza_modulo_canonico_ausente(self):
        self.modules[0] = {"id": "otro", "name": lc.MODULE_NAME, "namedGraph": CANONICAL_GRAPH}
        self.assert_rejected("Foreign or incomplete module metadata")

    def test_rechaza_regla_canonica_no_registrada(self):
        self.registered.pop()
        self.assert_rejected("Foreign or incomplete rule metadata")

    def test_rechaza_regla_canonica_cambiada_aunque_haya_agregadas(self):
        self.incorporate()
        stored = self.graphs[lc.RULE_GRAPH]
        r01 = URIRef(A + "R01")
        self.assertIn((r01, RDF.type, SWRL.Imp), stored)
        stored.remove((r01, SWRL.head, None))
        self.assert_rejected("Canonical rule R01 changed")

    def test_rechaza_regla_registrada_sin_definicion(self):
        self.incorporate()
        self.registered.append({"ruleId": "R30"})
        self.assert_rejected("Registered rules without definition R30")

    def test_rechaza_definicion_sin_registro(self):
        self.incorporate()
        self.graphs[lc.RULE_GRAPH] += rule("R31")
        self.assert_rejected("Unregistered rule definitions R31")

    def test_rechaza_declaraciones_canonicas_borradas(self):
        self.incorporate()
        stored = self.graphs[lc.RULE_GRAPH]
        variable = next(stored.subjects(RDF.type, SWRL.Variable))
        stored.remove((variable, RDF.type, SWRL.Variable))
        self.assert_rejected("Canonical rule declarations changed")

    def test_rechaza_modulo_canonico_modificado(self):
        self.incorporate()
        self.graphs[CANONICAL_GRAPH] = self.combined + Graph()
        self.graphs[CANONICAL_GRAPH].add((URIRef("https://e.org/s"), RDF.type, Literal("x")))
        self.assert_rejected("Stored asserted sources or rule definitions changed")

    def test_sin_agregados_el_grafo_de_reglas_sigue_siendo_estricto(self):
        self.graphs[lc.RULE_GRAPH].add((URIRef("https://e.org/s"), RDF.type, URIRef("https://e.org/T")))
        self.assert_rejected("Stored asserted sources or rule definitions changed")


class RuleIdTests(unittest.TestCase):
    def test_nombre_local_igual_que_jena(self):
        # Resultados de Resource.getLocalName() de Jena 4.10 para los mismos IRIs.
        for iri, local in (("http://w3id.org/protys/ontology/alignment#R01", "R01"),
                           ("http://e.org/rules/R27", "R27"),
                           ("http://e.org/x/123abc", "abc"),
                           ("urn:protys:R30", "R30")):
            self.assertEqual(local, lc.rule_id(URIRef(iri)))


if __name__ == "__main__":
    unittest.main()
