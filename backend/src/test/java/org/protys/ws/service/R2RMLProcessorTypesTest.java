package org.protys.ws.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Claves de join numéricas comparadas por valor y TIMESTAMP WITH TIME ZONE a xsd:dateTime con desplazamiento. */
class R2RMLProcessorTypesTest {

    static final String PREFIXES = "@prefix rr: <http://www.w3.org/ns/r2rml#> .\n@prefix ex: <http://e.org/> .\n";

    @Test
    void unaClaveIntegerYUnaDoubleIgualesSeUnen() throws Exception {
        String jdbc = "jdbc:h2:mem:join_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(jdbc, "sa", ""); var st = c.createStatement()) {
            st.execute("CREATE TABLE padre (ID INTEGER PRIMARY KEY)");
            st.execute("CREATE TABLE hijo (ID INTEGER PRIMARY KEY, PADRE DOUBLE PRECISION)");
            st.execute("INSERT INTO padre VALUES (8)");
            st.execute("INSERT INTO hijo VALUES (1, 8.0)");
            String mapping = PREFIXES + """
                    <#Padre> rr:logicalTable [ rr:tableName "PADRE" ] ;
                        rr:subjectMap [ rr:template "http://e.org/padre/{ID}" ] .
                    <#Hijo> rr:logicalTable [ rr:tableName "HIJO" ] ;
                        rr:subjectMap [ rr:template "http://e.org/hijo/{ID}" ] ;
                        rr:predicateObjectMap [ rr:predicate ex:padre ;
                            rr:objectMap [ rr:parentTriplesMap <#Padre> ;
                                rr:joinCondition [ rr:child "PADRE" ; rr:parent "ID" ] ] ] .
                    """;
            Graph g = R2RMLProcessor.execute(R2RMLProcessor.parse(mapping, "http://e.org/"), c).getDefaultGraph();
            assertTrue(g.contains(NodeFactory.createURI("http://e.org/hijo/1"), NodeFactory.createURI("http://e.org/padre"),
                    NodeFactory.createURI("http://e.org/padre/8")), "8 (INTEGER) y 8.0E0 (DOUBLE) deberían unirse");
        }
    }

    @Test
    void timestampConZonaConservaElDesplazamiento() throws Exception {
        String jdbc = "jdbc:h2:mem:tz_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(jdbc, "sa", ""); var st = c.createStatement()) {
            st.execute("CREATE TABLE orden (ID INTEGER PRIMARY KEY, CUANDO TIMESTAMP WITH TIME ZONE)");
            st.execute("INSERT INTO orden VALUES (1, TIMESTAMP WITH TIME ZONE '2026-11-18 10:15:30-03:00')");
            String mapping = PREFIXES + """
                    <#Orden> rr:logicalTable [ rr:tableName "ORDEN" ] ;
                        rr:subjectMap [ rr:template "http://e.org/orden/{ID}" ] ;
                        rr:predicateObjectMap [ rr:predicate ex:cuando ; rr:objectMap [ rr:column "CUANDO" ] ] .
                    """;
            Graph g = R2RMLProcessor.execute(R2RMLProcessor.parse(mapping, "http://e.org/"), c).getDefaultGraph();
            Node o = g.find(NodeFactory.createURI("http://e.org/orden/1"), NodeFactory.createURI("http://e.org/cuando"), Node.ANY)
                    .next().getObject();
            assertEquals("http://www.w3.org/2001/XMLSchema#dateTime", o.getLiteralDatatypeURI());
            assertEquals("2026-11-18T10:15:30-03:00", o.getLiteralLexicalForm());
        }
    }
}
