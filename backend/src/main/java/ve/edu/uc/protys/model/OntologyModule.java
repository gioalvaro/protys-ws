package ve.edu.uc.protys.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing an ontology module loaded in the PROTYS-KB system.
 * Each module corresponds to an RDF/OWL ontology loaded into the semantic web layer.
 */
@Entity
@Table(name = "ontology_modules", indexes = {
        @Index(name = "idx_base_uri", columnList = "base_uri", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_named_graph", columnList = "named_graph", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_uri", nullable = false)
    private String baseUri;

    @Column(name = "named_graph", nullable = false)
    private String namedGraph;

    @Column(name = "file_path")
    private String filePath;

    @Column(nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModuleStatus status;

    @Column(name = "class_count")
    private Integer classCount;

    @Column(name = "individual_count")
    private Integer individualCount;

    @Column(name = "triple_count")
    private Long tripleCount;

    @Column(name = "loaded_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime loadedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Enumeration for ontology module status.
     */
    public enum ModuleStatus {
        LOADED,
        VALIDATED,
        ERROR
    }
}
