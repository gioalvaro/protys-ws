package ve.edu.uc.protys.exception;

/**
 * Exception thrown for OWL parsing, R2RML mapping, and transformation errors.
 * Indicates failures during ontology materialization, data mapping, and RDF generation.
 *
 * Includes optional source file information to help with error diagnosis and debugging.
 */
public class ProtysMappingException extends RuntimeException {

    private final String sourceFile;

    /**
     * Constructs a ProtysMappingException with a message.
     *
     * @param message the error message describing the mapping failure
     */
    public ProtysMappingException(String message) {
        super(message);
        this.sourceFile = null;
    }

    /**
     * Constructs a ProtysMappingException with a message and cause.
     *
     * @param message the error message describing the mapping failure
     * @param cause   the underlying throwable that caused this exception
     */
    public ProtysMappingException(String message, Throwable cause) {
        super(message, cause);
        this.sourceFile = null;
    }

    /**
     * Constructs a ProtysMappingException with a message, cause, and source file.
     *
     * @param message    the error message describing the mapping failure
     * @param cause      the underlying throwable that caused this exception
     * @param sourceFile the path or name of the file involved in the mapping (nullable)
     */
    public ProtysMappingException(String message, Throwable cause, String sourceFile) {
        super(message, cause);
        this.sourceFile = sourceFile;
    }

    /**
     * Gets the source file that was involved in the mapping failure.
     *
     * @return the source file path/name, or null if not provided
     */
    public String getSourceFile() {
        return sourceFile;
    }
}
