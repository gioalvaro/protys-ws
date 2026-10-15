package org.protys.ws.exception;

import java.util.UUID;

/** Sanitized recovery receipt; never wraps raw JDBC or repository failures. */
public final class ERPMaterializationException extends ProtysFusekiException {
    private final UUID operationId;
    private final String compensationStatus;

    public ERPMaterializationException(UUID operationId, String compensationStatus) {
        super("ERP materialization failed; graph recovery status: " + compensationStatus);
        this.operationId = operationId;
        this.compensationStatus = compensationStatus;
    }

    public UUID getOperationId() { return operationId; }
    public String getCompensationStatus() { return compensationStatus; }
}
