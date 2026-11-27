package org.protys.ws.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.repository.ERPConnectorRepository;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ERPConnectorIntegrityTest {
    @TempDir Path directory;
    private ERPConnectorRepository repository;
    private FusekiService fuseki;
    private ERPConnectorService service;
    private ERPConnector connector;

    @BeforeEach void setUp() {
        repository = mock(ERPConnectorRepository.class);
        fuseki = mock(FusekiService.class);
        service = new ERPConnectorService(fuseki, repository, mock(DataSource.class));
        connector = ERPConnector.builder().id(UUID.randomUUID()).name("Synthetic ERP")
                .type(ERPConnector.ERPType.CUSTOM).jdbcUrl("jdbc:synthetic:erp")
                .username("synthetic").password("FAKE-secret-for-regression")
                .namedGraph("urn:test:existing-graph").tripleCount(9L)
                .status(ERPConnector.ConnectorStatus.MATERIALIZED).build();
        when(repository.findById(connector.getId())).thenReturn(Optional.of(connector));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mapping(String content) throws Exception {
        Path file = directory.resolve("mapping.xml");
        Files.writeString(file, content);
        connector.setR2rmlMappingPath(file.toString());
    }

    private void supportedMapping() throws Exception {
        mapping("<mapping xmlns:rr=\"http://www.w3.org/ns/r2rml#\">"
                + "<rr:sqlQuery>SELECT id, name FROM products</rr:sqlQuery></mapping>");
    }

    @Test void unsupportedMappingDoesNotOpenJdbcOrReplaceExistingGraph() throws Exception {
        mapping("@prefix rr: <http://www.w3.org/ns/r2rml#> . <urn:m> rr:sqlQuery \"SELECT id FROM products\" .");
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
            jdbc.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(connection);
            RuntimeException failure = assertThrows(RuntimeException.class, () -> service.materialize(connector.getId()));
            assertTrue(failure.getMessage().contains("UNSUPPORTED_MAPPING"));
            jdbc.verifyNoInteractions();
        }
        verifyNoInteractions(fuseki);
        verify(repository, never()).save(any());
        assertEquals(9L, connector.getTripleCount());
    }

    @Test void legitimateEmptyResultPreservesGraphAndClosesJdbc() throws Exception {
        supportedMapping();
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(mock(ResultSetMetaData.class));
        when(rows.next()).thenReturn(false);
        try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
            jdbc.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(connection);
            var result = service.materialize(connector.getId());
            assertEquals("EMPTY", result.getStatus());
            assertEquals(0L, result.getTripleCount());
            assertFalse(result.isSuccess());
            assertEquals(1, result.getMappingsProcessed());
        }
        verifyNoInteractions(fuseki);
        verify(repository, never()).save(any());
        verify(rows).close();
        verify(statement).close();
        verify(connection).close();
        assertEquals(9L, connector.getTripleCount());
    }

    @Test void sqlFailureClosesConnectionAndDoesNotExposeSecretOrPublish() throws Exception {
        supportedMapping();
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenThrow(new SQLException("driver echoed " + connector.getPassword()));
        try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
            jdbc.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(connection);
            RuntimeException failure = assertThrows(RuntimeException.class, () -> service.materialize(connector.getId()));
            assertFalse(failure.toString().contains(connector.getPassword()));
            assertNull(failure.getCause(), "Raw driver exceptions can contain credentials");
        }
        verify(statement).close();
        verify(connection).close();
        verifyNoInteractions(fuseki);
        verify(repository, never()).save(any());
    }

    @Test void supportedRowsPublishOnlyAfterJdbcHasClosed() throws Exception {
        supportedMapping();
        when(fuseki.getModelOrEmpty(connector.getNamedGraph())).thenReturn(org.apache.jena.rdf.model.ModelFactory.createDefaultModel());
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(2)).thenReturn("name");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("7");
        when(rows.getString(2)).thenReturn("Synthetic product");
        try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
            jdbc.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(connection);
            var result = service.materialize(connector.getId());
            assertEquals("SUCCESS", result.getStatus());
            assertEquals(2L, result.getTripleCount());
        }
        var order = inOrder(connection, fuseki);
        order.verify(connection).close();
        order.verify(fuseki).putModel(eq(connector.getNamedGraph()), argThat(model -> model.size() == 2));
        verify(repository).save(connector);
    }

    @Test void xmlEntityDeclarationsAndUnimplementedTermMapsAreRejectedBeforeJdbc() throws Exception {
        for (String xml : new String[]{
                "<!DOCTYPE mapping [<!ENTITY leak SYSTEM 'file:///nonexistent-test-only'>]>"
                        + "<mapping xmlns:rr='http://www.w3.org/ns/r2rml#'><rr:sqlQuery>&leak;</rr:sqlQuery></mapping>",
                "<mapping xmlns:rr='http://www.w3.org/ns/r2rml#'><rr:sqlQuery>SELECT id FROM products</rr:sqlQuery>"
                        + "<rr:subjectMap/></mapping>",
                "<mapping xmlns:rr='http://www.w3.org/ns/r2rml#'><rr:sqlQuery> </rr:sqlQuery></mapping>"}) {
            mapping(xml);
            try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
                var failure = assertThrows(RuntimeException.class, () -> service.materialize(connector.getId()));
                assertTrue(failure.getMessage().contains("UNSUPPORTED_MAPPING"));
                jdbc.verifyNoInteractions();
            }
        }
        verifyNoInteractions(fuseki);
    }

    @Test void jdbcCloseFailureCannotPublishAnIncompleteOperation() throws Exception {
        supportedMapping();
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(mock(ResultSetMetaData.class));
        doThrow(new SQLException("FAKE-secret-for-regression")).when(connection).close();
        try (MockedStatic<DriverManager> jdbc = mockStatic(DriverManager.class)) {
            jdbc.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(connection);
            assertThrows(RuntimeException.class, () -> service.materialize(connector.getId()));
        }
        verifyNoInteractions(fuseki);
        verify(repository, never()).save(any());
    }

    @Test void omittedAndBlankPasswordsPreserveStoredCredentialDuringPartialUpdate() {
        String originalUrl = connector.getJdbcUrl();
        String originalSecret = connector.getPassword();
        service.updateConnector(connector.getId(), ERPConnector.builder().name("Renamed").build());
        assertEquals(originalUrl, connector.getJdbcUrl());
        assertEquals(originalSecret, connector.getPassword());
        service.updateConnector(connector.getId(), ERPConnector.builder().password("").build());
        assertEquals(originalSecret, connector.getPassword());
        service.updateConnector(connector.getId(), ERPConnector.builder().password("replacement-fake").build());
        assertEquals("replacement-fake", connector.getPassword());
    }

    @Test void passwordIsWriteOnlyAndExcludedFromToString() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertFalse(mapper.writeValueAsString(connector).contains(connector.getPassword()));
        assertFalse(connector.toString().contains(connector.getPassword()));
        assertFalse(ERPConnector.builder().password(connector.getPassword()).toString().contains(connector.getPassword()));
        ERPConnector input = mapper.readValue("{\"password\":\"new-fake-secret\"}", ERPConnector.class);
        assertEquals("new-fake-secret", input.getPassword());
    }

    @Test void redactedPublicUrlCannotOverwriteWorkingConfiguration() {
        String originalUrl = connector.getJdbcUrl();
        service.updateConnector(connector.getId(), ERPConnector.builder()
                .jdbcUrl("jdbc:postgresql://localhost/db?password=[REDACTED]").build());
        assertEquals(originalUrl, connector.getJdbcUrl());
    }

    @Test void repositoryUpdateFailureDoesNotExposeCredential() {
        String originalSecret = connector.getPassword();
        when(repository.save(any())).thenThrow(new IllegalStateException(originalSecret));
        var failure = assertThrows(RuntimeException.class, () -> service.updateConnector(
                connector.getId(), ERPConnector.builder().name("Renamed").build()));
        assertFalse(failure.toString().contains(originalSecret));
        assertNull(failure.getCause());
    }
}
