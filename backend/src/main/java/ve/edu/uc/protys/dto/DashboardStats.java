package ve.edu.uc.protys.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for dashboard statistics and system overview.
 * Provides aggregated metrics across all ontology modules and connectors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

    private Integer totalModules;

    private Long totalClasses;

    private Long totalIndividuals;

    private Long totalTriples;

    private Integer activeAlignmentRules;

    private Long totalInferences;

    private Integer connectedERPs;

    private LocalDateTime lastActivity;

    private List<ModuleStatEntry> moduleStats;

    /**
     * Nested DTO for per-module statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleStatEntry {

        private String moduleName;

        private String baseUri;

        private String status;

        private Integer classCount;

        private Integer individualCount;

        private Long tripleCount;

        private LocalDateTime loadedAt;
    }

    /**
     * Calculate total classes safely (handling nulls).
     */
    public void recalculateTotals() {
        if (moduleStats != null && !moduleStats.isEmpty()) {
            this.totalClasses = moduleStats.stream()
                    .map(ModuleStatEntry::getClassCount)
                    .reduce(0, Integer::sum)
                    .longValue();

            this.totalIndividuals = moduleStats.stream()
                    .map(ModuleStatEntry::getIndividualCount)
                    .reduce(0, Integer::sum)
                    .longValue();

            this.totalTriples = moduleStats.stream()
                    .map(ModuleStatEntry::getTripleCount)
                    .reduce(0L, Long::sum);

            this.totalModules = moduleStats.size();
        }
    }
}
