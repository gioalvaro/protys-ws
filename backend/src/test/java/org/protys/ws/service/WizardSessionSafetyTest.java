package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.*;
import org.protys.ws.repository.StandardIncorporationSessionRepository;
import org.protys.ws.exception.ProtysFusekiException;
import org.apache.jena.atlas.web.HttpException;

class WizardSessionSafetyTest extends WizardSelectionRegressionTest {
    StandardIncorporationSession session(UUID id) { return (StandardIncorporationSession) sessions.get(id); }

    StandardIncorporationService restarted() throws Exception {
        var field = StandardIncorporationService.class.getDeclaredField("sessionRepository");
        field.setAccessible(true);
        return new StandardIncorporationService(fuseki, reasoning, modules, rules,
                (StandardIncorporationSessionRepository) field.get(service));
    }

    @Test void sameNameUploadsHaveDistinctOwnedGraphsAndNoGlobalModule() {
        UUID first = upload("Same"), second = upload("Same");
        assertNotEquals(session(first).getTemporaryGraph(), session(second).getTemporaryGraph());
        assertEquals(3, graphs.size()); assertTrue(registered.isEmpty());
        verify(modules, never()).save(any()); verify(modules, never()).saveAndFlush(any());
    }

    @Test void emptySelectionIsExplicitOwlOnlyAndHasAnEmptyExecutionReceipt() {
        UUID id = upload("EmptySelection"); select(id, List.of());
        var verified = service.step4_verifyInferences(id);
        assertEquals(true, verified.getStep4_verified());
        assertEquals(List.of(), verified.getSelectedRuleIds());
        assertEquals(List.of(), verified.getExecutedRules());
        verify(reasoning).classify(any(Model.class), eq(List.of()));
        verify(fuseki, never()).getModel(AlignmentService.RULE_GRAPH);
    }

    @Test void invalidOrUnregisteredRuleInvalidatesEarlierVerification() {
        UUID id = upload("InvalidSelection"); select(id, List.of(a));
        assertEquals(true, service.step4_verifyInferences(id).getStep4_verified());
        AlignmentRule unknown = new AlignmentRule(); unknown.setId(UUID.randomUUID());
        assertEquals(true, service.step3_defineAlignments(id, List.of(unknown)).getStep3_error());
        assertEquals(false, service.step4_verifyInferences(id).getStep4_verified());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        assertEquals(true, service.step3_defineAlignments(id, List.of(new AlignmentRule())).getStep3_error());
        verify(rules, never()).save(any()); assertTrue(registered.isEmpty());
    }

    @Test void aChangedSelectionMustBeVerifiedAgain() {
        UUID id = upload("ChangedSelection"); select(id, List.of(a));
        assertEquals(true, service.step4_verifyInferences(id).getStep4_verified());
        assertEquals(false, service.step3_defineAlignments(id, List.of(b)).getStep3_error());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        var verified = service.step4_verifyInferences(id);
        assertEquals(List.of(b.getId()), verified.getSelectedRuleIds());
        assertEquals(b.getName(), verified.getExecutedRules().get(0).getName());
        assertEquals(b.getId(), verified.getExecutedRules().get(0).getId());
    }

    @Test void deletedSelectedRuleCannotBeReplacedByTheGlobalActivationAtCompletion() {
        UUID id = upload("DeletedRule"); select(id, List.of(a)); service.step4_verifyInferences(id);
        ruleRecords.remove(a.getId());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        assertTrue(registered.isEmpty());
        verify(reasoning, never()).classify(any(Model.class), eq(List.of("R02")));
    }

    @Test void restartResumesSelectionAndCancelDeletesOnlyTheOwnedSession() throws Exception {
        UUID first = upload("A"), second = upload("B"); select(first, List.of(a));
        var recovered = restarted();
        assertEquals(true, recovered.step4_verifyInferences(first).getStep4_verified());
        recovered.cancelIncorporation(first); recovered.cancelIncorporation(first);
        assertFalse(graphs.containsKey(session(first).getTemporaryGraph()));
        assertTrue(graphs.containsKey(session(second).getTemporaryGraph()));
        assertEquals("UPLOADED", session(second).getPhase());
        verify(rules, never()).deleteAll(any());
    }

