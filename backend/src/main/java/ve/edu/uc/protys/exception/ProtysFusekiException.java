package ve.edu.uc.protys.exception;

/**
 * Exception thrown when there are issues communicating with the Fuseki triplestore.
 * This typically indicates a problem with the RDF backend service.
 */
public class ProtysFusekiException extends RuntimeException {

    /**
     * Constructs a new ProtysFusekiException with the specified detail message.
     *
     * @param message the detail message describing the Fuseki error
     */
    public ProtysFusekiException(String message) {
        super(message);
    }

    /**
     * Constructs a new ProtysFusekiException with the specified detail message and cause.
     *
     * @param message the detail message describing the Fuseki error
     * @param cause   the cause of this exception (a Throwable)
     */
    public ProtysFusekiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ProtysFusekiException with the specified cause.
     *
     * @param cause the cause of this exception (a Throwable)
     */
    public ProtysFusekiException(Throwable cause) {
        super(cause);
    }
}
