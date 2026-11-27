package org.protys.ws.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.repository.OntologyModuleRepository;
import org.protys.ws.service.FusekiService;
import org.protys.ws.service.IndividualService;
import org.protys.ws.service.OntologyService;
import org.protys.ws.service.ReasoningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Recorrido completo (alta, lectura, modificación, baja, deshacer) y entradas inválidas, con el
 * controlador y el servicio reales. Fuseki se reemplaza por un mapa de grafos en memoria y la validación de
 * consistencia por un doble que devuelve el estado que fija cada prueba.
 */
@Import({org.protys.ws.config.SecurityConfig.class, IndividualService.class})
@WebMvcTest(IndividualController.class)
class IndividualControllerTest {

    static final String GRAPH = "http://w3id.org/protys/ontology/Canonical_Catalog_Integrated#";
    static final String HR = "http://w3id.org/protys/ontology/iso15531#HumanResource";
    static final String IND = "http://example.org/individuos/operario-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FusekiService fuseki;
    @MockBean OntologyModuleRepository modules;
    @MockBean OntologyService ontologyService;

    final Map<String, Model> graphs = new HashMap<>();
    final UUID moduleId = UUID.randomUUID();
    String validationStatus = "CONSISTENT";

    @BeforeEach
    void setUp() {
        Model canonical = ModelFactory.createDefaultModel();
        canonical.add(canonical.createResource(HR), RDF.type, OWL.Class);
        canonical.add(canonical.createResource("http://example.org/existing"), RDF.type, canonical.createResource(HR));
        graphs.put(GRAPH, canonical);
        OntologyModule module = new OntologyModule();
        module.setId(moduleId);
        module.setName("Canonical_Catalog_Integrated");
        module.setNamedGraph(GRAPH);
        module.setLoadedAt(LocalDateTime.now());
        when(modules.findAll()).thenReturn(List.of(module));
        when(modules.findById(moduleId)).thenReturn(Optional.of(module));
        when(fuseki.getModel(anyString())).thenAnswer(call -> { Model m = graphs.get(call.<String>getArgument(0)); if (m == null) throw new IllegalStateException("unknown graph " + call.getArgument(0)); return ModelFactory.createDefaultModel().add(m); });
        doAnswer(call -> { graphs.put(call.getArgument(0), ModelFactory.createDefaultModel().add(call.<Model>getArgument(1))); return null; })
                .when(fuseki).putModel(anyString(), any());
        when(ontologyService.validateDetailed(any())).thenAnswer(call -> ReasoningService.ClassificationResult.builder()
                .validationStatus(validationStatus).consistent("CONSISTENT".equals(validationStatus)).build());
    }