    @Test void cancellationFailureRetainsOwnershipAndCanBeRetriedAfterRestart() throws Exception {
        UUID id = upload("FailedCancel"); String graph = session(id).getTemporaryGraph();
        doThrow(new RuntimeException("Fuseki unavailable")).when(fuseki).deleteGraph(graph);
        assertThrows(RuntimeException.class, () -> service.cancelIncorporation(id));
        assertEquals("CANCELLING", session(id).getPhase()); assertTrue(graphs.containsKey(graph));
        doAnswer(call -> { graphs.remove(graph); return null; }).when(fuseki).deleteGraph(graph);
        restarted().cancelIncorporation(id);
        assertEquals("CANCELLED", session(id).getPhase()); assertFalse(graphs.containsKey(graph));
    }

    @Test void completionCannotOverwriteAnExistingGraphEvenWhenItsContentMatches() {
        UUID id = upload("Collision"); select(id, List.of(a)); service.step4_verifyInferences(id);
        String target = session(id).getPermanentGraph();
        graphs.put(target, ModelFactory.createDefaultModel().add(graphs.get(session(id).getTemporaryGraph())));
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        service.cancelIncorporation(id);
        assertTrue(graphs.containsKey(target));
        verify(fuseki, never()).putModel(eq(target), any());
        verify(fuseki, never()).deleteGraph(target);
    }

    @Test void graphWriteFollowedByDatabaseFailureIsRecoverableWithoutReplacingOtherModules() throws Exception {
        UUID id = upload("DatabaseFailure"); select(id, List.of(a)); service.step4_verifyInferences(id);
        doThrow(new RuntimeException("Database unavailable")).when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        String target = session(id).getPermanentGraph();
        assertEquals("COMPLETING", session(id).getPhase()); assertTrue(graphs.containsKey(target));
        doAnswer(call -> saveModule(call.getArgument(0))).when(modules).saveAndFlush(any());
        var completed = restarted().completeIncorporation(id);
        assertEquals("COMPLETED", completed.getStatus()); assertNotNull(completed.getFinalModuleId());
        verify(fuseki, times(1)).putModel(eq(target), any());
    }

    @Test void partialCompletionCanBeCancelledWithoutDeletingAnotherSession() {
        UUID id = upload("Partial"), other = upload("Other"); select(id, List.of(a)); service.step4_verifyInferences(id);
        doThrow(new RuntimeException("Database unavailable")).when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        service.cancelIncorporation(id);
        assertFalse(graphs.containsKey(session(id).getPermanentGraph()));
        assertTrue(graphs.containsKey(session(other).getTemporaryGraph()));
    }

    @Test void completedRetryReturnsTheSameReceiptAndCancellationCannotDeleteTheModule() throws Exception {
        UUID id = upload("Completed"); select(id, List.of(a)); service.step4_verifyInferences(id);
        var first = service.completeIncorporation(id); var again = restarted().completeIncorporation(id);
        assertEquals("COMPLETED", first.getStatus());
        assertEquals(first.getFinalModuleId(), again.getFinalModuleId());
        assertEquals(first.getExecutedRules(), again.getExecutedRules());
        assertEquals(first.getRuleDefinitionsSha256(), again.getRuleDefinitionsSha256());
        assertEquals(64, again.getRuleDefinitionsSha256().length());
        assertEquals(List.of(a.getId()), again.getSelectedRuleIds());
        assertThrows(IllegalStateException.class, () -> service.cancelIncorporation(id));
        assertTrue(graphs.containsKey(session(id).getPermanentGraph()));
        verify(modules, times(1)).saveAndFlush(any());
    }

