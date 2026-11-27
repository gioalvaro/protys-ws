package org.protys.ws.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ERPGraphPublicationTest {
    private Model published() {
        Model model = ModelFactory.createDefaultModel();
        model.add(model.createResource("urn:published"), model.createProperty("urn:value"), "synthetic");
        return model;
    }

    @Test void compensationFailureHasOperationIdAndExplicitRecoveryReceipt(CapturedOutput output) {
        FusekiService fuseki = mock(FusekiService.class);
        when(fuseki.getModelOrEmpty(anyString())).thenReturn(ModelFactory.createDefaultModel());
        when(fuseki.restoreModelIfUnchanged(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("fake-driver-secret-must-not-be-logged"));
        var publication = new ERPGraphPublication(fuseki, UUID.randomUUID(), "urn:target", published());
        publication.publish();
        assertEquals(ERPGraphPublication.Outcome.COMPENSATION_FAILED, publication.compensate("TEST_FAILURE"));
        assertTrue(output.getAll().contains(publication.operationId().toString()));
        assertTrue(output.getAll().contains("COMPENSATION_FAILED"));
        assertTrue(output.getAll().contains("manualReviewRequired=true"));
        assertFalse(output.getAll().contains("fake-driver-secret-must-not-be-logged"));
    }

    @Test void ambiguousTransactionOutcomeNeverTriggersRestoration(CapturedOutput output) {
        FusekiService fuseki = mock(FusekiService.class);
        when(fuseki.getModelOrEmpty(anyString())).thenReturn(ModelFactory.createDefaultModel());
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var publication = new ERPGraphPublication(fuseki, UUID.randomUUID(), "urn:target", published());
            publication.publish();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
            assertEquals(ERPGraphPublication.Outcome.TRANSACTION_OUTCOME_UNKNOWN, publication.outcome());
            verify(fuseki, never()).restoreModelIfUnchanged(anyString(), any(), any());
            assertTrue(output.getAll().contains(publication.operationId().toString()));
            assertTrue(output.getAll().contains("TRANSACTION_OUTCOME_UNKNOWN"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
