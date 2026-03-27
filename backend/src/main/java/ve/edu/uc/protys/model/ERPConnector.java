package ve.edu.uc.protys.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for ERP system connectors.
 * Manages connections to external ERP systems (ADempiere, Odoo, etc.) for data materialization
 * into RDF triples using R2RML mappings.
 */
@Entity
@Table(name = "erp_connectors", indexes = {
        @Index(name = "idx_connector_name", columnList = "name", unique = true),
        @Index(name = "idx_connector_status", columnList = "status"),
        @Index(name = "idx_connector_type", columnList = "type"),
        @Index(name = "idx_named_graph", columnList = "named_graph", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ERPConnector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ERPType type;

    @Column(name = "jdbc_url", nullable = false, length = 500)
    private String jdbcUrl;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password", columnDefinition = "TEXT")
    private String password; // Should be encrypted at rest

    @Column(name = "database_name", length = 255)
    private String databaseName;

    @Column(name = "named_graph", nullable = false)
    private String namedGraph;

    @Column(name = "r2rml_mapping_path", columnDefinition = "TEXT")
    private String r2rmlMappingPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorStatus status;

    @Column(name = "last_materialization_at")
    private LocalDateTime lastMaterializationAt;

    @Column(name = "triple_count")
    private Long tripleCount;

    @Column(name = "materialization_time_ms")
    private Long materializationTimeMs;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Enumeration for ERP system types.
     */
    public enum ERPType {
        ADEMPIERE,
        ODOO,
        CUSTOM
    }

    /**
     * Enumeration for connector status states.
     */
    public enum ConnectorStatus {
        REGISTERED,       // Registered but not yet connected
        CONNECTED,        // Successfully connected to database
        MATERIALIZED,     // Data has been materialized to RDF
        ERROR             // Connection or materialization error
    }
}
