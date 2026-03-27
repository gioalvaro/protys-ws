package ve.edu.uc.protys.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for data materialization results from ERP to RDF.
 * Captures the outcome of R2RML-based materialization operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterializationResult {

    private UUID connectorId;

    private String namedGraph;

    private String status;

    private Long tripleCount;

    private Long executionTimeMs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Integer mappingsProcessed;

    private String errorMessage;

    @Builder.Default
    private List<String> errors = List.of();

    @Builder.Default
    private List<String> warnings = List.of();

    /**
     * Check if materialization was successful.
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    /**
     * Get human-readable status message.
     */
    public String getStatusMessage() {
        if (isSuccess()) {
            return String.format("Successfully materialized %d triples in %d ms",
                    tripleCount, executionTimeMs);
        } else {
            return String.format("Materialization %s: %s", status,
                    errorMessage != null ? errorMessage : "Unknown error");
        }
    }
}
