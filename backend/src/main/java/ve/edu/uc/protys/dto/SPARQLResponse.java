package ve.edu.uc.protys.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * DTO for SPARQL query results.
 * Contains the result columns, rows of data, and execution metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SPARQLResponse {

    private List<String> columns;

    private List<Map<String, Object>> rows;

    private Long executionTimeMs;

    private Integer resultCount;

    @Builder.Default
    private Boolean truncated = false;

    /**
     * Alternative constructor for error responses.
     */
    public static SPARQLResponse error(String message, Long executionTimeMs) {
        return SPARQLResponse.builder()
                .columns(List.of("error"))
                .rows(List.of(Map.of("error", message)))
                .executionTimeMs(executionTimeMs)
                .resultCount(0)
                .build();
    }
}
