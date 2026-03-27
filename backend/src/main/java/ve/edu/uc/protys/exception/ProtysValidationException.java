package ve.edu.uc.protys.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when validation of PROTYS data fails.
 * This exception carries a list of validation errors for detailed error reporting.
 */
public class ProtysValidationException extends RuntimeException {

    private final List<String> errors;

    /**
     * Constructs a new ProtysValidationException with a message and list of validation errors.
     *
     * @param message the detail message
     * @param errors  the list of validation error messages
     */
    public ProtysValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    /**
     * Constructs a new ProtysValidationException with a message and a single validation error.
     *
     * @param message the detail message
     * @param error   a single validation error message
     */
    public ProtysValidationException(String message, String error) {
        super(message);
        this.errors = new ArrayList<>();
        if (error != null) {
            this.errors.add(error);
        }
    }

    /**
     * Constructs a new ProtysValidationException with only a message (no specific errors).
     *
     * @param message the detail message
     */
    public ProtysValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
    }

    /**
     * Gets the list of validation errors.
     *
     * @return an unmodifiable list of validation error messages
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
