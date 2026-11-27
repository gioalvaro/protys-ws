package org.protys.ws.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.protys.ws.model.SPARQLQuery;
import org.protys.ws.dto.SPARQLRequest;
import org.protys.ws.dto.SPARQLResponse;
import org.protys.ws.exception.ProtysFusekiException;
import org.protys.ws.repository.SPARQLQueryRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                .filter(q -> q.getName() != null && q.getCompetencyQuestion() != null && q.getCompetencyQuestion().matches(".*P[1-5].*"))
                .collect(Collectors.toList());

        log.debug("Found {} competency questions", competencyQueries.size());
        return competencyQueries;
    }

    /**
     * Exports query results to specified format.
     *
     * @param response the SPARQLResponse
     * @param format   the supported export format (CSV or complete typed JSON)
     * @return String representation of exported results
     * @throws IOException if export fails
     */
    public String exportResults(SPARQLResponse response, String format) {
        log.info("Exporting SPARQL results to format: {}", format);

        try {
            return switch (format.toUpperCase()) {
                case "CSV" -> exportToCSV(response);
                case "JSON" -> exportToJSON(response);
                default -> throw new IllegalArgumentException("Unsupported format: " + format + ". Supported formats: CSV, JSON.");
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
        if (response.getAskResult() != null) {
            return "askResult\r\n" + response.getAskResult() + "\r\n";
        }
        List<Map<String, Object>> rows = exportRows(response);
        List<String> columns = exportColumns(response, rows);
        StringBuilder csv = new StringBuilder();
        if (!columns.isEmpty()) {
            csv.append(columns.stream().map(this::csvField).collect(Collectors.joining(",")))
                    .append("\r\n");
            for (Map<String, Object> row : rows) {
                csv.append(columns.stream().map(column -> csvField(row.get(column)))
                        .collect(Collectors.joining(","))).append("\r\n");
            }
        }
        return csv.toString();
    }

    private String csvField(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** SELECT responses from the execution API carry typed bindings, not necessarily rows. */
    private List<Map<String, Object>> exportRows(SPARQLResponse response) {
        JsonNode bindings = response.getSparqlJson() == null ? null
                : response.getSparqlJson().path("results").path("bindings");
        if (bindings != null && bindings.isArray()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode binding : bindings) {
                Map<String, Object> row = new LinkedHashMap<>();
                binding.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue().get("value");
                    row.put(entry.getKey(), value == null || value.isNull() ? null : value.asText());
                });
                rows.add(row);
            }
            return rows;
        }
        if (response.getRows() != null) return response.getRows();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (response.getResults() != null) {
            response.getResults().forEach(row -> rows.add(new LinkedHashMap<>(row)));
        }
        return rows;
    }

    private List<String> exportColumns(SPARQLResponse response, List<Map<String, Object>> rows) {
        if (response.getColumns() != null) return response.getColumns();
        JsonNode variables = response.getSparqlJson() == null ? null
                : response.getSparqlJson().path("head").path("vars");
        if (variables != null && variables.isArray()) {
            List<String> columns = new ArrayList<>();
            variables.forEach(variable -> columns.add(variable.asText()));
            return columns;
        }
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        rows.forEach(row -> columns.addAll(row.keySet()));
        return new ArrayList<>(columns);
    }

    /**
     * Exports results to JSON format.
     */
    private String exportToJSON(SPARQLResponse response) throws IOException {
        log.debug("Exporting to JSON");

        // Preserve the complete response, including RDF term types, datatypes and languages.
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
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
