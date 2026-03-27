package ve.edu.uc.protys.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for PROTYS application.
 * Loads and validates configuration from application.yml/properties with prefix "protys".
 */
@Configuration
@ConfigurationProperties(prefix = "protys")
@Validated
@Data
@NoArgsConstructor
public class ProtysFusekiConfig {

    /**
     * Fuseki triplestore configuration.
     */
    @Valid
    @NotNull
    private FusekiConfig fuseki;

    /**
     * Ontology configuration.
     */
    @Valid
    @NotNull
    private OntologyConfig ontologies;

    /**
     * Reasoning engine configuration.
     */
    @Valid
    @NotNull
    private ReasoningConfig reasoning;

    /**
     * R2RML mapping configuration.
     */
    @Valid
    @NotNull
    private R2RMLConfig r2rml;

    /**
     * SPARQL query configuration.
     */
    @Valid
    @NotNull
    private QueryConfig query;

    /**
     * Fuseki triplestore endpoint configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FusekiConfig {

        /**
         * Fuseki server endpoint URL.
         */
        @NotBlank(message = "Fuseki endpoint must not be blank")
        private String endpoint;

        /**
         * Default dataset name.
         */
        @NotBlank(message = "Fuseki dataset must not be blank")
        private String dataset;
    }

    /**
     * Ontology configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OntologyConfig {

        /**
         * Base URI for ontology resources.
         */
        @NotBlank(message = "Ontology base URI must not be blank")
        private String baseUri;

        /**
         * File system path where ontology files are stored.
         */
        @NotBlank(message = "Ontology path must not be blank")
        private String path;
    }

    /**
     * Reasoning engine configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReasoningConfig {

        /**
         * Reasoning engine type (e.g., RDFS, OWL, CUSTOM).
         */
        @NotBlank(message = "Reasoning engine must not be blank")
        private String engine;

        /**
         * Query timeout in seconds.
         */
        @NotNull(message = "Reasoning timeout must not be null")
        private Integer timeout;
    }

    /**
     * R2RML mapping configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class R2RMLConfig {

        /**
         * File system path where R2RML mapping files are stored.
         */
        @NotBlank(message = "R2RML path must not be blank")
        private String path;
    }

    /**
     * SPARQL query configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QueryConfig {

        /**
         * Query execution timeout in seconds.
         */
        @NotNull(message = "Query timeout must not be null")
        private Integer timeout;

        /**
         * Default page size for paginated query results.
         */
        @NotNull(message = "Query page size must not be null")
        private Integer pageSize;
    }
}
