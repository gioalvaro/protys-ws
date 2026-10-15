package org.protys.ws.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/** Durable ownership record. Temporary wizard graphs are never global ontology modules. */
@Entity
@Table(name = "standard_incorporation_sessions")
@Data
@NoArgsConstructor
public class StandardIncorporationSession {
    @Id
    private UUID id;
    @Version
    private Long revision;
    @Column(nullable = false)
    private String standardName;
    @Column(nullable = false, unique = true)
    private String temporaryGraph;
    @Column(nullable = false, unique = true)
    private String permanentGraph;
    @Column(nullable = false)
    private String phase;
    @Column(columnDefinition = "TEXT")
    private String selectedRuleIds;
    @Column(columnDefinition = "TEXT")
    private String executedRulesJson;
    @Column(columnDefinition = "TEXT")
    private String ruleDefinitionsSnapshot;
    private String ruleDefinitionsSha256;
    private boolean permanentWriteStarted;
    private boolean consistencyVerified;
    private boolean inferenceVerified;
    private Integer tripleCount;
    private Integer inferredTriples;
    private UUID finalModuleId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
