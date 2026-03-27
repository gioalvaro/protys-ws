package ve.edu.uc.protys.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.edu.uc.protys.model.SPARQLQuery;
import ve.edu.uc.protys.dto.SPARQLRequest;
import ve.edu.uc.protys.dto.SPARQLResponse;
import ve.edu.uc.protys.exception.ProtysFusekiException;
import ve.edu.uc.protys.repository.SPARQLQueryRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SPARQL query management service.
 * Handles query execution, templating, and result export.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SPARQLService {

    private final FusekiService fusekiService;
    private final SPARQLQueryRepository sparqlQueryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Executes a SPARQL query.
     *
     * @param request the SPARQL request DTO
     * @return SPARQLResponse with results
     * @throws ProtysFusekiException if execution fails
     */
    @Transactional(readOnly = true)
    public SPARQLResponse executeQuery(SPARQLRequest request) {
        log.info("Executing SPARQL query");
        long startTime = System.currentTimeMillis();

        try {
            validateQuery(request.getQuery());
            SPARQLResponse response = fusekiService.executeSPARQL(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Query executed successfully in {}ms, {} results",
                    duration, response.getResultCount());

            return response;
        } catch (ProtysFusekiException e) {
            log.error("SPARQL execution failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Query validation or execution failed", e);
            throw new ProtysFusekiException("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates SPARQL query syntax.
     *
     * @param queryText the SPARQL query string
     * @throws IllegalArgumentException if syntax is invalid
     */
    public void validateQuery(String queryText) {
        log.debug("Validating SPARQL query");

        try {
            Query query = QueryFactory.create(queryText);
            log.debug("Query validation passed, type: {}", query.getQueryType());
        } catch (Exception e) {
            log.error("Query validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid SPARQL syntax: " + e.getMessage(), e);
        }
    }

    /**
     * Saves a SPARQL query template to the repository.
     *
     * @param template the SPARQLQuery entity
     * @return saved SPARQLQuery
     */
    @Transactional
    public SPARQLQuery saveTemplate(SPARQLQuery template) {
        log.info("Saving SPARQL query template: {}", template.getName());

        try {
            validateQuery(template.getQueryText());
            template.setId(UUID.randomUUID());
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());

            SPARQLQuery saved = sparqlQueryRepository.save(template);
            log.info("Template saved with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to save query template", e);
            throw new ProtysFusekiException("Failed to save query template: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all saved query templates.
     *
     * @return List of SPARQLQuery templates
     */
    @Transactional(readOnly = true)
    public List<SPARQLQuery> getTemplates() {
        log.debug("Fetching all SPARQL query templates");

        List<SPARQLQuery> templates = sparqlQueryRepository.findAll();
        log.debug("Found {} templates", templates.size());
        return templates;
    }

    /**
     * Retrieves competency questions (CQ1-CQ5).
     *
     * @return List of SPARQLQuery competency question templates
     */
    @Transactional(readOnly = true)
    public List<SPARQLQuery> getCompetencyQueries() {
        log.debug("Fetching competency question queries");

        List<SPARQLQuery> allQueries = sparqlQueryRepository.findAll();
        List<SPARQLQuery> competencyQueries = allQueries.stream()
                .filter(q -> q.getName() != null && q.getName().matches("CQ[1-5].*"))
                .collect(Collectors.toList());

        log.debug("Found {} competency questions", competencyQueries.size());
        return competencyQueries;
    }

    /**
     * Exports query results to specified format.
     *
     * @param response the SPARQLResponse
     * @param format   the export format (CSV, JSON_LD)
     * @return String representation of exported results
     * @throws IOException if export fails
     */
    public String exportResults(SPARQLResponse response, String format) {
        log.info("Exporting SPARQL results to format: {}", format);

        try {
            return switch (format.toUpperCase()) {
                case "CSV" -> exportToCSV(response);
                case "JSON" -> exportToJSON(response);
                case "JSONLD" -> exportToJSONLD(response);
                case "XML" -> exportToXML(response);
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };
        } catch (Exception e) {
            log.error("Failed to export results", e);
            throw new ProtysFusekiException("Failed to export results: " + e.getMessage(), e);
        }
    }

    /**
     * Exports results to CSV format.
     */
    private String exportToCSV(SPARQLResponse response) {
        log.debug("Exporting to CSV");

        StringBuilder csv = new StringBuilder();

        if (!response.getRows().isEmpty()) {
            Map<String, Object> firstRow = response.getRows().get(0);
            csv.append(String.join(",", firstRow.keySet())).append("\n");

            for (Map<String, Object> row : response.getRows()) {
                csv.append(row.values().stream()
                        .map(v -> v != null ? v.toString() : "")
                        .collect(java.util.stream.Collectors.joining(",")))
                        .append("\n");
            }
        }

        return csv.toString();
    }

    /**
     * Exports results to JSON format.
     */
    private String exportToJSON(SPARQLResponse response) throws IOException {
        log.debug("Exporting to JSON");

        Map<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("columns", response.getColumns());
        jsonOutput.put("results", response.getRows());
        jsonOutput.put("resultCount", response.getResultCount());
        jsonOutput.put("executionTimeMs", response.getExecutionTimeMs());

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput);
    }

    /**
     * Exports results to JSON-LD format.
     */
    private String exportToJSONLD(SPARQLResponse response) {
        log.debug("Exporting to JSON-LD");

        // Create a minimal JSON-LD context
        Map<String, Object> jsonLd = new HashMap<>();
        jsonLd.put("@context", Map.of(
                "@vocab", "http://protys.ontology/",
                "results", "http://protys.ontology/results"
        ));
        jsonLd.put("@id", "http://protys.query/" + UUID.randomUUID());
        jsonLd.put("results", response.getRows());

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLd);
        } catch (IOException e) {
            log.error("Failed to serialize JSON-LD", e);
            return "{}";
        }
    }

    /**
     * Exports results to XML format.
     */
    private String exportToXML(SPARQLResponse response) {
        log.debug("Exporting to XML");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<sparql xmlns=\"http://www.w3.org/2005/sparql-results#\">\n");
        xml.append("  <head>\n");

        if (!response.getRows().isEmpty()) {
            Map<String, Object> firstRow = response.getRows().get(0);
            for (String variable : firstRow.keySet()) {
                xml.append("    <variable name=\"").append(variable).append("\"/>\n");
            }
        }

        xml.append("  </head>\n");
        xml.append("  <results>\n");

        for (Map<String, Object> row : response.getRows()) {
            xml.append("    <result>\n");
            for (Map.Entry<String, Object> binding : row.entrySet()) {
                xml.append("      <binding name=\"").append(binding.getKey()).append("\">\n");
                if (binding.getValue() != null) {
                    xml.append("        <literal>").append(escapeXml(binding.getValue().toString())).append("</literal>\n");
                }
                xml.append("      </binding>\n");
            }
            xml.append("    </result>\n");
        }

        xml.append("  </results>\n");
        xml.append("</sparql>");

        return xml.toString();
    }

    /**
     * Escapes XML special characters.
     */
    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Gets query execution history (last N queries).
     *
     * @param limit maximum number of queries to return
     * @return List of recently executed queries
     */
    @Transactional(readOnly = true)
    public List<SPARQLQuery> getExecutionHistory(int limit) {
        log.debug("Fetching execution history (limit: {})", limit);

        List<SPARQLQuery> queries = sparqlQueryRepository.findAll();
        return queries.stream()
                .sorted((q1, q2) -> q2.getUpdatedAt().compareTo(q1.getUpdatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing query template.
     *
     * @param queryId the query ID
     * @param updated the updated SPARQLQuery
     * @return updated SPARQLQuery
     */
    @Transactional
    public SPARQLQuery updateTemplate(UUID queryId, SPARQLQuery updated) {
        log.info("Updating query template: {}", queryId);

        try {
            validateQuery(updated.getQueryText());

            SPARQLQuery existing = sparqlQueryRepository.findById(queryId)
                    .orElseThrow(() -> new ProtysFusekiException("Query not found: " + queryId));

            existing.setName(updated.getName());
            existing.setQueryText(updated.getQueryText());
            existing.setDescription(updated.getDescription());
            existing.setUpdatedAt(LocalDateTime.now());

            SPARQLQuery saved = sparqlQueryRepository.save(existing);
            log.info("Query template updated: {}", queryId);
            return saved;
        } catch (Exception e) {
            log.error("Failed to update query template: {}", queryId, e);
            throw new ProtysFusekiException("Failed to update template: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a query template.
     *
     * @param queryId the query ID
     */
    @Transactional
    public void deleteTemplate(UUID queryId) {
        log.info("Deleting query template: {}", queryId);

        sparqlQueryRepository.deleteById(queryId);
        log.info("Query template deleted: {}", queryId);
    }
}
