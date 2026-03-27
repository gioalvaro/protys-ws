package ve.edu.uc.protys.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers in the PROTYS application.
 * Provides centralized exception handling and consistent error response formatting.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles ProtysFusekiException - issues with the triplestore backend.
     * Returns HTTP 502 Bad Gateway status.
     */
    @ExceptionHandler(ProtysFusekiException.class)
    public ResponseEntity<ErrorResponse> handleProtysFusekiException(
            ProtysFusekiException ex,
            WebRequest request) {

        log.error("Fuseki triplestore error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_GATEWAY.value())
                .error("Triplestore Error")
                .message(ex.getMessage())
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_GATEWAY);
    }

    /**
     * Handles ProtysValidationException - validation failures.
     * Returns HTTP 400 Bad Request status with detailed validation errors.
     */
    @ExceptionHandler(ProtysValidationException.class)
    public ResponseEntity<ErrorResponse> handleProtysValidationException(
            ProtysValidationException ex,
            WebRequest request) {

        log.warn("Validation error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message(ex.getMessage())
                .errors(!ex.getErrors().isEmpty() ? ex.getErrors() : null)
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ProtysNotFoundException - entity not found errors.
     * Returns HTTP 404 Not Found status.
     */
    @ExceptionHandler(ProtysNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProtysNotFoundException(
            ProtysNotFoundException ex,
            WebRequest request) {

        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles ProtysMappingException - OWL parsing, R2RML mapping, and transformation errors.
     * Returns HTTP 422 Unprocessable Entity status.
     */
    @ExceptionHandler(ProtysMappingException.class)
    public ResponseEntity<ErrorResponse> handleProtysMappingException(
            ProtysMappingException ex,
            WebRequest request) {

        log.error("Mapping/transformation error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Mapping Error")
                .message(ex.getMessage())
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Handles ProtysERPException - ERP connector operation failures.
     * Returns HTTP 503 Service Unavailable status.
     */
    @ExceptionHandler(ProtysERPException.class)
    public ResponseEntity<ErrorResponse> handleProtysERPException(
            ProtysERPException ex,
            WebRequest request) {

        log.error("ERP connector error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("ERP Service Error")
                .message(ex.getMessage())
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Handles MethodArgumentNotValidException - validation errors on request parameters.
     * Returns HTTP 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        log.warn("Method argument validation failed");

        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        List<String> globalErrors = ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> String.format("%s: %s", error.getObjectName(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        List<String> allErrors = new ArrayList<>(fieldErrors);
        allErrors.addAll(globalErrors);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Request validation failed")
                .errors(!allErrors.isEmpty() ? allErrors : null)
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles MaxUploadSizeExceededException - file upload size exceeds limit.
     * Returns HTTP 413 Payload Too Large status.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            WebRequest request) {

        log.warn("Upload size exceeded: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Payload Too Large")
                .message("The uploaded file exceeds the maximum allowed size")
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Handles all other uncaught exceptions.
     * Returns HTTP 500 Internal Server Error status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please contact support if the problem persists.")
                .path(getRequestPath(request))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Extracts the request path from the WebRequest.
     *
     * @param request the WebRequest object
     * @return the request path or empty string if not available
     */
    private String getRequestPath(WebRequest request) {
        String description = request.getDescription(false);
        return description != null ? description.replace("uri=", "") : "";
    }
}
