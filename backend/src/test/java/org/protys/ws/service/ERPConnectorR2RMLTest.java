package org.protys.ws.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.protys.ws.exception.ProtysFusekiException;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.repository.ERPConnectorRepository;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Materialización con mapeos R2RML sobre una base H2 que pliega identificadores como PostgreSQL. */
class ERPConnectorR2RMLTest {

    static final String I = "http://w3id.org/protys/ontology/iso15531#";
    static final String C = "http://w3id.org/protys/ontology/core#";
    static final String GRAPH = "http://w3id.org/protys/erp/odoo";

    @TempDir Path directory;
    ERPConnectorRepository repository;
    FusekiService fuseki;
    ERPConnectorService service;
    ERPConnector connector;
    String jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = "jdbc:h2:mem:odoo_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (var c = DriverManager.getConnection(jdbc, "sa", ""); var st = c.createStatement()) {
            st.execute("CREATE TABLE mrp_workcenter (id INTEGER PRIMARY KEY, name VARCHAR(50))");
            st.execute("CREATE TABLE mrp_bom (id INTEGER PRIMARY KEY, code VARCHAR(20))");
            st.execute("CREATE TABLE mrp_routing_workcenter (id INTEGER PRIMARY KEY, bom_id INTEGER, "
                    + "name VARCHAR(50), sequence INTEGER, workcenter_id INTEGER, time_cycle_manual DECIMAL(10,2))");
            st.execute("INSERT INTO mrp_workcenter VALUES (1, 'Assembly Line 1'), (2, 'Drill Station 1')");
            st.execute("INSERT INTO mrp_bom VALUES (8, 'SEC-ASSEM')");
            st.execute("INSERT INTO mrp_routing_workcenter VALUES (5, 8, 'Packing', 5, 2, 60.00), "
                    + "(4, 8, 'Testing', 10, 1, 60.00)");
        }
        repository = mock(ERPConnectorRepository.class);
        fuseki = mock(FusekiService.class);
        when(fuseki.getModelOrEmpty(anyString())).thenReturn(ModelFactory.createDefaultModel());
        service = new ERPConnectorService(fuseki, repository, mock(DataSource.class));
        connector = ERPConnector.builder().id(UUID.randomUUID()).name("Odoo sintético")
                .type(ERPConnector.ERPType.ODOO).jdbcUrl(jdbc).username("sa").password("")
                .namedGraph(GRAPH).status(ERPConnector.ConnectorStatus.REGISTERED).build();
        when(repository.findById(connector.getId())).thenReturn(Optional.of(connector));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    void mapping(String turtle) throws Exception {
        Path file = directory.resolve("odoo.r2rml.ttl");
        Files.writeString(file, PREFIXES + turtle);
        connector.setR2rmlMappingPath(file.toString());
    }

    static final String PREFIXES = """
            @prefix rr: <http://www.w3.org/ns/r2rml#> .
            @prefix i: <http://w3id.org/protys/ontology/iso15531#> .
            @prefix c: <http://w3id.org/protys/ontology/core#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            """;

    static final String ROUTING = """
            <#Workcenter> rr:logicalTable [ rr:tableName "mrp_workcenter" ] ;
              rr:subjectMap [ rr:template "http://w3id.org/protys/erp/odoo/workcenter/{id}" ; rr:class i:ManufacturingResource ] ;
              rr:predicateObjectMap [ rr:predicate c:hasName ; rr:objectMap [ rr:column "name" ] ] .
            <#Plan> rr:logicalTable [ rr:sqlQuery "SELECT id FROM mrp_bom" ] ;
              rr:subjectMap [ rr:template "http://w3id.org/protys/erp/odoo/plan/{id}" ; rr:class i:ProcessPlan ] .
            <#Operation> rr:logicalTable [ rr:tableName "mrp_routing_workcenter" ] ;
              rr:subjectMap [ rr:template "http://w3id.org/protys/erp/odoo/operation/{id}" ; rr:class i:ProcessOperation ] ;
              rr:predicateObjectMap [ rr:predicate i:operationSequence ; rr:objectMap [ rr:column "sequence" ] ] ;
              rr:predicateObjectMap [ rr:predicate i:duration ; rr:objectMap [ rr:column "time_cycle_manual" ] ] ;
              rr:predicateObjectMap [ rr:predicate i:requiresResource ;
                rr:objectMap [ rr:parentTriplesMap <#Workcenter> ; rr:joinCondition [ rr:child "workcenter_id" ; rr:parent "id" ] ] ] .
            <#PlanIncludes> rr:logicalTable [ rr:tableName "mrp_routing_workcenter" ] ;
              rr:subjectMap [ rr:template "http://w3id.org/protys/erp/odoo/plan/{bom_id}" ] ;
              rr:predicateObjectMap [ rr:predicate i:includesActivity ; rr:objectMap [ rr:template "http://w3id.org/protys/erp/odoo/operation/{id}" ] ] .
            """;

    Model published() {
        ArgumentCaptor<Model> captor = ArgumentCaptor.forClass(Model.class);
        verify(fuseki).putModel(eq(GRAPH), captor.capture());
        return captor.getValue();
    }

    @Test void r2rmlMappingTypesIndividualsWithNetworkClassesAndLinksThem() throws Exception {
        mapping(ROUTING);
        var result = service.materialize(connector.getId());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(4, result.getMappingsProcessed());
        assertEquals(GRAPH, result.getNamedGraph());
        Model m = published();
        Resource op5 = m.createResource("http://w3id.org/protys/erp/odoo/operation/5");
        Resource plan = m.createResource("http://w3id.org/protys/erp/odoo/plan/8");
        Resource drill = m.createResource("http://w3id.org/protys/erp/odoo/workcenter/2");
        assertTrue(m.contains(op5, RDF.type, m.createResource(I + "ProcessOperation")));
        assertTrue(m.contains(plan, RDF.type, m.createResource(I + "ProcessPlan")));
        assertTrue(m.contains(drill, RDF.type, m.createResource(I + "ManufacturingResource")));
        assertTrue(m.contains(plan, m.createProperty(I + "includesActivity"), op5));
        assertTrue(m.contains(op5, m.createProperty(I + "requiresResource"), drill));
        assertTrue(m.contains(drill, m.createProperty(C + "hasName"), "Drill Station 1"));
        // Tipos naturales de SQL a XSD: INTEGER → xsd:integer, DECIMAL → xsd:decimal canónico.
        assertEquals("5", m.getRequiredProperty(op5, m.createProperty(I + "operationSequence")).getLiteral().getLexicalForm());
        assertEquals("http://www.w3.org/2001/XMLSchema#integer",
                m.getRequiredProperty(op5, m.createProperty(I + "operationSequence")).getLiteral().getDatatypeURI());
        assertEquals("60", m.getRequiredProperty(op5, m.createProperty(I + "duration")).getLiteral().getLexicalForm());
        // Nada del vocabulario genérico heredado (erp:<tabla>, erp:<columna>).
        assertFalse(m.listStatements().toList().stream()
                .anyMatch(s -> s.getPredicate().getURI().startsWith("http://w3id.org/protys/erp/")));
        assertEquals(m.size(), connector.getTripleCount());
        assertEquals(ERPConnector.ConnectorStatus.MATERIALIZED, connector.getStatus());
    }

    @Test void materializationUsesAReadOnlyConnection() throws Exception {
        mapping(ROUTING);
        // En H2 isReadOnly() informa si la base entera es de sólo lectura, no la marca de la conexión;
        // por eso se verifica el orden: setReadOnly(true) antes de crear cualquier sentencia.
        Connection spy = spy(DriverManager.getConnection(jdbc, "sa", ""));
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString())).thenReturn(spy);
            service.materialize(connector.getId());
        }
        var order = inOrder(spy);
        order.verify(spy).setReadOnly(true);
        order.verify(spy, atLeastOnce()).createStatement();
        order.verify(spy).close();
    }

    @Test void namedGraphMapsAreRejectedBeforeOpeningJdbc() throws Exception {
        mapping("""
                <#Wc> rr:logicalTable [ rr:tableName "mrp_workcenter" ] ;
                  rr:subjectMap [ rr:template "http://x/{id}" ; rr:graph <http://otro/grafo> ] .
                """);
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            var failure = assertThrows(ProtysMappingException.class, () -> service.materialize(connector.getId()));
            assertTrue(failure.getMessage().startsWith("UNSUPPORTED_MAPPING"));
            dm.verifyNoInteractions();
        }
        verify(fuseki, never()).putModel(anyString(), any());
    }

    @Test void nonSelectQueriesAreRejectedBeforeOpeningJdbc() throws Exception {
        for (String sql : new String[]{"DELETE FROM mrp_bom", "SELECT id FROM mrp_bom; DROP TABLE mrp_bom",
                "UPDATE mrp_bom SET code = 'x'"}) {
            mapping("<#P> rr:logicalTable [ rr:sqlQuery \"" + sql + "\" ] ; rr:subjectMap [ rr:template \"http://x/{id}\" ] .");
            try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
                assertThrows(ProtysMappingException.class, () -> service.materialize(connector.getId()), sql);
                dm.verifyNoInteractions();
            }
        }
        try (var c = DriverManager.getConnection(jdbc, "sa", ""); var st = c.createStatement();
             var rs = st.executeQuery("SELECT count(*) FROM mrp_bom")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test void dataErrorsDoNotPublishAnything() throws Exception {
        // Un nombre con espacio usado como IRI completa es un error de datos de R2RML.
        mapping("""
                <#Wc> rr:logicalTable [ rr:tableName "mrp_workcenter" ] ;
                  rr:subjectMap [ rr:column "name" ] .
                """);
        var failure = assertThrows(ProtysFusekiException.class, () -> service.materialize(connector.getId()));
        assertTrue(failure.getMessage().contains("R2RML_DATA_ERROR"));
        verify(fuseki, never()).putModel(anyString(), any());
        verify(repository, never()).save(any());
    }

    @Test void legacyXmlIsStillRecognized() {
        assertTrue(ERPConnectorService.isLegacyXmlMapping("  <mapping xmlns:rr=\"http://www.w3.org/ns/r2rml#\"/>"));
        assertTrue(ERPConnectorService.isLegacyXmlMapping("<?xml version=\"1.0\"?><mapping/>"));
        assertTrue(ERPConnectorService.isLegacyXmlMapping("<!DOCTYPE m [<!ENTITY x \"y\">]><mapping/>"));
        assertFalse(ERPConnectorService.isLegacyXmlMapping("@prefix rr: <http://www.w3.org/ns/r2rml#> ."));
        assertFalse(ERPConnectorService.isLegacyXmlMapping("<urn:m> a <http://www.w3.org/ns/r2rml#TriplesMap> ."));
    }
}