    private ResultActions create(String classUri, String body) throws Exception {
        return mvc.perform(post("/api/ontology/individuals").param("classUri", classUri)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Resource ind() {
        return ResourceFactory.createResource(IND);
    }

    @Test
    void fullLifecycleWithTheIriInTheUriQueryParameter() throws Exception {
        create(HR, "{\"uri\":\"" + IND + "\",\"label\":\"Operario 1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation").value("CREATE"))
                .andExpect(jsonPath("$.uri").value(IND))
                .andExpect(jsonPath("$.validation.status").value("CONSISTENT"))
                .andExpect(jsonPath("$.editId", notNullValue()));
        Model g = graphs.get(GRAPH);
        assertTrue(g.contains(ind(), RDF.type, g.createResource(HR)));
        assertTrue(g.contains(ind(), RDF.type, OWL2.NamedIndividual));
        assertTrue(g.contains(ind(), RDFS.label, "Operario 1"));
        assertFalse(g.listStatements().toList().stream().anyMatch(s -> s.getPredicate().getURI().contains("fuseki")));
        verify(ontologyService, times(1)).validateDetailed(moduleId);

        mvc.perform(get("/api/ontology/individuals").param("classUri", HR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].uri").value(hasItems(IND, "http://example.org/existing")));
        mvc.perform(get("/api/ontology/individuals").param("uri", IND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Operario 1"))
                .andExpect(jsonPath("$.types", hasSize(1))).andExpect(jsonPath("$.types[0]").value(HR));

        mvc.perform(put("/api/ontology/individuals").param("uri", IND)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Operario 1 (modificado)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("UPDATE"))
                .andExpect(jsonPath("$.validation.status").value("CONSISTENT"));
        assertTrue(graphs.get(GRAPH).contains(ind(), RDFS.label, "Operario 1 (modificado)"));
        assertFalse(graphs.get(GRAPH).contains(ind(), RDFS.label, "Operario 1"));

        mvc.perform(delete("/api/ontology/individuals").param("uri", IND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("DELETE"));
        assertFalse(graphs.get(GRAPH).contains(ind(), null, (RDFNode) null));
        mvc.perform(get("/api/ontology/individuals").param("uri", IND))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString(IND)));
        verify(ontologyService, times(3)).validateDetailed(moduleId);
    }

    @Test
    void undoRestoresTheGraphAndRefusesWhenTheGraphChangedAfterwards() throws Exception {
        Model original = ModelFactory.createDefaultModel().add(graphs.get(GRAPH));
        validationStatus = "INCONSISTENT";
        String body = create(HR, "{\"uri\":\"" + IND + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validation.status").value("INCONSISTENT"))
                .andExpect(jsonPath("$.validation.consistent").value(false))
                .andReturn().getResponse().getContentAsString();
        String editId = json.readTree(body).path("editId").asText();
        validationStatus = "CONSISTENT";
        mvc.perform(post("/api/ontology/individuals/edits/{id}/undo", editId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("UNDO"))
                .andExpect(jsonPath("$.validation.status").value("CONSISTENT"));
        assertTrue(graphs.get(GRAPH).isIsomorphicWith(original));
        mvc.perform(post("/api/ontology/individuals/edits/{id}/undo", editId)).andExpect(status().isNotFound());

        JsonNode first = json.readTree(create(HR, "{\"uri\":\"" + IND + "\"}").andReturn().getResponse().getContentAsString());
        create(HR, "{\"uri\":\"http://example.org/individuos/otro\"}").andExpect(status().isCreated());
        mvc.perform(post("/api/ontology/individuals/edits/{id}/undo", first.path("editId").asText()))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputsAreRejectedWithMessagesAndNothingIsWritten() throws Exception {
        Model original = ModelFactory.createDefaultModel().add(graphs.get(GRAPH));
        create("http://w3id.org/protys/ontology/iso15531#ClaseQueNoExiste", "{\"label\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Class not declared")));
        create("esto no es un IRI", "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("Malformed IRI")));
        create("relativo", "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("Malformed IRI")));
        mvc.perform(post("/api/ontology/individuals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("classUri")));
        create(HR, "{\"uri\":\"no es un IRI\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("Malformed IRI")));
        create(HR, "{\"properties\":{\"uri\":\"x\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("properties key")));
        create(HR, "{\"uri\":\"http://example.org/existing\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("already exists")));
        mvc.perform(put("/api/ontology/individuals").param("uri", "http://example.org/nadie")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ontology/individuals").param("uri", "http://example.org/nadie")).andExpect(status().isNotFound());
        mvc.perform(get("/api/ontology/individuals").param("uri", "no es un IRI")).andExpect(status().isBadRequest());
        assertTrue(graphs.get(GRAPH).isIsomorphicWith(original));
        verify(fuseki, never()).putModel(anyString(), any());
        verify(ontologyService, never()).validateDetailed(any());
    }

    @Test
    void duplicateCreateWithTheSameIriIsAConflictNotASecondIndividual() throws Exception {
        create(HR, "{\"uri\":\"" + IND + "\",\"label\":\"a\"}").andExpect(status().isCreated());
        create(HR, "{\"uri\":\"" + IND + "\",\"label\":\"a\"}").andExpect(status().isConflict());
        assertEquals(1, graphs.get(GRAPH).listStatements(ind(), RDFS.label, (RDFNode) null).toList().size());
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
