package org.protys.ws.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for wizard standard incorporation results.
 * Tracks progress and status of standard integration into the ontology.
 * Each step (1-4) populates its own fields progressively.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardIncorporationResult {

    private UUID tempModuleId;

    private String standardName;

    private String status;
    private Map<String,Object> cleaningEvaluation;
    private Map<String,Object> contextEvaluation;
    private Map<String,Object> numericEvaluation;
    private String owlValidationStatus;

    // Pipeline tracking
    private Integer currentStep;
    private LocalDateTime startedAt;
    private Long executionTimeMs;
    private Integer tripleCount;
    private String message;
    private UUID finalModuleId;

    // Step 1: Upload & Parse
    private Boolean step1_parsed;
    private String step1_message;
    private Boolean step1_error;

    // Step 2: Consistency Validation
    private Boolean step2_consistent;
    private List<String> step2_conflictingModules;
    private String step2_message;
    private Boolean step2_error;

    // Step 3: Alignment Generation
    private Integer step3_rulesGenerated;
    private Integer step3_rulesCount;
    private String step3_message;
    private Boolean step3_error;

    // Step 4: Verification
    private Boolean step4_verified;
    private Integer step4_inferredTriples;
    private String step4_message;
    private Boolean step4_error;

    // Aggregate stats
    private Integer classesImported;

    private Integer propertiesImported;

    private Integer alignmentRulesCreated;

    private Integer inferencesGenerated;

    private Boolean consistencyCheck;

    private List<String> warnings;

    private List<String> errors;

    private LocalDateTime completedAt;
}