    @Test void globalReasoningExcludesLegacyAbandonedTemporaryModules() {
        OntologyModule orphan = new OntologyModule(); orphan.setId(UUID.randomUUID()); orphan.setName("old-temp");
        orphan.setNamedGraph("http://w3id.org/protys/ontology/temp/old-session"); registered.put(orphan.getId(), orphan);
        OntologyModule permanent = new OntologyModule(); permanent.setId(UUID.randomUUID()); permanent.setName("permanent");
        permanent.setNamedGraph("https://example.org/permanent"); registered.put(permanent.getId(), permanent);
        Model model = ModelFactory.createDefaultModel(); model.add(model.createResource("https://example.org/Permanent"), RDF.type, OWL.Class);
        graphs.put(permanent.getNamedGraph(), model);
        var alignment = new AlignmentService(fuseki, rules, modules, reasoning);
        assertEquals("SUCCESS", alignment.executeReasoning().getStatus());
        verify(fuseki, never()).getModel(orphan.getNamedGraph());
    }

    @Test void only404IsAnAbsentDestinationAndOtherHttpFailuresBlockCompletion() {
        UUID id = upload("HttpBoundary"); select(id, List.of(a)); service.step4_verifyInferences(id);
        String target = session(id).getPermanentGraph();
        doThrow(new ProtysFusekiException("fetch failed", new HttpException(503))).when(fuseki).getModel(target);
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        verify(fuseki, never()).putModel(eq(target), any());
        doThrow(new ProtysFusekiException("not found", new HttpException(404))).when(fuseki).getModel(target);
        assertEquals("COMPLETED", service.completeIncorporation(id).getStatus());
    }

    @Test void repeatedCleanupToleratesOnlyA404MissingGraph() {
        UUID id = upload("HttpCleanup"); String source = session(id).getTemporaryGraph();
        doThrow(new ProtysFusekiException("not found", new HttpException(404))).when(fuseki).deleteGraph(source);
        assertDoesNotThrow(() -> service.cancelIncorporation(id));
        assertEquals("CANCELLED", session(id).getPhase());
    }

    @Test void completedModuleSurvivesCleanupFailureAndRetryReportsCleanupExplicitly() throws Exception {
        UUID id = upload("CleanupReceipt"); select(id, List.of(a)); service.step4_verifyInferences(id);
        String temporary = session(id).getTemporaryGraph();
        doThrow(new ProtysFusekiException("temporary cleanup unavailable")).when(fuseki).deleteGraph(temporary);
        var first = service.completeIncorporation(id);
        assertEquals("COMPLETED", first.getStatus()); assertEquals(true, first.getCleanupPending());
        assertNotNull(first.getFinalModuleId()); assertTrue(graphs.containsKey(session(id).getPermanentGraph()));
        doAnswer(call -> { graphs.remove(temporary); return null; }).when(fuseki).deleteGraph(temporary);
        var retry = restarted().completeIncorporation(id);
        assertEquals("COMPLETED", retry.getStatus()); assertEquals(false, retry.getCleanupPending());
        assertEquals(first.getFinalModuleId(), retry.getFinalModuleId()); assertEquals(first.getExecutedRules(), retry.getExecutedRules());
        assertFalse(graphs.containsKey(temporary));
        verify(modules, times(1)).saveAndFlush(any());
    }

    @Test void aFailedUploadReturnsItsDurableOwnerForSafeCleanup() {
        doThrow(new ProtysFusekiException("write unavailable")).when(fuseki).putModel(startsWith(StandardIncorporationService.TEMP_PREFIX), any());
        String rdf = "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#' xmlns:owl='http://www.w3.org/2002/07/owl#'><owl:Class rdf:about='https://example.org/UploadFailure'/></rdf:RDF>";
        var result = service.step1_upload(new org.springframework.mock.web.MockMultipartFile("file", "test.owl", "application/rdf+xml", rdf.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "Failed upload");
        assertEquals(false, result.getStep1_parsed()); assertNotNull(result.getTempModuleId());
        assertEquals("UPLOADING", session(result.getTempModuleId()).getPhase());
        service.cancelIncorporation(result.getTempModuleId());
        assertEquals("CANCELLED", session(result.getTempModuleId()).getPhase());
    }
}
