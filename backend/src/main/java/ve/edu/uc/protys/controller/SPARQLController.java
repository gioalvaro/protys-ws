package ve.edu.uc.protys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ve.edu.uc.protys.dto.SPARQLRequest;
import ve.edu.uc.protys.dto.SPARQLResponse;
import ve.edu.uc.protys.model.SPARQLQuery;
import ve.edu.uc.protys.service.SPARQLService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sparql")
@Tag(name = "SPARQL", description = "SPARQL query execution and management")
@RequiredArgsConstructor
public class SPARQLController {

    private final SPARQLService sparqlService;

    // Query Execution Endpoints

    @PostMapping("/execute")
    @Operation(summary = "Execute SPARQL query", description = "Execute a SPARQL query and return results")
    public ResponseEntity<SPARQLResponse> executeSPARQLQuery(@RequestBody SPARQLRequest request) {
        log.info("Executing SPARQL query");
        try {
            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                log.warn("Empty SPARQL query provided");
                return ResponseEntity.badRequest().build();
            }

            SPARQLResponse response = sparqlService.executeQuery(request);
            log.debug("SPARQL query executed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate SPARQL query syntax", description = "Validate the syntax of a SPARQL query without executing it")
    public ResponseEntity<Map<String, Object>> validateSPARQLQuery(@RequestBody SPARQLRequest request) {
        log.info("Validating SPARQL query syntax");
        try {
            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                log.warn("Empty SPARQL query provided for validation");
                return ResponseEntity.badRequest().build();
            }

            sparqlService.validateQuery(request.getQuery());
            Map<String, Object> validationResult = Map.of(
                    "valid", true,
                    "message", "Query syntax is valid"
            );
            log.debug("SPARQL query validation completed");
            return ResponseEntity.ok(validationResult);
        } catch (IllegalArgumentException e) {
            log.error("Query syntax validation failed", e);
            Map<String, Object> errorResult = Map.of(
                    "valid", false,
                    "message", e.getMessage()
            );
            return ResponseEntity.ok(errorResult);
        } catch (Exception e) {
            log.error("Error validating SPARQL query", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Template Management Endpoints

    @GetMapping("/templates")
    @Operation(summary = "List saved query templates", description = "Retrieve all saved SPARQL query templates")
    public ResponseEntity<List<SPARQLQuery>> getQueryTemplates() {
        log.info("Fetching all query templates");
        try {
            List<SPARQLQuery> templates = sparqlService.getTemplates();
            log.debug("Retrieved {} templates", templates.size());
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error retrieving query templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/templates/competency")
    @Operation(summary = "Get competency question templates", description = "Retrieve CQ1-CQ5 competency question templates")
    public ResponseEntity<List<SPARQLQuery>> getCompetencyQuestionTemplates() {
        log.info("Fetching competency question templates");
        try {
            List<SPARQLQuery> cqTemplates = sparqlService.getCompetencyQueries();
            log.debug("Retrieved {} competency questions", cqTemplates.size());
            return ResponseEntity.ok(cqTemplates);
        } catch (Exception e) {
            log.error("Error retrieving competency question templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/templates")
    @Operation(summary = "Save new query template", description = "Create and save a new SPARQL query template")
    public ResponseEntity<SPARQLQuery> saveTemplate(@RequestBody SPARQLQuery template) {
        log.info("Saving new query template");
        try {
            if (template.getName() == null || template.getName().trim().isEmpty() ||
                    template.getQueryText() == null || template.getQueryText().trim().isEmpty()) {
                log.warn("Invalid template data: missing name or query");
                return ResponseEntity.badRequest().build();
            }

            SPARQLQuery savedTemplate = sparqlService.saveTemplate(template);
            log.info("Template saved successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(savedTemplate);
        } catch (Exception e) {
            log.error("Error saving template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Export Endpoint

    @PostMapping("/export")
    @Operation(summary = "Export query results", description = "Export SPARQL query results in specified format (CSV, JSON, JSONLD, XML)")
    public ResponseEntity<String> exportResults(
            @RequestParam("format") String format,
            @RequestBody SPARQLResponse response) {
        log.info("Exporting results in format: {}", format);
        try {
            if (!isValidExportFormat(format)) {
                log.warn("Invalid export format: {}", format);
                return ResponseEntity.badRequest().build();
            }

            String exportedData = sparqlService.exportResults(response, format);

            log.debug("Results exported successfully in {} format", format);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentType(format))
                    .header("Content-Disposition", "attachment; filename=\"results." + format.toLowerCase() + "\"")
                    .body(exportedData);
        } catch (Exception e) {
            log.error("Error exporting results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper Methods

    private boolean isValidExportFormat(String format) {
        return format != null && (format.equalsIgnoreCase("CSV") ||
                format.equalsIgnoreCase("JSON") ||
                format.equalsIgnoreCase("JSONLD") ||
                format.equalsIgnoreCase("XML"));
    }

    private String getContentType(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "XML" -> "application/xml";
            case "JSON", "JSONLD" -> "application/json";
            default -> "text/plain";
        };
    }
}
