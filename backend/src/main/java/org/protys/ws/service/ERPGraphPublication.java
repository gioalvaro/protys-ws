package org.protys.ws.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Best-effort recovery across two stores; this is not a distributed transaction. */
@Slf4j
final class ERPGraphPublication {
    enum Outcome { NOT_PUBLISHED, PUBLISHED, RESTORED, CURRENT_GRAPH_CHANGED, COMPENSATION_FAILED, TRANSACTION_OUTCOME_UNKNOWN }
    private final UUID operationId = UUID.randomUUID();
    private final UUID connectorId;
    private final String graph;
    private final FusekiService fuseki;
    private final Model previous;
    private final Model expected;
    private boolean attempted;
    private Outcome outcome = Outcome.NOT_PUBLISHED;

    ERPGraphPublication(FusekiService fuseki, UUID connectorId, String graph, Model expected) {
        this.fuseki = fuseki;
        this.connectorId = connectorId;
        this.graph = graph;
        this.expected = ModelFactory.createDefaultModel().add(expected);
        this.previous = ModelFactory.createDefaultModel().add(fuseki.getModelOrEmpty(graph));
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) compensate("TRANSACTION_ROLLBACK");
                    else if (status == STATUS_UNKNOWN && attempted) {
                        outcome = Outcome.TRANSACTION_OUTCOME_UNKNOWN;
                        receipt("TRANSACTION_COMPLETION");
                    }
                }
            });
        }
    }

    void publish() {
        attempted = true; // A failed HTTP response does not establish that nothing was written.
        fuseki.putModel(graph, expected);
        outcome = Outcome.PUBLISHED;
    }

    synchronized Outcome compensate(String trigger) {
        if (!attempted || outcome == Outcome.RESTORED || outcome == Outcome.CURRENT_GRAPH_CHANGED) return outcome;
        try {
            outcome = fuseki.restoreModelIfUnchanged(graph, expected, previous)
                    ? Outcome.RESTORED : Outcome.CURRENT_GRAPH_CHANGED;
        } catch (Exception failure) {
            outcome = Outcome.COMPENSATION_FAILED;
        }
        receipt(trigger);
        return outcome;
    }

    private void receipt(String trigger) {
        // Deliberately omit graph data, JDBC details and raw exceptions from this recovery receipt.
        if (outcome == Outcome.RESTORED) {
            log.info("ERP_GRAPH_RECOVERY operationId={} connectorId={} outcome={} trigger={}", operationId, connectorId, outcome, trigger);
        } else {
            log.error("ERP_GRAPH_RECOVERY operationId={} connectorId={} outcome={} trigger={} manualReviewRequired=true",
                    operationId, connectorId, outcome, trigger);
        }
    }

    UUID operationId() { return operationId; }
    Outcome outcome() { return outcome; }
}
