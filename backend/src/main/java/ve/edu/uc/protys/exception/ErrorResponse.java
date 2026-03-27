package ve.edu.uc.protys.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for standardized error responses in API responses.
 * Provides consistent error information including timestamp, HTTP status, messages, and detailed errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * HTTP status code.
     */
    private int status;

    /**
     * Error type or category.
     */
    private String error;

    /**
     * Detailed error message.
     */
    private String message;

    /**
     * List of detailed error messages (for validation errors, etc.).
     */
    private List<String> errors;

    /**
     * The API path that was requested.
     */
    private String path;
}
