package org.protys.ws.service;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.IsoMatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre los casos R2RML de la suite W3C RDB2RDF contra {@link R2RMLProcessor}, sobre H2 en memoria.
 *
 * <p>Un caso con resultado esperado pasa si el conjunto de cuádruplas generado es isomorfo al
 * esperado. Un caso sin resultado esperado (mapeo inválido o error de datos) pasa si el
 * procesador falla. Los casos de {@link #EXCLUDED} quedan fuera por una razón documentada que no
 * depende del procesador; se informan igual en el reporte.
 */
class R2RMLW3CTestSuiteTest {

    static final Path SUITE = Path.of("src/test/resources/w3c-r2rml");
    static final String BASE = "http://example.com/base/";
    static final String TEST = "http://purl.org/NET/rdb2rdf-test#";
    static final String DC = "http://purl.org/dc/elements/1.1/";

    /** Casos excluidos: identificador → razón. Se llenan sólo con causas ajenas al procesador. */
    static final Map<String, String> EXCLUDED = Map.of();

    static final Map<String, String> REPORT = new TreeMap<>();

    record Case(String id, String title, Path dir, String mapping, String output, boolean expectsOutput) {
        @Override public String toString() { return id + " " + title; }
    }

    static Stream<Case> cases() throws IOException {
        List<Case> cases = new ArrayList<>();
        try (var dirs = Files.list(SUITE)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Model manifest = RDFParser.source(dir.resolve("manifest.ttl")).lang(Lang.TURTLE).toModel();
                var type = manifest.createResource(TEST + "R2RML");
                for (Resource tc : manifest.listSubjectsWithProperty(
                        org.apache.jena.vocabulary.RDF.type, type).toList()) {
                    String id = tc.getProperty(manifest.createProperty(DC + "identifier")).getString();
                    String title = tc.getProperty(manifest.createProperty(DC + "title")).getString();
                    String mapping = tc.getProperty(manifest.createProperty(TEST + "mappingDocument")).getString();
                    boolean expects = tc.getProperty(manifest.createProperty(TEST + "hasExpectedOutput")).getBoolean();
                    var out = tc.getProperty(manifest.createProperty(TEST + "output"));
                    cases.add(new Case(id, title, dir, mapping, out == null ? null : out.getString(), expects));
                }
            }
        }
        cases.sort((a, b) -> a.id().compareTo(b.id()));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void w3cCase(Case tc) throws Exception {
        if (EXCLUDED.containsKey(tc.id())) {
            REPORT.put(tc.id(), "EXCLUIDO  " + EXCLUDED.get(tc.id()));
            return;
        }
        String jdbc = "jdbc:h2:mem:w3c_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(jdbc, "sa", "")) {
            try (var st = connection.createStatement()) {
                st.execute("RUNSCRIPT FROM '" + tc.dir().resolve("create.sql").toAbsolutePath() + "'");
            }
            connection.setReadOnly(true);
            DatasetGraph produced;
            try {
                var mapping = R2RMLProcessor.parse(Files.readString(tc.dir().resolve(tc.mapping())), BASE);
                produced = R2RMLProcessor.execute(mapping, connection);
            } catch (RuntimeException | java.sql.SQLException failure) {
                if (!tc.expectsOutput()) {
                    REPORT.put(tc.id(), "APROBADO  error esperado: " + failure.getMessage());
                    return;
                }
                REPORT.put(tc.id(), "FALLADO   " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                fail(tc.id() + " falló: " + failure.getMessage(), failure);
                return;
            }
            if (!tc.expectsOutput()) {
                REPORT.put(tc.id(), "FALLADO   se esperaba un error y se generaron " + produced.stream().count() + " cuádruplas");
                fail(tc.id() + ": se esperaba un error");
            }
            Dataset expected = RDFDataMgr.loadDataset(tc.dir().resolve(tc.output()).toString(), Lang.NQUADS);
            boolean iso = IsoMatcher.isomorphic(expected.asDatasetGraph(), produced);
            REPORT.put(tc.id(), (iso ? "APROBADO  " : "FALLADO   resultado distinto: ") + tc.title());
            if (!iso) {
                var sw = new java.io.StringWriter();
                RDFDataMgr.write(sw, produced, Lang.NQUADS);
                fail(tc.id() + " generó:\n" + sw);
            }
        }
    }

    @AfterAll
    static void writeReport() throws IOException {
        long ok = REPORT.values().stream().filter(v -> v.startsWith("APROBADO")).count();
        long ko = REPORT.values().stream().filter(v -> v.startsWith("FALLADO")).count();
        long ex = REPORT.values().stream().filter(v -> v.startsWith("EXCLUIDO")).count();
        StringBuilder sb = new StringBuilder("Suite W3C RDB2RDF, casos R2RML, sobre H2\n");
        sb.append("aprobados=").append(ok).append(" fallados=").append(ko).append(" excluidos=").append(ex)
          .append(" total=").append(REPORT.size()).append('\n');
        REPORT.forEach((k, v) -> sb.append(k).append("  ").append(v).append('\n'));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/w3c-r2rml-report.txt"), sb.toString());
    }
}
