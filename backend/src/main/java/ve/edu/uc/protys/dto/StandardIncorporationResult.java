package ve.edu.uc.protys.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
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

    // Step 1: Upload & Parse
    private Boolean step1_parsed;
    private String step1_message;
    private Boolean step1_error;

    // Step 2: Consistency Validation
    private Boolean step2_consistent;
    private List<String> step2_conflictingModules;
    private String step2_message;

    // Step 3: Alignment Generation
    private Integer step3_rulesGenerated;
    private String step3_message;

    // Step 4: Verification
    private Boolean step4_verified;
    private String step4_message;

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
