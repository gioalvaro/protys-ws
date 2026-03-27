package ve.edu.uc.protys.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for reasoning/inference statistics.
 * Returned by AlignmentService.executeReasoning() and getInferenceStats().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceStats {

    private Integer totalRules;

    private Integer activeRules;

    private Long totalInferences;

    private Long reasoningTimeMs;

    private Long executionTimeMs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime lastExecutedAt;

    private Integer inputTripleCount;

    private Integer inferredTripleCount;

    private Integer newClassCount;

    private Integer newIndividualCount;

    private String status;

    private String errorMessage;

    private List<RuleResult> ruleResults;

    /**
     * Per-rule inference result details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResult {

        private String ruleId;

        private String ruleName;

        private Integer inferenceCount;

        private Long executionTimeMs;

        private String status;
    }
}
