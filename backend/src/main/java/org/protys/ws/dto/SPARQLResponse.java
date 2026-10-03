package org.protys.ws.dto;

import lombok.*;

import java.time.Instant;
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

    private com.fasterxml.jackson.databind.JsonNode sparqlJson;

    private String queryText;

    private Instant executedAt;

    private List<String> columns;

    private List<Map<String, Object>> rows;

    /** SELECT bindings as variable/value maps. */
    private List<Map<String, String>> results;

    /** Serialized graph produced by CONSTRUCT/DESCRIBE queries. */
    private String constructResult;

    /** Boolean result of ASK queries. */
    private Boolean askResult;

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
