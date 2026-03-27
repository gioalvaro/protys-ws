package ve.edu.uc.protys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ve.edu.uc.protys.dto.SPARQLRequest;
import ve.edu.uc.protys.dto.SPARQLResponse;
import ve.edu.uc.protys.exception.ProtysFusekiException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level service for Apache Jena Fuseki SPARQL endpoint communication.
 * Handles RDF model upload, SPARQL execution, and dataset management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FusekiService {

    @Value("${fuseki.endpoint:http://localhost:3030/protys}")
    private String fusekiEndpoint;

    @Value("${fuseki.query-endpoint:${fuseki.endpoint}/query}")
    private String queryEndpoint;

    @Value("${fuseki.update-endpoint:${fuseki.endpoint}/update}")
    private String updateEndpoint;

    @Value("${fuseki.gsp-endpoint:${fuseki.endpoint}/data}")
    private String gspEndpoint;

    /**
     * Loads an OWL file into a named graph in Fuseki.
     *
     * @param namedGraph the named graph URI
     * @param owlFile    the OWL file content as InputStream
     * @throws ProtysFusekiException if load fails
     */
    public void loadModel(String namedGraph, InputStream owlFile) {
        log.info("Loading OWL model into named graph: {}", namedGraph);
        long startTime = System.currentTimeMillis();

        try {
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, owlFile, Lang.RDFXML);

            try (RDFConnection conn = RDFConnectionFactory.connect(fusekiEndpoint)) {
                conn.load(namedGraph, model);
                long duration = System.currentTimeMillis() - startTime;
                log.info("Successfully loaded {} triples into {} in {}ms",
                        model.size(), namedGraph, duration);
            }
        } catch (Exception e) {
            log.error("Failed to load model into Fuseki: {}", namedGraph, e);
            throw new ProtysFusekiException("Failed to load OWL model: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a SPARQL query against Fuseki.
     *
     * @param request the SPARQL request DTO
     * @return SPARQLResponse with results and execution timing
     * @throws ProtysFusekiException if execution fails
     */
    public SPARQLResponse executeSPARQL(SPARQLRequest request) {
        log.debug("Executing SPARQL query: {}", request.getQueryText());
        long startTime = System.currentTimeMillis();

        try {
            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(
                    queryEndpoint, request.getQueryText())) {

                SPARQLResponse response = new SPARQLResponse();
                response.setQueryText(request.getQueryText());
                response.setExecutedAt(Instant.now());

                if (request.getQueryText().trim().toUpperCase().startsWith("SELECT")) {
                    ResultSet resultSet = qexec.execSelect();
                    List<Map<String, String>> results = new ArrayList<>();

                    while (resultSet.hasNext()) {
                        var solution = resultSet.nextSolution();
                        Map<String, String> row = new HashMap<>();
                        resultSet.getResultVars().forEach(varName ->
                                row.put(varName, solution.get(varName) != null ?
                                        solution.get(varName).toString() : null)
                        );
                        results.add(row);
                    }

                    response.setResults(results);
                    response.setResultCount(results.size());

                } else if (request.getQueryText().trim().toUpperCase().startsWith("CONSTRUCT")) {
                    Model resultModel = qexec.execConstruct();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    RDFDataMgr.write(baos, resultModel, Lang.JSONLD);
                    response.setConstructResult(baos.toString());
                    response.setResultCount((int) resultModel.size());

                } else if (request.getQueryText().trim().toUpperCase().startsWith("ASK")) {
                    boolean result = qexec.execAsk();
                    response.setAskResult(result);
                }

                long duration = System.currentTimeMillis() - startTime;
                response.setExecutionTimeMs(duration);
                log.info("SPARQL execution completed in {}ms, {} results",
                        duration, response.getResultCount());

                return response;
            }
        } catch (Exception e) {
            log.error("SPARQL execution failed", e);
            throw new ProtysFusekiException("SPARQL query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the RDF model for a named graph.
     *
     * @param namedGraph the named graph URI
     * @return Jena Model
     * @throws ProtysFusekiException if retrieval fails
     */
    public Model getModel(String namedGraph) {
        log.debug("Retrieving model from named graph: {}", namedGraph);

        try {
            try (RDFConnection conn = RDFConnectionFactory.connect(fusekiEndpoint)) {
                Model model = conn.fetch(namedGraph);
                log.debug("Retrieved {} triples from {}", model.size(), namedGraph);
                return model;
            }
        } catch (Exception e) {
            log.error("Failed to retrieve model from Fuseki: {}", namedGraph, e);
            throw new ProtysFusekiException("Failed to retrieve model: " + e.getMessage(), e);
        }
    }

    /**
     * Stores an RDF model into a named graph.
     *
     * @param namedGraph the named graph URI
     * @param model      the Jena Model to store
     * @throws ProtysFusekiException if store fails
     */
    public void putModel(String namedGraph, Model model) {
        log.info("Storing model into named graph: {} ({} triples)", namedGraph, model.size());

        try {
            try (RDFConnection conn = RDFConnectionFactory.connect(fusekiEndpoint)) {
                conn.put(namedGraph, model);
                log.info("Successfully stored model in {}", namedGraph);
            }
        } catch (Exception e) {
            log.error("Failed to store model in Fuseki: {}", namedGraph, e);
            throw new ProtysFusekiException("Failed to store model: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a named graph from Fuseki.
     *
     * @param namedGraph the named graph URI to delete
     * @throws ProtysFusekiException if deletion fails
     */
    public void deleteGraph(String namedGraph) {
        log.info("Deleting named graph: {}", namedGraph);

        try {
            try (RDFConnection conn = RDFConnectionFactory.connect(fusekiEndpoint)) {
                conn.delete(namedGraph);
                log.info("Successfully deleted named graph: {}", namedGraph);
            }
        } catch (Exception e) {
            log.error("Failed to delete named graph: {}", namedGraph, e);
            throw new ProtysFusekiException("Failed to delete graph: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves statistics about all named graphs in the dataset.
     *
     * @return Map of named graph URIs to triple counts
     */
    public Map<String, Long> getDatasetStats() {
        log.debug("Fetching dataset statistics");
        Map<String, Long> stats = new HashMap<>();

        String statsQuery = """
                SELECT ?g (COUNT(*) as ?count)
                WHERE {
                  GRAPH ?g { ?s ?p ?o }
                }
                GROUP BY ?g
                """;

        try {
            SPARQLRequest request = new SPARQLRequest();
            request.setQueryText(statsQuery);
            SPARQLResponse response = executeSPARQL(request);

            for (Map<String, String> row : response.getResults()) {
                String graphUri = row.get("g");
                Long count = Long.parseLong(row.get("count"));
                stats.put(graphUri, count);
            }

            log.debug("Dataset stats: {}", stats);
            return stats;
        } catch (Exception e) {
            log.warn("Failed to retrieve dataset statistics", e);
            return stats;
        }
    }

    /**
     * Lists all named graphs in the Fuseki dataset.
     *
     * @return List of named graph URIs
     */
    public List<String> listNamedGraphs() {
        log.debug("Listing all named graphs");
        List<String> graphs = new ArrayList<>();

        String listQuery = """
                SELECT DISTINCT ?g
                WHERE {
                  GRAPH ?g { ?s ?p ?o }
                }
                ORDER BY ?g
                """;

        try {
            SPARQLRequest request = new SPARQLRequest();
            request.setQueryText(listQuery);
            SPARQLResponse response = executeSPARQL(request);

            for (Map<String, String> row : response.getResults()) {
                String graphUri = row.get("g");
                if (graphUri != null) {
                    graphs.add(graphUri);
                }
            }

            log.debug("Found {} named graphs", graphs.size());
            return graphs;
        } catch (Exception e) {
            log.warn("Failed to list named graphs", e);
            return graphs;
        }
    }

    /**
     * Tests connectivity to Fuseki endpoint.
     *
     * @return true if Fuseki is reachable, false otherwise
     */
    public boolean testConnectivity() {
        log.debug("Testing Fuseki connectivity to {}", fusekiEndpoint);

        try {
            SPARQLRequest request = new SPARQLRequest();
            request.setQueryText("ASK { ?s ?p ?o }");
            executeSPARQL(request);
            log.info("Fuseki connectivity test successful");
            return true;
        } catch (Exception e) {
            log.error("Fuseki connectivity test failed", e);
            return false;
        }
    }

    /**
     * Clears all triples from a named graph.
     *
     * @param namedGraph the named graph URI to clear
     * @throws ProtysFusekiException if clear fails
     */
    public void clearGraph(String namedGraph) {
        log.info("Clearing named graph: {}", namedGraph);

        try {
            String clearQuery = String.format("CLEAR GRAPH <%s>", namedGraph);
            SPARQLRequest request = new SPARQLRequest();
            request.setQueryText(clearQuery);
            executeSPARQL(request);
            log.info("Successfully cleared graph: {}", namedGraph);
        } catch (Exception e) {
            log.error("Failed to clear graph: {}", namedGraph, e);
            throw new ProtysFusekiException("Failed to clear graph: " + e.getMessage(), e);
        }
    }
}
