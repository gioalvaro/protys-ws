package ve.edu.uc.protys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ve.edu.uc.protys.dto.InferenceStats;
import ve.edu.uc.protys.model.AlignmentRule;
import ve.edu.uc.protys.service.AlignmentService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/alignment")
@Tag(name = "Alignment", description = "Ontology alignment rules and reasoning execution")
@RequiredArgsConstructor
public class AlignmentController {

    private final AlignmentService alignmentService;

    // Rule Management Endpoints

    @GetMapping("/rules")
    @Operation(summary = "List all alignment rules", description = "Retrieve all defined alignment rules (active and inactive)")
    public ResponseEntity<List<AlignmentRule>> getAllRules() {
        log.info("Fetching all alignment rules");
        try {
            List<AlignmentRule> rules = alignmentService.getAllRules();
            log.debug("Retrieved {} alignment rules", rules.size());
            return ResponseEntity.ok(rules);
        } catch (Exception e) {
            log.error("Error retrieving alignment rules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/rules/active")
    @Operation(summary = "List active alignment rules", description = "Retrieve only the currently active alignment rules")
    public ResponseEntity<List<AlignmentRule>> getActiveRules() {
        log.info("Fetching active alignment rules");
        try {
            List<AlignmentRule> activeRules = alignmentService.getActiveRules();
            log.debug("Retrieved {} active alignment rules", activeRules.size());
            return ResponseEntity.ok(activeRules);
        } catch (Exception e) {
            log.error("Error retrieving active alignment rules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/rules/upload")
    @Operation(summary = "Upload SWRL rules file", description = "Upload a file containing SWRL alignment rules")
    public ResponseEntity<List<AlignmentRule>> uploadRulesFile(@RequestParam("file") MultipartFile file) {
        log.info("Uploading SWRL rules file");
        try {
            if (file.isEmpty()) {
                log.warn("Empty file upload attempted");
                return ResponseEntity.badRequest().build();
            }

            if (!isValidRulesFile(file.getOriginalFilename())) {
                log.warn("Invalid file type: {}", file.getOriginalFilename());
                return ResponseEntity.badRequest().build();
            }

            List<AlignmentRule> uploadedRules = alignmentService.loadAlignmentRules(file);
            log.info("Rules file uploaded successfully with {} rules", uploadedRules.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(uploadedRules);
        } catch (Exception e) {
            log.error("Error uploading rules file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/rules/{id}/toggle")
    @Operation(summary = "Toggle rule activation", description = "Enable or disable a specific alignment rule")
    public ResponseEntity<AlignmentRule> toggleRuleActivation(
            @PathVariable UUID id,
            @RequestParam("active") boolean active) {
        log.info("Toggling rule {} activation to: {}", id, active);
        try {
            AlignmentRule updatedRule = alignmentService.toggleRule(id, active);
            log.info("Rule activation toggled successfully");
            return ResponseEntity.ok(updatedRule);
        } catch (Exception e) {
            log.error("Error toggling rule activation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/rules/{id}/validate")
    @Operation(summary = "Validate alignment rule", description = "Validate the syntax and consistency of a specific alignment rule")
    public ResponseEntity<Map<String, Object>> validateRule(@PathVariable UUID id) {
        log.info("Validating alignment rule: {}", id);
        try {
            boolean isValid = alignmentService.validateAlignment(id);
            Map<String, Object> validationResult = Map.of(
                    "ruleId", id.toString(),
                    "valid", isValid,
                    "timestamp", java.time.LocalDateTime.now()
            );
            log.debug("Rule validation completed");
            return ResponseEntity.ok(validationResult);
        } catch (Exception e) {
            log.error("Error validating rule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Reasoning Execution Endpoints

    @PostMapping("/reasoning/execute")
    @Operation(summary = "Execute reasoning engine", description = "Trigger the reasoning engine to apply all active alignment rules")
    public ResponseEntity<InferenceStats> executeReasoning() {
        log.info("Executing alignment reasoning");
        try {
            InferenceStats result = alignmentService.executeReasoning();
            log.info("Reasoning execution completed successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("Error executing reasoning", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reasoning/stats")
    @Operation(summary = "Get inference statistics", description = "Retrieve statistics about the inference process including new inferences and conflicts")
    public ResponseEntity<InferenceStats> getReasoningStats() {
        log.info("Fetching inference statistics");
        try {
            InferenceStats stats = alignmentService.getInferenceStats();
            log.debug("Inference statistics retrieved");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving reasoning statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper Methods

    private boolean isValidRulesFile(String filename) {
        return filename != null && (filename.endsWith(".swrl") ||
                filename.endsWith(".owl") ||
                filename.endsWith(".rdf") ||
                filename.endsWith(".txt"));
    }
}
