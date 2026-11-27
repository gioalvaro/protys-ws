package org.protys.ws.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.repository.ERPConnectorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual JDBC transactions and private HTTP Fuseki; no configured database or service is used. */
class ERPGraphCompensationIntegrationTest {
    @TempDir Path directory;

    private final class Environment implements AutoCloseable {
        final IsolatedFusekiFixture fuseki = new IsolatedFusekiFixture(directory);
        final DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:erp_recovery_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final ERPConnectorRepository repository = mock(ERPConnectorRepository.class);
        final ERPConnector connector;
        final ERPConnectorService service;
        final Model previous = ModelFactory.createDefaultModel();

        Environment() throws Exception {
            jdbc.execute("CREATE TABLE products (id INTEGER, name VARCHAR(50))");
            jdbc.execute("INSERT INTO products VALUES (7, 'Synthetic product')");
            jdbc.execute("CREATE TABLE metadata_events (connector_id VARCHAR(36))");
            var detail = previous.createResource();
            previous.add(previous.createResource("urn:prior:item"), previous.createProperty("urn:prior:detail"), detail);
            previous.add(detail, previous.createProperty("urn:prior:label"), "preserve one blank node");
            previous.add(detail, previous.createProperty("urn:prior:count"), previous.createTypedLiteral(2));
            Path mapping = directory.resolve("mapping.xml");
            Files.writeString(mapping, "<mapping xmlns:rr='http://www.w3.org/ns/r2rml#'>"
                    + "<rr:sqlQuery>SELECT id, name FROM products</rr:sqlQuery></mapping>");
            connector = ERPConnector.builder().id(UUID.randomUUID()).name("Synthetic recovery")
                    .type(ERPConnector.ERPType.CUSTOM).jdbcUrl(source.getUrl()).username("sa").password("")
                    .namedGraph("urn:erp:recovery").r2rmlMappingPath(mapping.toString())
                    .tripleCount(previous.size()).status(ERPConnector.ConnectorStatus.MATERIALIZED).build();
            fuseki.client.putModel(connector.getNamedGraph(), previous);
            when(repository.findById(connector.getId())).thenReturn(Optional.of(connector));
            when(repository.save(any())).thenAnswer(call -> {
                jdbc.update("INSERT INTO metadata_events VALUES (?)", connector.getId().toString());
                return call.getArgument(0);
            });
            service = new ERPConnectorService(fuseki.client, repository, source);
        }

        Model graph() { return fuseki.client.getModelOrEmpty(connector.getNamedGraph()); }
        @Override public void close() throws Exception {
            try { jdbc.execute("SHUTDOWN"); } finally { fuseki.close(); }
        }
    }

    @Test void metadataSaveFailureRestoresPreviousGraphIncludingBlankNodesExactlyOnce() throws Exception {
        try (var env = new Environment()) {
            doThrow(new IllegalStateException("synthetic repository failure")).when(env.repository).save(any());
            RuntimeException failure = assertThrows(RuntimeException.class, () -> env.service.materialize(env.connector.getId()));
            assertTrue(failure.getMessage().contains("RESTORED"));
            assertTrue(env.graph().isIsomorphicWith(env.previous));
            assertEquals(3, env.graph().size(), "A multi-triple DELETE must not multiply INSERT blank nodes");
        }
    }

    @Test void realOuterTransactionRollbackRestoresGraphAndRollsBackMetadata() throws Exception {
        try (var env = new Environment()) {
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(env.source));
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                assertEquals("SUCCESS", env.service.materialize(env.connector.getId()).getStatus());
                assertEquals(2, env.graph().size());
                assertEquals(1, env.jdbc.queryForObject("SELECT COUNT(*) FROM metadata_events", Integer.class));
                throw new IllegalStateException("Deliberately roll back the outer JDBC transaction");
            }));
            assertEquals(0, env.jdbc.queryForObject("SELECT COUNT(*) FROM metadata_events", Integer.class));
            assertTrue(env.graph().isIsomorphicWith(env.previous));
            assertEquals(3, env.graph().size());
        }
    }

    @Test void compensationCannotOverwriteAnotherWritersGraph() throws Exception {
        try (var env = new Environment()) {
            Model other = ModelFactory.createDefaultModel();
            other.add(other.createResource("urn:another:writer"), other.createProperty("urn:another:value"), "must survive");
            other.add(other.createResource("urn:another:writer"), other.createProperty("urn:another:second"), "same size as the ERP publication");
            doAnswer(call -> {
                env.fuseki.client.putModel(env.connector.getNamedGraph(), other);
                throw new IllegalStateException("Metadata failed after another writer published");
            }).when(env.repository).save(any());
            RuntimeException failure = assertThrows(RuntimeException.class, () -> env.service.materialize(env.connector.getId()));
            assertTrue(failure.getMessage().contains("CURRENT_GRAPH_CHANGED"));
            assertTrue(env.graph().isIsomorphicWith(other));
        }
    }

    @Test void committedTransactionKeepsPublishedGraph() throws Exception {
        try (var env = new Environment()) {
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(env.source));
            transaction.executeWithoutResult(status -> env.service.materialize(env.connector.getId()));
            assertEquals(1, env.jdbc.queryForObject("SELECT COUNT(*) FROM metadata_events", Integer.class));
            assertEquals(2, env.graph().size());
            assertFalse(env.graph().isIsomorphicWith(env.previous));
        }
    }
}
