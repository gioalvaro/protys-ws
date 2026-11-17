package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/** El texto que se guarda y se muestra para cada regla es SWRL legible, no un nodo en blanco. */
class SwrlTextTest {
    static final String A = "http://w3id.org/protys/ontology/alignment#";

    static Model canonicalRules() {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, Path.of("..", "ontologies", "alignment-rules.owl").toUri().toString());
        return model;
    }

    @Test void lasReglasCanonicasSeLeenComoSwrl() {
        Model model = canonicalRules();
        var imp = model.createResource(SwrlText.SWRL + "Imp");
        var rules = model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, imp).toList();
        assertEquals(22, rules.size());
        for (Resource rule : rules) {
            String text = SwrlText.of(rule);
            assertTrue(text.matches("\\S.* -> \\S.*"), rule.getLocalName() + ": " + text);
            assertTrue(text.contains("?"), "debe tener variables: " + text);
            assertFalse(text.contains("(_"), "sin nodos en blanco como argumento: " + text);
            assertNotNull(SwrlText.description(rule), rule.getLocalName());
        }
    }

    @Test void r01TieneCuerpoYCabezaConVariables() {
        Model model = canonicalRules();
        Resource r01 = model.getResource(A + "R01");
        String text = SwrlText.of(r01);
        assertEquals(2, text.split(" -> ").length, text);
        System.out.println("R01 = " + text);
        assertTrue(SwrlText.description(r01).startsWith("R01:"));
    }

    @Test void reglaSinCabezaNoDevuelveNulo() {
        Model model = ModelFactory.createDefaultModel();
        Resource rule = model.createResource(A + "RX").addProperty(org.apache.jena.vocabulary.RDF.type, model.createResource(SwrlText.SWRL + "Imp"));
        assertEquals(" -> ", SwrlText.of(rule));
        assertNull(SwrlText.description(rule));
    }
}
