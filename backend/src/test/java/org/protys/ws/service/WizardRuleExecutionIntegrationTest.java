package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.StandardIncorporationSession;
import org.protys.ws.repository.StandardIncorporationSessionRepository;

/** Synthetic rules executed by the genuine isolated HermiT/SWRLAPI engines. Storage is mocked. */
class WizardRuleExecutionIntegrationTest extends WizardSelectionRegressionTest {
    @Test void abandonedInconsistentSessionCannotContaminateAnotherSessionWithRealOwlValidation() throws Exception {
        ReasoningService real = new ReasoningService(90000, "hermit-swrlapi-drools", Path.of("..").toAbsolutePath().toString());
        var field = StandardIncorporationService.class.getDeclaredField("sessionRepository");
        field.setAccessible(true);
        service = new StandardIncorporationService(fuseki, real, modules, rules,
                (StandardIncorporationSessionRepository) field.get(service));
        UUID abandoned = upload("Abandoned"), clean = upload("Clean");
        var abandonedSession = (StandardIncorporationSession) sessions.get(abandoned);
        Model inconsistent = graphs.get(abandonedSession.getTemporaryGraph());
        String ns = "https://example.org/abandoned#";
        Resource first = inconsistent.createResource(ns + "First"), second = inconsistent.createResource(ns + "Second"), x = inconsistent.createResource(ns + "instance");
        inconsistent.add(first, RDF.type, OWL.Class).add(second, RDF.type, OWL.Class)
                .add(first, OWL.disjointWith, second).add(x, RDF.type, OWL2.NamedIndividual)
                .add(x, RDF.type, first).add(x, RDF.type, second);
        var rejected = service.step2_validateConsistency(abandoned);
        assertEquals("INCONSISTENT", rejected.getStatus(), rejected.getStep2_message());
        assertEquals(false, rejected.getStep2_consistent());
        clearInvocations(fuseki);
        var accepted = service.step2_validateConsistency(clean);
        assertEquals(true, accepted.getStep2_consistent(), accepted.getStep2_message());
        assertEquals(false, service.step3_defineAlignments(clean, List.of()).getStep3_error());
        var verified = service.step4_verifyInferences(clean);
        assertEquals(true, verified.getStep4_verified(), verified.getStep4_message());
        assertEquals(List.of(), verified.getExecutedRules());
        verify(fuseki, never()).getModel(abandonedSession.getTemporaryGraph());
        assertTrue(graphs.containsKey(abandonedSession.getTemporaryGraph()));
    }

    @Test void selectingAInfersOnlyAAndSelectingBInfersOnlyBWithoutChangingGlobalActivation() throws Exception {
        AtomicReference<ReasoningService.ClassificationResult> executed = new AtomicReference<>();
        ReasoningService real = spy(new ReasoningService(90000, "hermit-swrlapi-drools", Path.of("..").toAbsolutePath().toString()));
        doAnswer(call -> {
            var result = (ReasoningService.ClassificationResult) call.callRealMethod();
            executed.set(result); return result;
        }).when(real).classify(any(Model.class), anyList());
        var field = StandardIncorporationService.class.getDeclaredField("sessionRepository");
        field.setAccessible(true);
        service = new StandardIncorporationService(fuseki, real, modules, rules,
                (StandardIncorporationSessionRepository) field.get(service));
        ruleRecords.clear(); a = rule("R901", false); b = rule("R902", true);
        UUID id = upload("RealEngine");
        var session = (StandardIncorporationSession) sessions.get(id);
        Model source = graphs.get(session.getTemporaryGraph());
        String ns = "https://example.org/wizard-test#";
        for (String name : List.of("Input", "OutputA", "OutputB")) source.add(source.createResource(ns + name), RDF.type, OWL.Class);
        Resource individual = source.createResource(ns + "instance");
        source.add(individual, RDF.type, OWL2.NamedIndividual).add(individual, RDF.type, source.createResource(ns + "Input"));
        Model definitions = ModelFactory.createDefaultModel();
        RDFParser.fromString("""
            @prefix e: <https://example.org/wizard-test#> .
            @prefix swrl: <http://www.w3.org/2003/11/swrl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            e:x a swrl:Variable .
            e:R901 a swrl:Imp; rdfs:label "R901";
              swrl:body ([a swrl:ClassAtom; swrl:classPredicate e:Input; swrl:argument1 e:x]);
              swrl:head ([a swrl:ClassAtom; swrl:classPredicate e:OutputA; swrl:argument1 e:x]) .
            e:R902 a swrl:Imp; rdfs:label "R902";
              swrl:body ([a swrl:ClassAtom; swrl:classPredicate e:Input; swrl:argument1 e:x]);
              swrl:head ([a swrl:ClassAtom; swrl:classPredicate e:OutputB; swrl:argument1 e:x]) .
            """).lang(Lang.TURTLE).parse(definitions);
        graphs.put(AlignmentService.RULE_GRAPH, definitions);
        assertEquals(true, service.step2_validateConsistency(id).getStep2_consistent());
        assertEquals(false, service.step3_defineAlignments(id, List.of(a)).getStep3_error());
        var first = service.step4_verifyInferences(id);
        assertEquals(true, first.getStep4_verified(), first.getStep4_message());
        assertEquals(1, executed.get().getImportedRuleCount());
        assertTrue(executed.get().getDeductions().contains(individual, RDF.type, source.createResource(ns + "OutputA")));
        assertFalse(executed.get().getDeductions().contains(individual, RDF.type, source.createResource(ns + "OutputB")));
        assertEquals(List.of(a.getId()), first.getSelectedRuleIds());
        assertEquals(a.getId(), first.getExecutedRules().get(0).getId());
        assertEquals(false, service.step3_defineAlignments(id, List.of(b)).getStep3_error());
        var second = service.step4_verifyInferences(id);
        assertEquals(true, second.getStep4_verified(), second.getStep4_message());
        assertTrue(executed.get().getDeductions().contains(individual, RDF.type, source.createResource(ns + "OutputB")));
        assertFalse(executed.get().getDeductions().contains(individual, RDF.type, source.createResource(ns + "OutputA")));
        assertEquals(List.of(b.getId()), second.getSelectedRuleIds());
        assertFalse(a.getActive()); assertTrue(b.getActive());
        verify(rules, never()).save(any()); verify(rules, never()).saveAll(any());
    }
}
