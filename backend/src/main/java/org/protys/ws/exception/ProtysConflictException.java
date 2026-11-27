package org.protys.ws.exception;

/** El recurso que se quiere crear ya existe (HTTP 409). */
public class ProtysConflictException extends RuntimeException {
    public ProtysConflictException(String message) {
        super(message);
    }
}
