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
}
