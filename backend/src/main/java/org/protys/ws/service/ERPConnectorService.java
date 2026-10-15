package org.protys.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.dto.MaterializationResult;
import org.protys.ws.dto.SchemaMetadata;
import org.protys.ws.exception.ProtysFusekiException;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.exception.ERPMaterializationException;
import org.protys.ws.repository.ERPConnectorRepository;

import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import java.io.StringReader;
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
            log.warn("Failed to register connector ({})", e.getClass().getSimpleName());
            throw new ProtysFusekiException("Failed to register connector");
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
            log.warn("Connection test failed for connector {} ({})", connectorId, e.getClass().getSimpleName());
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
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
              while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                List<SchemaMetadata> columns = new ArrayList<>();

                try (ResultSet columnsRs = metadata.getColumns(null, null, tableName, null)) {
                  while (columnsRs.next()) {
                    SchemaMetadata col = new SchemaMetadata();
                    col.setName(columnsRs.getString("COLUMN_NAME"));
                    col.setType(columnsRs.getString("TYPE_NAME"));
                    col.setNullable(columnsRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    columns.add(col);
                  }
                }

                schema.put(tableName, columns);
                log.debug("Found table: {} with {} columns", tableName, columns.size());
              }
            }

            log.info("Schema introspection found {} tables", schema.size());
            return schema;

        } catch (Exception e) {
            log.warn("Failed to introspect schema for connector {} ({})", connectorId, e.getClass().getSimpleName());
            throw new ProtysFusekiException("Failed to introspect ERP schema");
        }
    }

    /**
     * Executes the limited legacy XML SQL adapter, not a general R2RML processor.
     * Unsupported mappings, failed SQL execution and empty results never replace an existing graph.
     */
    @Transactional
    public MaterializationResult materialize(UUID connectorId) {
        log.info("Starting materialization for connector: {}", connectorId);
        long startTime = System.currentTimeMillis();

        MaterializationResult result = new MaterializationResult();
        result.setConnectorId(connectorId);
        result.setStartedAt(LocalDateTime.now());
        ERPGraphPublication publication = null;

        try {
            ERPConnector connector = erpConnectorRepository.findById(connectorId)
                    .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

            // Validate the complete supported structure before opening JDBC or touching Fuseki.
            List<String> sqlQueries = extractSqlQueries(loadR2RMLMapping(connector));
            result.setMappingsProcessed(sqlQueries.size());
            result.setNamedGraph(connector.getNamedGraph());

            Model rdfModel = ModelFactory.createDefaultModel();
            rdfModel.setNsPrefix("protys", "http://w3id.org/protys/ontology/");
            rdfModel.setNsPrefix("erp", "http://w3id.org/protys/erp/");

            // Finish and close all JDBC resources before publishing anything.
            try (Connection dbConnection = java.sql.DriverManager.getConnection(
                    connector.getJdbcUrl(), connector.getUsername(), connector.getPassword())) {
                dbConnection.setReadOnly(true);
                executeMappingAndGenerateTriples(dbConnection, connector, sqlQueries, rdfModel);
            }
            long tripleCount = rdfModel.size();
            result.setTripleCount(tripleCount);
            if (rdfModel.isEmpty()) {
                result.setStatus("EMPTY");
                result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                result.setCompletedAt(LocalDateTime.now());
                result.setWarnings(List.of("Queries returned no rows; existing graph and connector metadata preserved"));
                return result;
            }

            String namedGraphUri = connector.getNamedGraph();
            publication = new ERPGraphPublication(fusekiService, connectorId, namedGraphUri, rdfModel);
            publication.publish();
            log.info("Stored model in Fuseki named graph: {}", namedGraphUri);

            long duration = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(duration);
            result.setNamedGraph(namedGraphUri);
            result.setCompletedAt(LocalDateTime.now());
            result.setStatus("SUCCESS");

            // Update connector metadata
            connector.setLastMaterializationAt(LocalDateTime.now());
            connector.setTripleCount(tripleCount);
            connector.setMaterializationTimeMs(duration);
            connector.setStatus(ERPConnector.ConnectorStatus.MATERIALIZED);
            erpConnectorRepository.save(connector);

            log.info("Materialization completed in {}ms: {} triples generated", duration, tripleCount);

            return result;

        } catch (Exception e) {
            if (publication != null) {
                publication.compensate("EXECUTION_FAILURE");
                throw new ERPMaterializationException(publication.operationId(), publication.outcome().name());
            }
            if (e instanceof ProtysMappingException mapping) throw mapping;
            // JDBC and database exceptions can echo connection credentials.
            log.warn("Materialization failed for connector {} ({})", connectorId, e.getClass().getSimpleName());
            throw new ProtysFusekiException("ERP materialization failed; no successful result was recorded");
        }
    }

    /**
     * Generates legacy RDF triples from the validated SQL list.
     */
    private int executeMappingAndGenerateTriples(
            Connection dbConnection,
            ERPConnector connector,
            List<String> sqlQueries,
            Model rdfModel) throws Exception {

        int tripleCount = 0;

        try (Statement stmt = dbConnection.createStatement()) {
            log.debug("Executing {} queries from legacy SQL mapping", sqlQueries.size());

            for (String sqlQuery : sqlQueries) {
                try (ResultSet resultSet = stmt.executeQuery(sqlQuery)) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();

                    while (resultSet.next()) {
                        String subjectId = resultSet.getString(1);
                        String subjectUri = "http://w3id.org/protys/erp/" + connector.getId() + "/" +
                                extractTableName(sqlQuery) + "/" + subjectId;

                        Resource subject = rdfModel.createResource(subjectUri);

                        for (int i = 2; i <= columnCount; i++) {
                            String columnName = metadata.getColumnName(i);
                            String columnValue = resultSet.getString(i);

                            if (columnValue != null && !columnValue.isBlank()) {
                                String predicateUri = "http://w3id.org/protys/erp/" + columnName.toLowerCase();
                                Property property = rdfModel.createProperty(predicateUri);
                                subject.addProperty(property, columnValue);
                                tripleCount++;
                            }
                        }

                        String classUri = "http://w3id.org/protys/erp/" + extractTableName(sqlQuery);
                        Resource classResource = rdfModel.createResource(classUri);
                        subject.addProperty(org.apache.jena.vocabulary.RDF.type, classResource);
                        tripleCount++;
                    }
                }
            }
        }

        log.info("Generated {} triple statements from legacy SQL execution", tripleCount);
        return tripleCount;
    }

    /**
     * Supported legacy format: a mapping root with only rr:sqlQuery children.
     * It maps column 1 to the subject ID and later columns to plain literals.
     * RDF/XML triples maps, Turtle, term maps, joins and extensions are rejected.
     */
    private List<String> extractSqlQueries(String r2rmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new InputSource(new StringReader(r2rmlContent))).getDocumentElement();
            if (!"mapping".equals(root.getTagName()) || root.getNamespaceURI() != null) {
                throw unsupportedMapping();
            }
            for (int i = 0; i < root.getAttributes().getLength(); i++) {
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(root.getAttributes().item(i).getNamespaceURI())) {
                    throw unsupportedMapping();
                }
            }
            List<String> queries = new ArrayList<>();
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.COMMENT_NODE) continue;
                if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) continue;
                if (!(child instanceof Element element)
                        || !"http://www.w3.org/ns/r2rml#".equals(element.getNamespaceURI())
                        || !"sqlQuery".equals(element.getLocalName()) || element.hasAttributes()) {
                    throw unsupportedMapping();
                }
                for (Node value = element.getFirstChild(); value != null; value = value.getNextSibling()) {
                    if (value.getNodeType() != Node.TEXT_NODE && value.getNodeType() != Node.CDATA_SECTION_NODE) {
                        throw unsupportedMapping();
                    }
                }
                String query = element.getTextContent().trim();
                if (!query.matches("(?is)^SELECT\\s+.+") || query.contains(";")) throw unsupportedMapping();
                queries.add(query);
            }
            if (queries.isEmpty()) throw unsupportedMapping();
            return queries;
        } catch (Exception e) {
            throw unsupportedMapping();
        }
    }

    private ProtysMappingException unsupportedMapping() {
        return new ProtysMappingException("UNSUPPORTED_MAPPING: expected legacy XML <mapping> with only "
                + "rr:sqlQuery SELECT children (rr=http://www.w3.org/ns/r2rml#); general R2RML is not supported");
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
            log.warn("Failed to extract table name from SQL ({})", e.getClass().getSimpleName());
        }
        return "unknown";
    }

    /**
     * Loads R2RML mapping file content.
     */
    private String loadR2RMLMapping(ERPConnector connector) throws Exception {
        if (connector.getR2rmlMappingPath() == null || connector.getR2rmlMappingPath().isBlank()) {
            throw new ProtysMappingException("MAPPING_UNAVAILABLE: mapping path not configured");
        }

        try {
            return Files.readString(Paths.get(connector.getR2rmlMappingPath()));
        } catch (Exception e) {
            throw new ProtysMappingException("MAPPING_UNAVAILABLE: mapping file cannot be read");
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
        try {
          ERPConnector connector = erpConnectorRepository.findById(connectorId)
                .orElseThrow(() -> new ProtysFusekiException("Connector not found: " + connectorId));

        if (updated.getName() != null) connector.setName(updated.getName());
        if (updated.getType() != null) connector.setType(updated.getType());
        // A client may submit a public DTO again; never persist its redaction marker.
        if (updated.getJdbcUrl() != null && !updated.getJdbcUrl().contains("[REDACTED]")) connector.setJdbcUrl(updated.getJdbcUrl());
        if (updated.getUsername() != null) connector.setUsername(updated.getUsername());
        if (updated.getPassword() != null && !updated.getPassword().isBlank()) connector.setPassword(updated.getPassword());
        if (updated.getDatabaseName() != null) connector.setDatabaseName(updated.getDatabaseName());
        if (updated.getR2rmlMappingPath() != null) connector.setR2rmlMappingPath(updated.getR2rmlMappingPath());
        connector.setUpdatedAt(LocalDateTime.now());

        ERPConnector saved = erpConnectorRepository.save(connector);
        log.info("Connector updated: {}", connectorId);
        return saved;
        } catch (Exception e) {
            log.warn("Failed to update connector {} ({})", connectorId, e.getClass().getSimpleName());
            throw new ProtysFusekiException("Failed to update ERP connector");
        }
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
