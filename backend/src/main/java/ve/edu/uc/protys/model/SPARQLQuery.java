package ve.edu.uc.protys.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for saved SPARQL query templates.
 * Enables users to save, categorize, and reuse SPARQL queries with parameter support.
 * Tracks competency questions (e.g., "CQ1", "CQ2") for ontology validation.
 */
@Entity
@Table(name = "sparql_queries", indexes = {
        @Index(name = "idx_query_name", columnList = "name"),
        @Index(name = "idx_query_type", columnList = "query_type"),
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_competency_question", columnList = "competency_question")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SPARQLQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "query_text", columnDefinition = "TEXT", nullable = false)
    private String queryText;

    @Enumerated(EnumType.STRING)
    @Column(name = "query_type", nullable = false)
    private QueryType queryType;

    @Column(name = "competency_question", length = 50)
    private String competencyQuestion;

    @Column(name = "is_template")
    @Builder.Default
    private Boolean isTemplate = false;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters; // JSON string with parameter definitions

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "execution_count")
    @Builder.Default
    private Integer executionCount = 0;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Enumeration for SPARQL query types.
     */
    public enum QueryType {
        SELECT,    // SELECT queries returning variable bindings
        CONSTRUCT, // CONSTRUCT queries returning RDF graphs
        ASK,       // ASK queries returning boolean results
        DESCRIBE   // DESCRIBE queries returning RDF descriptions
    }
}
