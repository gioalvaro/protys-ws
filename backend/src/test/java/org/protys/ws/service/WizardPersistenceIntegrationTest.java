package org.protys.ws.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.protys.ws.controller.StandardWizardController;
import org.protys.ws.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real repository transactions and real HTTP Fuseki. Semantic execution is tested separately. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:p1-wizard-lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name=target/p1-wizard-lifecycle.log"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WizardPersistenceIntegrationTest {
    @TempDir Path temporary;
    @Autowired StandardIncorporationSessionRepository sessions;
    @Autowired OntologyModuleRepository modules;
    @Autowired AlignmentRuleRepository rules;

    @Test void lifecycleSurvivesRestartAndMissingGraphsWhilePreservingOtherSessionAndPermanentData() throws Exception {
        try (var server = new IsolatedFusekiFixture(temporary)) {
            var reasoning = mock(ReasoningService.class);
            var consistent = ReasoningService.ClassificationResult.builder()
                    .consistent(true).validationStatus("CONSISTENT").owlValidationStatus("CONSISTENT")
                    .deductions(ModelFactory.createDefaultModel()).importedRuleCount(0).build();
            when(reasoning.classify(any())).thenReturn(consistent);
            when(reasoning.classify(any(), anyList())).thenReturn(consistent);
            var service = new StandardIncorporationService(server.client, reasoning, modules, rules, sessions);
            var sentinel = ModelFactory.createDefaultModel();
            sentinel.add(sentinel.createResource("urn:p1:sentinel"), sentinel.createProperty("urn:p1:value"), "keep");
            server.client.putModel("urn:p1:unrelated", sentinel);
            UUID a = upload(service, "A"), b = upload(service, "B");
            String aGraph = sessions.findById(a).orElseThrow().getTemporaryGraph();
            String bGraph = sessions.findById(b).orElseThrow().getTemporaryGraph();
            var beforeB = server.client.getModel(bGraph);
            assertTrue(modules.findAll().isEmpty(), "Temporary sessions must not enter the permanent registry");

            // Simulate process loss after DELETE succeeded but before the CANCELLED checkpoint.
            var partial = sessions.findById(a).orElseThrow();
            partial.setPhase("CANCELLING"); sessions.saveAndFlush(partial);
            server.client.deleteGraph(aGraph);
            var restarted = new StandardIncorporationService(server.client, reasoning, modules, rules, sessions);
            var http = MockMvcBuilders.standaloneSetup(new StandardWizardController(restarted))
                    .defaultRequest(get("/").accept(org.springframework.http.MediaType.APPLICATION_JSON)).build();
            http.perform(delete("/api/wizard/" + a)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            http.perform(delete("/api/wizard/" + a)).andExpect(status().isOk());
            assertEquals("CANCELLED", sessions.findById(a).orElseThrow().getPhase());
            assertTrue(server.client.getModel(bGraph).isIsomorphicWith(beforeB));
            assertTrue(server.client.getModel("urn:p1:unrelated").isIsomorphicWith(sentinel));

            assertEquals(true, restarted.step2_validateConsistency(b).getStep2_consistent());
            assertEquals(false, restarted.step3_defineAlignments(b, List.of()).getStep3_error());
            assertEquals(true, restarted.step4_verifyInferences(b).getStep4_verified());
            var completed = restarted.completeIncorporation(b);
            assertEquals("COMPLETED", completed.getStatus(), completed.getMessage());
            var saved = modules.findById(completed.getFinalModuleId()).orElseThrow();
            assertEquals(b, saved.getIncorporationSessionId());
            assertTrue(server.client.getModel(saved.getNamedGraph()).isIsomorphicWith(beforeB));
            http.perform(post("/api/wizard/complete/" + b)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.finalModuleId").value(completed.getFinalModuleId().toString()));
            http.perform(delete("/api/wizard/" + b)).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value("COMPLETED"));
            assertEquals(1, modules.count());
            assertTrue(server.client.getModel(saved.getNamedGraph()).isIsomorphicWith(beforeB));
            assertTrue(server.client.getModel("urn:p1:unrelated").isIsomorphicWith(sentinel));
        }
    }

    private UUID upload(StandardIncorporationService service, String name) {
        String content = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:owl=\"http://www.w3.org/2002/07/owl#\">"
                + "<owl:Class rdf:about=\"https://example.org/" + name + "\"/></rdf:RDF>";
        var result = service.step1_upload(new MockMultipartFile("file", name + ".owl", "application/rdf+xml", content.getBytes(StandardCharsets.UTF_8)), name);
        assertEquals(true, result.getStep1_parsed(), result.getStep1_message());
        return result.getTempModuleId();
    }
}
