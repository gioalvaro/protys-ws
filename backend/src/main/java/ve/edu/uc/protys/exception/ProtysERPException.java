package ve.edu.uc.protys.exception;

/**
 * Exception thrown when there are issues with ERP connector operations.
 * This can include connection failures, materialization errors, and data synchronization problems.
 */
public class ProtysERPException extends RuntimeException {

    /**
     * Constructs a new ProtysERPException with the specified detail message.
     *
     * @param message the detail message describing the ERP error
     */
    public ProtysERPException(String message) {
        super(message);
    }

    /**
     * Constructs a new ProtysERPException with the specified detail message and cause.
     *
     * @param message the detail message describing the ERP error
     * @param cause   the cause of this exception (a Throwable)
     */
    public ProtysERPException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ProtysERPException with the specified cause.
     *
     * @param cause the cause of this exception (a Throwable)
     */
    public ProtysERPException(Throwable cause) {
        super(cause);
    }
}
