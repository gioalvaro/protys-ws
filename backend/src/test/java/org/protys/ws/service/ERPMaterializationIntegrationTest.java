package org.protys.ws.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.repository.ERPConnectorRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real JDBC plus a private Fuseki process; no existing database, ports or graphs are used. */
class ERPMaterializationIntegrationTest {
    @TempDir Path temporary;

    @Test void invalidAndEmptyMappingsPreserveARealGraphAndSupportedRowsMaterialize() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path runtime = root.resolve("research/runtime/validator/target");
        String classpath = runtime.resolve("classes") + java.io.File.pathSeparator
                + Files.readString(runtime.resolve("classpath.txt")).trim();
        Path portFile = temporary.resolve("port.txt");
        Process fusekiProcess = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", classpath, "org.protys.research.IsolatedFuseki",
                portFile.toString(), temporary.resolve("tdb").toString())
                .redirectOutput(temporary.resolve("fuseki.stdout").toFile())
                .redirectError(temporary.resolve("fuseki.stderr").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(portFile) && fusekiProcess.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(portFile), () -> "Private Fuseki failed to start: "
                    + readLog(temporary.resolve("fuseki.stderr")));
            FusekiService fuseki = new FusekiService();
            ReflectionTestUtils.setField(fuseki, "fusekiEndpoint",
                    "http://127.0.0.1:" + Files.readString(portFile).trim() + "/academic");

            String jdbc = "jdbc:h2:mem:erp_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
            try (var connection = DriverManager.getConnection(jdbc, "sa", "");
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name VARCHAR(100))");
                statement.execute("INSERT INTO products VALUES (7, 'Synthetic product')");
            }
            var connector = ERPConnector.builder().id(UUID.randomUUID()).name("Isolated synthetic ERP")
                    .type(ERPConnector.ERPType.CUSTOM).jdbcUrl(jdbc).username("sa").password("")
                    .namedGraph("urn:p1:existing").tripleCount(1L)
                    .status(ERPConnector.ConnectorStatus.MATERIALIZED).build();
            var repository = mock(ERPConnectorRepository.class);
            when(repository.findById(connector.getId())).thenReturn(Optional.of(connector));
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            var service = new ERPConnectorService(fuseki, repository, mock(DataSource.class));
            var previous = ModelFactory.createDefaultModel();
            previous.add(previous.createResource("urn:p1:kept"), previous.createProperty("urn:p1:value"), "original");
            fuseki.putModel(connector.getNamedGraph(), previous);
            Path mapping = temporary.resolve("mapping.xml");
            connector.setR2rmlMappingPath(mapping.toString());

            Files.writeString(mapping, "@prefix rr: <http://www.w3.org/ns/r2rml#> .");
            assertThrows(ProtysMappingException.class, () -> service.materialize(connector.getId()));
            assertTrue(fuseki.getModel(connector.getNamedGraph()).isIsomorphicWith(previous));
            verify(repository, never()).save(any());

            Files.writeString(mapping, xml("SELECT id, name FROM products WHERE 1=0"));
            assertEquals("EMPTY", service.materialize(connector.getId()).getStatus());
            assertTrue(fuseki.getModel(connector.getNamedGraph()).isIsomorphicWith(previous));
            assertEquals(1L, connector.getTripleCount());
            verify(repository, never()).save(any());

            Files.writeString(mapping, xml("SELECT id, name FROM products"));
            var success = service.materialize(connector.getId());
            assertEquals("SUCCESS", success.getStatus());
            var published = fuseki.getModel(connector.getNamedGraph());
            assertEquals(2, published.size());
            assertTrue(published.contains(null, null, "Synthetic product"));
            assertFalse(published.containsResource(previous.createResource("urn:p1:kept")));
            assertEquals(published.size(), connector.getTripleCount());
            verify(repository).save(connector);
            try (var connection = DriverManager.getConnection(jdbc, "sa", "");
                 var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        } finally {
            fusekiProcess.destroy();
            if (!fusekiProcess.waitFor(5, TimeUnit.SECONDS)) {
                fusekiProcess.destroyForcibly();
                assertTrue(fusekiProcess.waitFor(5, TimeUnit.SECONDS), "Private Fuseki did not stop");
            }
        }
    }

    private static String xml(String sql) {
        return "<mapping xmlns:rr=\"http://www.w3.org/ns/r2rml#\"><rr:sqlQuery>" + sql
                + "</rr:sqlQuery></mapping>";
    }

    private static String readLog(Path path) {
        try { return Files.readString(path); }
        catch (Exception ignored) { return "No process log available"; }
    }
}
