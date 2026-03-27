package ve.edu.uc.protys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.edu.uc.protys.model.ERPConnector;
import ve.edu.uc.protys.dto.MaterializationResult;
import ve.edu.uc.protys.dto.SchemaMetadata;
import ve.edu.uc.protys.exception.ProtysFusekiException;
import ve.edu.uc.protys.repository.ERPConnectorRepository;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ERP integration service using R2RML mapping.
 * Handles relational data materialization into RDF triples.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ERPConnectorService {

    private final FusekiService fusekiService;
    private final ERPConnectorRepository erpConnectorRepository;
    private final DataSource dataSource;

    /**
     * Registers a new ERP connector configuration.
     */
    @Transactional
    public ERPConnector registerConnector(ERPConnector connector) {
        log.info("Registering ERP connector: {}", connector.getName());

        try {
            connector.setId(UUID.randomUUID());
            connector.setStatus(ERPConnector.ConnectorStatus.REGISTERED);
            connector.setCreatedAt(LocalDateTime.now());
            connector.setUpdatedAt(LocalDateTime.now());

            ERPConnector saved = erpConnectorRepository.save(connector);
            log.info("Connector registered with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("Failed to register connector", e);
            throw new ProtysFusekiException("Failed to register connector: " + e.getMessage(), e);
        }
    }

    /**
     * Tests JDBC connectivity to the ERP database.
     */
    @Transactional(readOnly = true)
    public boolean testConnection(UUID connectorId) {
        log.info("Testing connection for connector: {}", connectorId);

        ERPConnector connector = erpConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

        try {
            java.sql.DriverManager.getConnection(
                    connector.getJdbcUrl(),
                    connector.getUsername(),
                    connector.getPassword()
            ).close();

            log.info("Connection test successful for connector: {}", connectorId);
            return true;
        } catch (Exception e) {
            log.error("Connection test failed for connector: {}", connectorId, e);
            return false;
        }
    }

    /**
     * Introspects the remote database schema.
     */
    @Transactional(readOnly = true)
    public Map<String, List<SchemaMetadata>> introspectSchema(UUID connectorId) {
        log.info("Introspecting schema for connector: {}", connectorId);

        ERPConnector connector = erpConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

        Map<String, List<SchemaMetadata>> schema = new HashMap<>();

        try (Connection conn = java.sql.DriverManager.getConnection(
                connector.getJdbcUrl(),
                connector.getUsername(),
                connector.getPassword())) {

            DatabaseMetaData metadata = conn.getMetaData();
            ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                List<SchemaMetadata> columns = new ArrayList<>();

                ResultSet columnsRs = metadata.getColumns(null, null, tableName, null);
                while (columnsRs.next()) {
                    SchemaMetadata col = new SchemaMetadata();
                    col.setName(columnsRs.getString("COLUMN_NAME"));
                    col.setType(columnsRs.getString("TYPE_NAME"));
                    col.setNullable(columnsRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    columns.add(col);
                }
                columnsRs.close();

                schema.put(tableName, columns);
                log.debug("Found table: {} with {} columns", tableName, columns.size());
            }
            tables.close();

            log.info("Schema introspection found {} tables", schema.size());
            return schema;

        } catch (Exception e) {
            log.error("Failed to introspect schema for connector: {}", connectorId, e);
            throw new ProtysFusekiException("Failed to introspect schema: " + e.getMessage(), e);
        }
    }

    /**
     * Materializes RDF triples from ERP database using R2RML mapping.
     */
    @Transactional
    public MaterializationResult materialize(UUID connectorId) {
        log.info("Starting materialization for connector: {}", connectorId);
        long startTime = System.currentTimeMillis();

        MaterializationResult result = new MaterializationResult();
        result.setConnectorId(connectorId);
        result.setStartedAt(LocalDateTime.now());

        try {
            ERPConnector connector = erpConnectorRepository.findById(connectorId)
                    .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

            log.debug("Step 1: Loaded connector configuration: {}", connector.getName());

            // Step 2: Establish JDBC connection
            Connection dbConnection = java.sql.DriverManager.getConnection(
                    connector.getJdbcUrl(),
                    connector.getUsername(),
                    connector.getPassword()
            );
            log.info("Step 2: JDBC connection established to {}", connector.getType());

            // Step 3: Load R2RML mapping
            String r2rmlContent = loadR2RMLMapping(connector);
            log.debug("Step 3: R2RML mapping loaded ({} bytes)", r2rmlContent.length());

            // Step 4: Create RDF model
            Model rdfModel = ModelFactory.createDefaultModel();
            rdfModel.setNsPrefix("protys", "http://protys.ontology/");
            rdfModel.setNsPrefix("erp", "http://protys.erp/");

            // Step 5: Execute SQL queries and generate triples
            int tripleCount = executeMappingAndGenerateTriples(
                    dbConnection, connector, r2rmlContent, rdfModel);

            log.info("Step 4-5: Generated {} RDF triples", tripleCount);

            // Step 6: Store resulting model in Fuseki
            String namedGraphUri = connector.getNamedGraph();
            fusekiService.putModel(namedGraphUri, rdfModel);
            log.info("Step 6: Stored model in Fuseki named graph: {}", namedGraphUri);

            // Step 7: Prepare result
            long duration = System.currentTimeMillis() - startTime;
            result.setTripleCount((long) tripleCount);
            result.setExecutionTimeMs(duration);
            result.setNamedGraph(namedGraphUri);
            result.setCompletedAt(LocalDateTime.now());
            result.setStatus("SUCCESS");

            // Update connector metadata
            connector.setLastMaterializationAt(LocalDateTime.now());
            connector.setTripleCount((long) tripleCount);
            connector.setMaterializationTimeMs(duration);
            connector.setStatus(ERPConnector.ConnectorStatus.MATERIALIZED);
            erpConnectorRepository.save(connector);

            log.info("Materialization completed in {}ms: {} triples generated", duration, tripleCount);

            dbConnection.close();
            return result;

        } catch (Exception e) {
            log.error("Materialization failed for connector: {}", connectorId, e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setCompletedAt(LocalDateTime.now());
            throw new ProtysFusekiException("Materialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes R2RML mapping and generates RDF triples from SQL results.
     */
    private int executeMappingAndGenerateTriples(
            Connection dbConnection,
            ERPConnector connector,
            String r2rmlContent,
            Model rdfModel) throws Exception {

        int tripleCount = 0;

        try (Statement stmt = dbConnection.createStatement()) {
            List<String> sqlQueries = extractSqlQueries(r2rmlContent);
            log.debug("Found {} SQL queries in R2RML mapping", sqlQueries.size());

            for (String sqlQuery : sqlQueries) {
                log.debug("Executing SQL: {}", sqlQuery);

                try (ResultSet resultSet = stmt.executeQuery(sqlQuery)) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();

                    while (resultSet.next()) {
                        String subjectId = resultSet.getString(1);
                        String subjectUri = "http://protys.erp/" + connector.getId() + "/" +
                                extractTableName(sqlQuery) + "/" + subjectId;

                        Resource subject = rdfModel.createResource(subjectUri);

                        for (int i = 2; i <= columnCount; i++) {
                            String columnName = metadata.getColumnName(i);
                            String columnValue = resultSet.getString(i);

                            if (columnValue != null && !columnValue.isBlank()) {
                                String predicateUri = "http://protys.erp/" + columnName.toLowerCase();
                                Property property = rdfModel.createProperty(predicateUri);
                                subject.addProperty(property, columnValue);
                                tripleCount++;
                            }
                        }

                        String classUri = "http://protys.erp/" + extractTableName(sqlQuery);
                        Resource classResource = rdfModel.createResource(classUri);
                        subject.addProperty(org.apache.jena.vocabulary.RDF.type, classResource);
                        tripleCount++;
                    }
                }
            }
        }

        log.info("Generated {} triples from R2RML execution", tripleCount);
        return tripleCount;
    }

    /**
     * Extracts SQL queries from R2RML mapping content.
     */
    private List<String> extractSqlQueries(String r2rmlContent) {
        List<String> queries = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<rr:sqlQuery>([^<]+)</rr:sqlQuery>");
        java.util.regex.Matcher m = p.matcher(r2rmlContent);
        while (m.find()) {
            String query = m.group(1).trim();
            if (!query.isEmpty()) {
                queries.add(query);
            }
        }
        return queries;
    }

    /**
     * Extracts table name from SQL SELECT statement.
     */
    private String extractTableName(String sql) {
        try {
            String upper = sql.toUpperCase();
            int fromIndex = upper.indexOf("FROM");
            if (fromIndex > -1) {
                String afterFrom = sql.substring(fromIndex + 4).trim();
                String tableName = afterFrom.split("\\s")[0];
                return tableName.replaceAll("[`\"]", "");
            }
        } catch (Exception e) {
            log.warn("Failed to extract table name from SQL", e);
        }
        return "unknown";
    }

    /**
     * Loads R2RML mapping file content.
     */
    private String loadR2RMLMapping(ERPConnector connector) throws Exception {
        if (connector.getR2rmlMappingPath() == null || connector.getR2rmlMappingPath().isBlank()) {
            throw new ProtysFusekiException("R2RML mapping path not configured");
        }

        try {
            return new String(Files.readAllBytes(Paths.get(connector.getR2rmlMappingPath())));
        } catch (Exception e) {
            log.error("Failed to load R2RML file: {}", connector.getR2rmlMappingPath(), e);
            throw new ProtysFusekiException("Failed to load R2RML file: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves materialization history for a connector.
     */
    @Transactional(readOnly = true)
    public List<MaterializationResult> getMaterializationHistory(UUID connectorId) {
        log.debug("Fetching materialization history for connector: {}", connectorId);
        return new ArrayList<>();
    }

    /**
     * Retrieves all registered connectors.
     */
    @Transactional(readOnly = true)
    public List<ERPConnector> getConnectors() {
        log.debug("Fetching all ERP connectors");
        return erpConnectorRepository.findAll();
    }

    /**
     * Gets a specific connector by ID.
     */
    @Transactional(readOnly = true)
    public ERPConnector getConnector(UUID connectorId) {
        log.debug("Fetching connector: {}", connectorId);
        return erpConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));
    }

    /**
     * Updates an ERP connector configuration.
     */
    @Transactional
    public ERPConnector updateConnector(UUID connectorId, ERPConnector updated) {
        log.info("Updating connector: {}", connectorId);

        ERPConnector connector = erpConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

        connector.setName(updated.getName());
        connector.setType(updated.getType());
        connector.setJdbcUrl(updated.getJdbcUrl());
        connector.setUsername(updated.getUsername());
        connector.setPassword(updated.getPassword());
        connector.setDatabaseName(updated.getDatabaseName());
        connector.setR2rmlMappingPath(updated.getR2rmlMappingPath());
        connector.setUpdatedAt(LocalDateTime.now());

        ERPConnector saved = erpConnectorRepository.save(connector);
        log.info("Connector updated: {}", connectorId);
        return saved;
    }

    /**
     * Deletes an ERP connector.
     */
    @Transactional
    public void deleteConnector(UUID connectorId) {
        log.info("Deleting connector: {}", connectorId);
        erpConnectorRepository.deleteById(connectorId);
        log.info("Connector deleted: {}", connectorId);
    }
}
