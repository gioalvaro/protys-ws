package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.AlignmentRule;
import org.protys.ws.repository.*;
import org.springframework.mock.web.MockMultipartFile;

/** La subida de reglas suma definiciones al grafo de reglas; no borra las de otras reglas. */
class AlignmentRuleUploadTest {
    static final String SWRL = "http://www.w3.org/2003/11/swrl#";
    static final String A = "http://w3id.org/protys/ontology/alignment#";
    final Map<String, Model> graphs = new HashMap<>();
    final Map<String, AlignmentRule> rows = new HashMap<>();
    FusekiService fuseki = mock(FusekiService.class);
    AlignmentRuleRepository rules = mock(AlignmentRuleRepository.class);
    AlignmentService service;

    @BeforeEach void setup() {
        when(fuseki.getModelOrEmpty(anyString())).thenAnswer(c -> ModelFactory.createDefaultModel().add(graphs.getOrDefault(c.getArgument(0), ModelFactory.createDefaultModel())));
        doAnswer(c -> { graphs.put(c.getArgument(0), ModelFactory.createDefaultModel().add((Model) c.getArgument(1))); return null; }).when(fuseki).putModel(anyString(), any(Model.class));
        when(rules.findByRuleId(anyString())).thenAnswer(c -> Optional.ofNullable(rows.get(c.getArgument(0))));
        when(rules.saveAll(anyList())).thenAnswer(c -> { List<AlignmentRule> l = c.getArgument(0); l.forEach(r -> rows.put(r.getRuleId(), r)); return l; });
        service = new AlignmentService(fuseki, rules, mock(OntologyModuleRepository.class), mock(ReasoningService.class));
    }

    /** Una regla de un átomo de clase en el cuerpo y otro en la cabeza. */
    static Model rule(String id, String bodyClass, String headClass) {
        Model m = ModelFactory.createDefaultModel();
        Resource x = m.createResource(A + "var_x").addProperty(RDF.type, m.createResource(SWRL + "Variable"));
        Resource body = m.createResource().addProperty(RDF.type, m.createResource(SWRL + "ClassAtom"))
                .addProperty(m.createProperty(SWRL + "classPredicate"), m.createResource(bodyClass)).addProperty(m.createProperty(SWRL + "argument1"), x);
        Resource head = m.createResource().addProperty(RDF.type, m.createResource(SWRL + "ClassAtom"))
                .addProperty(m.createProperty(SWRL + "classPredicate"), m.createResource(headClass)).addProperty(m.createProperty(SWRL + "argument1"), x);
        m.createResource(A + id).addProperty(RDF.type, m.createResource(SWRL + "Imp"))
                .addProperty(m.createProperty(SWRL + "body"), m.createList(new RDFNode[]{body}))
                .addProperty(m.createProperty(SWRL + "head"), m.createList(new RDFNode[]{head}));
        return m;
    }

    static MockMultipartFile file(Model m) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); RDFDataMgr.write(out, m, Lang.RDFXML);
        return new MockMultipartFile("file", "rules.owl", "application/rdf+xml", out.toByteArray());
    }

    Model definitions() { return graphs.get(AlignmentService.RULE_GRAPH); }
    boolean has(String id) { return definitions().contains(definitions().createResource(A + id), RDF.type, definitions().createResource(SWRL + "Imp")); }

    @Test void unaSegundaSubidaConservaLasDefinicionesAnteriores() {
        service.loadAlignmentRules(file(rule("R01", "https://e.org/A", "https://e.org/B")));
        Model r01 = AlignmentService.ruleDefinition(definitions(), definitions().createResource(A + "R01"));
        service.loadAlignmentRules(file(rule("R27", "https://e.org/C", "https://e.org/D")));
        assertTrue(has("R01")); assertTrue(has("R27"));
        assertTrue(AlignmentService.ruleDefinition(definitions(), definitions().createResource(A + "R01")).isIsomorphicWith(r01));
        assertEquals(Set.of("R01", "R27"), rows.keySet());
    }

    @Test void repetirLaMismaSubidaNoDuplicaListas() {
        service.loadAlignmentRules(file(rule("R27", "https://e.org/C", "https://e.org/D")));
        long size = definitions().size();
        service.loadAlignmentRules(file(rule("R27", "https://e.org/C", "https://e.org/D")));
        assertEquals(size, definitions().size());
    }

    @Test void otraDefinicionConElMismoIdentificadorSeRechazaSinTocarNada() {
        service.loadAlignmentRules(file(rule("R27", "https://e.org/C", "https://e.org/D")));
        Model before = ModelFactory.createDefaultModel().add(definitions());
        AlignmentRule row = rows.get("R27");
        assertThrows(RuntimeException.class, () -> service.loadAlignmentRules(file(rule("R27", "https://e.org/C", "https://e.org/OTRA"))));
        assertTrue(definitions().isIsomorphicWith(before));
        assertSame(row, rows.get("R27"));
        verify(rules, times(1)).saveAll(anyList());
    }
}
