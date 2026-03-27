package ve.edu.uc.protys.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for SWRL (Semantic Web Rule Language) alignment rules.
 * Rules enable interoperability between different ontology modules and inference capabilities.
 */
@Entity
@Table(name = "alignment_rules", indexes = {
        @Index(name = "idx_rule_id", columnList = "rule_id", unique = true),
        @Index(name = "idx_source_module", columnList = "source_module_id"),
        @Index(name = "idx_target_module", columnList = "target_module_id"),
        @Index(name = "idx_rule_type", columnList = "rule_type"),
        @Index(name = "idx_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false, length = 50)
    private String ruleId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_module_id", nullable = false)
    private OntologyModule sourceModule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_module_id", nullable = false)
    private OntologyModule targetModule;

    @Column(name = "swrl_expression", columnDefinition = "TEXT", nullable = false)
    private String swrlExpression;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "inference_count")
    @Builder.Default
    private Integer inferenceCount = 0;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Enumeration for alignment rule types.
     */
    public enum RuleType {
        BRIDGE,           // Rules bridging between modules
        CORRESPONDENCE,   // Correspondence rules between equivalent concepts
        INFERENCE         // Inference rules deriving new knowledge
    }
}
