package org.protys.ws.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sólo lectura de la conexión del conector, contra un PostgreSQL real y desechable (nunca la base de un ERP).
 * El usuario configurado TIENE permiso de escritura sobre la tabla {@code prueba}: así lo que se prueba es la
 * conexión de la aplicación, no el rol. Se activa con PROTYS_TEST_PG_URL, PROTYS_TEST_PG_USER y
 * PROTYS_TEST_PG_PASSWORD; lo corre research/integration/revision-2026-11-18/solo-lectura.sh.
 */
class ERPReadOnlyConnectionIntegrationTest {

    String url, user, password;

    @BeforeEach
    void base() throws SQLException {
        url = System.getenv("PROTYS_TEST_PG_URL");
        user = System.getenv("PROTYS_TEST_PG_USER");
        password = System.getenv("PROTYS_TEST_PG_PASSWORD");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "sin PostgreSQL desechable configurado");
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS prueba (i int)");
            st.execute("TRUNCATE prueba");
        }
    }

    long filas() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement();
             var rs = st.executeQuery("SELECT count(*) FROM prueba")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Lo que hacía el servicio antes: setReadOnly(true) con autocommit activo. Se registra lo que pasa. */
    @Test
    void comprobacionConAutocommitActivo() throws SQLException {
        String resultado;
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setReadOnly(true);
            try (var st = c.createStatement()) {
                st.executeUpdate("INSERT INTO prueba VALUES (1)");
                resultado = "INSERT aceptado";
            } catch (SQLException e) {
                resultado = "INSERT rechazado, SQLSTATE " + e.getSQLState() + ": " + e.getMessage();
            }
            System.out.println("PGJDBC autocommit=" + c.getAutoCommit() + " readOnly=" + c.isReadOnly()
                    + " driver=" + c.getMetaData().getDriverVersion()
                    + " servidor=" + c.getMetaData().getDatabaseProductVersion() + " -> " + resultado
                    + "; filas en prueba=" + filas());
        }
    }

    @Test
    void laConexionDelConectorRechazaEscriturasAunqueElUsuarioPuedaEscribir() throws SQLException {
        try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
            assertFalse(c.getAutoCommit());
            assertTrue(c.isReadOnly());
            try (var st = c.createStatement(); var rs = st.executeQuery("SHOW transaction_read_only")) {
                rs.next();
                assertEquals("on", rs.getString(1));
            }
            SQLException error = assertThrows(SQLException.class, () -> {
                try (var st = c.createStatement()) { st.executeUpdate("INSERT INTO prueba VALUES (2)"); }
            });
            assertEquals("25006", error.getSQLState(), error.getMessage());
            System.out.println("PGJDBC conexión del conector (autocommit=false, readOnly=true) -> INSERT rechazado, SQLSTATE "
                    + error.getSQLState() + ": " + error.getMessage());
            c.rollback();
        }
        assertEquals(0, filas());
    }

    @Test
    void laConexionDelConectorLeeNormalmente() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            st.executeUpdate("INSERT INTO prueba VALUES (7)");
        }
        try (Connection c = ERPConnectorService.openReadOnly(url, user, password); var st = c.createStatement();
             var rs = st.executeQuery("SELECT i FROM prueba")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
            c.rollback();
        }
    }

    /**
     * Comprobación (no afirma nada): varias sentencias en una sola consulta, sin pasar por el filtro de SQL,
     * por la conexión del conector, que se cierra como lo hace el servicio (rollback y close).
     */
    @Test
    void comprobacionVariasSentenciasPorLaConexionDelConector() throws SQLException {
        String[] variantes = {
                "SELECT 1; COMMIT; INSERT INTO prueba VALUES (99)",
                "SELECT 1; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT",
                "SELECT 1; SET TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)"};
        for (String sql : variantes) {
            for (String metodo : new String[]{"executeQuery", "execute"}) {
                try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
                    st.execute("TRUNCATE prueba");
                }
                String resultado;
                try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
                    try (var st = c.createStatement()) {
                        if (metodo.equals("executeQuery")) st.executeQuery(sql); else st.execute(sql);
                        resultado = "aceptada";
                    } catch (SQLException e) {
                        resultado = "rechazada, SQLSTATE " + e.getSQLState() + ": " + e.getMessage().replace('\n', ' ');
                    } finally {
                        try { c.rollback(); } catch (SQLException ignored) { }
                    }
                }
                System.out.println("PGJDBC COMPROBACION " + metodo + " [" + sql + "] -> " + resultado + "; filas en prueba=" + filas());
            }
        }
    }

    /**
     * Comprobación (no afirma nada): las mismas variantes con cada configuración de conexión candidata,
     * para elegir la más simple que impida escribir sin depender del filtro.
     */
    @Test
    void comprobacionConfiguracionesDeConexion() throws SQLException {
        String[] variantes = {
                "SELECT 1; COMMIT; INSERT INTO prueba VALUES (99)",
                "SELECT 1; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT",
                "SELECT 1; SET TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)",
                "SELECT 1; COMMIT; SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)"};
        String[][] configuraciones = {
                {"session-characteristics", null, null},
                {"options-default_transaction_read_only", "options", "-c default_transaction_read_only=on"},
                {"readOnlyMode=always", "readOnlyMode", "always"},
                {"preferQueryMode=simple", "preferQueryMode", "simple"},
                {"preferQueryMode=extended", "preferQueryMode", "extended"},
                {"preferQueryMode=extendedForPrepared", "preferQueryMode", "extendedForPrepared"}};
        for (String[] conf : configuraciones) {
            for (String sql : variantes) {
                for (String metodo : new String[]{"executeQuery", "execute"}) {
                    try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
                        st.execute("TRUNCATE prueba");
                    }
                    var props = new java.util.Properties();
                    props.setProperty("user", user);
                    props.setProperty("password", password);
                    if (conf[1] != null) props.setProperty(conf[1], conf[2]);
                    String resultado;
                    try (Connection c = DriverManager.getConnection(url, props)) {
                        c.setAutoCommit(false);
                        c.setReadOnly(true);
                        if (conf[0].equals("session-characteristics"))
                            try (var st = c.createStatement()) { st.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"); }
                        try (var st = c.createStatement()) {
                            if (metodo.equals("executeQuery")) st.executeQuery(sql); else st.execute(sql);
                            resultado = "aceptada";
                        } catch (SQLException e) {
                            resultado = "rechazada, SQLSTATE " + e.getSQLState() + ": " + e.getMessage().replace('\n', ' ');
                        } finally {
                            try { c.rollback(); } catch (SQLException ignored) { }
                        }
                    }
                    System.out.println("PGJDBC CONFIG " + conf[0] + " " + metodo + " [" + sql + "] -> " + resultado + "; filas=" + filas());
                }
            }
        }
    }

    /** Tres entradas donde PostgreSQL ve un ';' que el analizador propio del filtro no ve (ronda 3). */
    static final String SALTEO_A = "SELECT 1 AS a_$x$ ; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 1 AS b_$x$";
    static final String SALTEO_B = "SELECT $é$ ' $é$ ; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 1 --'";
    static final String SALTEO_C = "SELECT 1 --\r; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT; SELECT 2";

    /**
     * Comprobación (no afirma nada): qué dice el filtro de cada salteo y qué pasa si la consulta llega a la
     * conexión del conector con un usuario que puede escribir.
     */
    @Test
    void comprobacionSalteosDelFiltro() throws SQLException {
        String[][] casos = {{"A", SALTEO_A}, {"B", SALTEO_B}, {"C", SALTEO_C}};
        for (String[] caso : casos) {
            String sql = caso[1];
            String filtro;
            try {
                SqlQueryGuard.check(sql);
                filtro = "aceptada";
            } catch (SqlQueryGuard.RejectedQuery e) {
                filtro = "rechazada: " + e.getMessage();
            }
            try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
                st.execute("TRUNCATE prueba");
            }
            String conexion;
            try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
                try (var st = c.createStatement()) {
                    st.executeQuery(sql);
                    conexion = "aceptada";
                } catch (SQLException e) {
                    conexion = "rechazada, SQLSTATE " + e.getSQLState() + ": " + e.getMessage().replace('\n', ' ');
                } finally {
                    try { c.rollback(); } catch (SQLException ignored) { }
                }
            }
            System.out.println("PGJDBC SALTEO " + caso[0] + " [" + sql.replace("\r", "\\r") + "] filtro -> " + filtro
                    + "; executeQuery por la conexión del conector -> " + conexion + "; filas en prueba=" + filas());
        }
    }

    static final String COMMIT_INSERT ="SELECT 1; COMMIT; INSERT INTO prueba VALUES (99)";
    static final String BEGIN_READ_WRITE = "SELECT 1; COMMIT; BEGIN READ WRITE; INSERT INTO prueba VALUES (99); COMMIT";
    static final String SET_TRANSACTION = "SELECT 1; SET TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)";
    static final String SET_SESSION = "SELECT 1; COMMIT; SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE; INSERT INTO prueba VALUES (99)";

    /**
     * Camino completo: un mapeo R2RML con cada salteo como rr:sqlQuery. Se rechaza al analizarlo y, si no se
     * rechazara, se ejecuta por la conexión del conector con un usuario que puede escribir: prueba queda sin filas.
     */
    @Test
    void losSalteosSeRechazanAlAnalizarElMapeoYNoEscriben() throws Exception {
        for (String sql : new String[]{SALTEO_A, SALTEO_B, SALTEO_C}) {
            String mapping = SqlQueryGuardTest.mapping(sql);
            RuntimeException rechazo = null;
            try {
                var parsed = R2RMLProcessor.parse(mapping, "http://e.org/");
                try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
                    try { R2RMLProcessor.execute(parsed, c); } catch (Exception ignored) { }
                    finally { try { c.rollback(); } catch (SQLException ignored) { } }
                }
            } catch (RuntimeException e) {
                rechazo = e;
            }
            assertEquals(0, filas(), "escribió con: " + sql.replace("\r", "\\r"));
            assertNotNull(rechazo, "el mapeo se aceptó: " + sql.replace("\r", "\\r"));
            assertTrue(rechazo.getMessage().contains("';' is not allowed"), rechazo.getMessage());
        }
    }

    /**
     * Comprobación del supuesto que sostiene el control del divisor (no afirma nada): en el protocolo extendido,
     * ¿el servidor rechaza un mensaje Parse con dos sentencias? Se construye una cadena que el divisor, con
     * standard_conforming_strings apagado, cuenta como una, pero que el servidor (con esa opción activa) ve como
     * dos, y se la manda en un único Parse por la API de bajo nivel de pgjdbc, con un usuario que puede escribir
     * y autocommit activo. Como contraste, la misma cadena por una conexión con preferQueryMode=simple.
     */
    @Test
    void comprobacionUnParseConDosSentencias() throws SQLException {
        String sql = "SELECT 'a\\'; INSERT INTO prueba VALUES (99) --'";
        var nativas = org.postgresql.core.Parser.parseJdbcSql(sql, false, false, true, false, true);
        // QUERY_SUPPRESS_BEGIN: con autocommit, como hace la capa de Statement; sin él el ejecutor abre un BEGIN
        // que nadie confirma y lo escrito se deshace al cerrar.
        int sinBegin = org.postgresql.core.QueryExecutor.QUERY_SUPPRESS_BEGIN;
        for (String[] modo : new String[][]{{"extendido", String.valueOf(sinBegin)},
                {"simple", String.valueOf(sinBegin | org.postgresql.core.QueryExecutor.QUERY_EXECUTE_AS_SIMPLE)}}) {
            try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
                st.execute("TRUNCATE prueba");
            }
            String resultado;
            String scs;
            var props = new java.util.Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);
            if (modo[0].equals("simple")) props.setProperty("preferQueryMode", "simple");
            try (Connection c = DriverManager.getConnection(url, props)) {
                try (var st = c.createStatement(); var rs = st.executeQuery("SHOW standard_conforming_strings")) {
                    rs.next();
                    scs = rs.getString(1);
                }
                var qe = c.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();
                var query = qe.wrap(nativas);
                var handler = new org.postgresql.core.ResultHandlerBase();
                try {
                    qe.execute(query, query.createParameterList(), handler, 0, 0, Integer.parseInt(modo[1]));
                    handler.handleCompletion();
                    resultado = "aceptada";
                } catch (SQLException e) {
                    resultado = "rechazada, SQLSTATE " + e.getSQLState() + ": " + e.getMessage().replace('\n', ' ');
                }
            }
            System.out.println("PGJDBC PARSE " + modo[0] + " [" + sql + "] sentencias según el divisor (scs=off)=" + nativas.size()
                    + "; standard_conforming_strings del servidor=" + scs + " -> " + resultado + "; filas en prueba=" + filas());
        }
    }

    /** El divisor se apoya en el protocolo extendido: una URL que pida el simple se rechaza al abrir. */
    @Test
    void laConexionDelConectorRechazaLosModosQueMandanVariasSentenciasJuntas() throws SQLException {
        String sep = url.contains("?") ? "&" : "?";
        for (String modo : new String[]{"simple", "extendedForPrepared"}) {
            var error = assertThrows(SQLException.class,
                    () -> ERPConnectorService.openReadOnly(url + sep + "preferQueryMode=" + modo, user, password).close());
            assertTrue(error.getMessage().contains("preferQueryMode=" + modo + " is not allowed"), error.getMessage());
        }
        for (String modo : new String[]{"extended", "extendedCacheEverything"})
            ERPConnectorService.openReadOnly(url + sep + "preferQueryMode=" + modo, user, password).close();
    }

    /** URLs que intentan deshacer la sólo lectura que configura openReadOnly (cuarta revisión). */
    static final String[] URLS_QUE_PISAN = {
            "options=-c%20default_transaction_read_only%3Doff",
            "readOnlyMode=ignore&options=-c%20default_transaction_read_only%3Doff",
            "readOnlyMode=ignore",
            "readOnly=false"};

    String conParametros(String parametros) {
        return url + (url.contains("?") ? "&" : "?") + parametros;
    }

    void crearFuncionQueEscribe() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            st.execute("CREATE OR REPLACE FUNCTION escribe() RETURNS int LANGUAGE sql AS "
                    + "'INSERT INTO prueba VALUES (5) RETURNING 1'");
        }
    }

    /**
     * Abre con openReadOnly y registra el estado de la sesión y si un INSERT y SELECT escribe() quedan escritos
     * con un commit explícito.
     */
    String sondear(String parametros) throws SQLException {
        String linea;
        try (Connection c = ERPConnectorService.openReadOnly(parametros.isEmpty() ? url : conParametros(parametros), user, password)) {
            String defaultRo, txRo;
            try (var st = c.createStatement();
                 var rs = st.executeQuery("SELECT current_setting('default_transaction_read_only'), current_setting('transaction_read_only')")) {
                rs.next();
                defaultRo = rs.getString(1);
                txRo = rs.getString(2);
            }
            c.rollback();
            linea = "default_ro=" + defaultRo + " tx_ro=" + txRo;
            for (String sql : new String[]{"INSERT INTO prueba VALUES (1)", "SELECT escribe()"}) {
                try (Connection l = DriverManager.getConnection(url, user, password); var st = l.createStatement()) {
                    st.execute("TRUNCATE prueba");
                }
                String r;
                try (var st = c.createStatement()) {
                    st.execute(sql);
                    c.commit();
                    r = "aceptada";
                } catch (SQLException e) {
                    r = "rechazada " + e.getSQLState();
                    try { c.rollback(); } catch (SQLException ignored) { }
                }
                linea += "; [" + sql + "] " + r + " filas=" + filas();
            }
        } catch (SQLException e) {
            linea = "conexión rechazada: " + e.getMessage().replace('\n', ' ');
        }
        return linea;
    }

    /** Comprobación (no afirma nada): qué deja la URL del conector después de openReadOnly. */
    @Test
    void comprobacionUrlQuePisaLaSoloLectura() throws SQLException {
        crearFuncionQueEscribe();
        System.out.println("PGJDBC URL [sin parámetros] " + sondear(""));
        for (String p : URLS_QUE_PISAN)
            System.out.println("PGJDBC URL [?" + p + "] " + sondear(p));
    }

    /** Ejecuta como el procesador (executeQuery) y como execute, y cierra como el servicio. */
    void intentar(String sql, String usuario, String clave) throws SQLException {
        for (boolean query : new boolean[]{true, false}) {
            try (Connection c = ERPConnectorService.openReadOnly(url, usuario, clave)) {
                try (var st = c.createStatement()) {
                    if (query) st.executeQuery(sql); else st.execute(sql);
                } catch (SQLException expected) {
                    // Lo que importa es si quedó escrito, no el mensaje.
                } finally {
                    try { c.rollback(); } catch (SQLException ignored) { }
                }
            }
        }
    }

    @Test
    void laConexionDelConectorImpideEscribirDespuesDeCommitYConSetReadWrite() throws SQLException {
        for (String sql : new String[]{COMMIT_INSERT, SET_TRANSACTION, SET_SESSION}) {
            intentar(sql, user, password);
            assertEquals(0, filas(), "escribió con: " + sql);
        }
    }

    /**
     * BEGIN READ WRITE explícito pasa por encima de la configuración de la conexión (comprobado): lo frenan el
     * filtro de SQL, que rechaza el ';' intermedio, y un rol que sólo puede leer, que no depende de la consulta.
     */
    @Test
    void beginReadWriteLoFrenanElFiltroYElRolNoLaConexion() throws SQLException {
        var rechazo = assertThrows(SqlQueryGuard.RejectedQuery.class, () -> SqlQueryGuard.check(BEGIN_READ_WRITE));
        assertTrue(rechazo.getMessage().contains("';' is not allowed"));
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            st.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lector') THEN "
                    + "CREATE ROLE lector LOGIN PASSWORD 'lector_demo'; END IF; END $$");
            st.execute("REVOKE ALL ON prueba FROM lector");
            st.execute("GRANT SELECT ON prueba TO lector");
        }
        intentar(BEGIN_READ_WRITE, "lector", "lector_demo");
        assertEquals(0, filas(), "un rol con sólo SELECT no debería poder escribir");
    }

    /**
     * En PostgreSQL, pgjdbc informa timestamptz como TIMESTAMP: sin la rama propia el valor saldría sin zona. Se
     * conserva el instante, no el desplazamiento escrito ('10:15:30-03' queda '13:15:30Z').
     */
    @Test
    void timestamptzDePostgresqlConservaElDesplazamiento() throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS orden");
            st.execute("CREATE TABLE orden (id int PRIMARY KEY, cuando timestamptz)");
            st.execute("INSERT INTO orden VALUES (1, '2026-11-18 10:15:30-03')");
        }
        String mapping = """
                @prefix rr: <http://www.w3.org/ns/r2rml#> .
                <#Orden> rr:logicalTable [ rr:tableName "orden" ] ;
                    rr:subjectMap [ rr:template "http://e.org/orden/{id}" ] ;
                    rr:predicateObjectMap [ rr:predicate <http://e.org/cuando> ; rr:objectMap [ rr:column "cuando" ] ] .
                """;
        try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
            var graph = R2RMLProcessor.execute(R2RMLProcessor.parse(mapping, "http://e.org/"), c).getDefaultGraph();
            var o = graph.find(org.apache.jena.graph.NodeFactory.createURI("http://e.org/orden/1"),
                    org.apache.jena.graph.NodeFactory.createURI("http://e.org/cuando"), org.apache.jena.graph.Node.ANY).next().getObject();
            c.rollback();
            assertEquals("http://www.w3.org/2001/XMLSchema#dateTime", o.getLiteralDatatypeURI());
            String lexical = o.getLiteralLexicalForm();
            assertTrue(lexical.matches(".*(Z|[+-]\\d\\d:\\d\\d)$"), "sin desplazamiento: " + lexical);
            assertEquals(java.time.OffsetDateTime.parse("2026-11-18T10:15:30-03:00").toInstant(),
                    java.time.OffsetDateTime.parse(lexical).toInstant());
            System.out.println("PGJDBC timestamptz '2026-11-18 10:15:30-03' -> \"" + lexical + "\"^^xsd:dateTime");
        }
    }

    /** El tiempo máximo corta la consulta en PostgreSQL (SQLSTATE 57014) con el mensaje del procesador. */
    @Test
    void elTiempoMaximoCortaUnaConsultaLentaEnPostgresql() throws Exception {
        var parsed = R2RMLProcessor.parse(SqlQueryGuardTest.mapping("SELECT 1 AS id FROM pg_sleep(5)"), "http://e.org/");
        try (Connection c = ERPConnectorService.openReadOnly(url, user, password)) {
            long start = System.nanoTime();
            var error = assertThrows(R2RMLProcessor.R2RMLDataException.class,
                    () -> R2RMLProcessor.execute(parsed, c, new R2RMLProcessor.Limits(1, 100)));
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            c.rollback();
            assertTrue(error.getMessage().contains("exceeded the query timeout of 1 s"), error.getMessage());
            assertTrue(seconds < 4, "no cortó a tiempo: " + seconds + " s");
            System.out.println("PGJDBC pg_sleep(5) con límite de 1 s -> " + error.getMessage() + " (" + seconds + " s)");
        }
    }
}
