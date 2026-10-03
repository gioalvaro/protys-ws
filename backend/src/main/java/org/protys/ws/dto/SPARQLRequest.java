package org.protys.ws.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * DTO for SPARQL query execution requests.
 * Encapsulates query parameters, graph URIs, and execution options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SPARQLRequest {

    @NotBlank(message = "SPARQL query text is required")
    private String query;

    private String defaultGraphUri;

    private List<String> namedGraphUris;

    @Builder.Default
    private Integer timeout = 30; // seconds

    @Builder.Default
    private ResultFormat format = ResultFormat.JSON;

    /**
     * Alias for the query field, kept for service-layer compatibility.
     */
    public String getQueryText() {
        return query;
    }

    /**
     * Alias for the query field, kept for service-layer compatibility.
     */
    public void setQueryText(String queryText) {
        this.query = queryText;
    }

    /**
     * Enumeration for SPARQL result output formats.
     */
    public enum ResultFormat {
        JSON,     // JSON result format
        CSV,      // CSV result format
        XML,      // SPARQL XML result format
        JSONLD    // JSON-LD result format
    }
}
