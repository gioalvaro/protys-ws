package org.protys.ws.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Filtro de forma de las consultas de los mapeos y límites de ejecución del procesador R2RML. */
class SqlQueryGuardTest {

    @Test
    void aceptaSelectYCte() {
        assertEquals("SELECT id FROM t", SqlQueryGuard.check("  SELECT id FROM t  "));
        String cte = "WITH x AS (SELECT id FROM t) SELECT id FROM x";
        assertEquals(cte, SqlQueryGuard.check(cte));
        assertEquals("with recursive r(n) as (select 1 union all select n+1 from r where n < 3) select n from r",
                SqlQueryGuard.check("with recursive r(n) as (select 1 union all select n+1 from r where n < 3) select n from r"));
    }

    @Test
    void unPuntoYComaFinalSeDescartaComoEnLosCasosW3C() {
        assertEquals("SELECT id FROM t WHERE a = 'EN'", SqlQueryGuard.check("SELECT id FROM t WHERE a = 'EN';"));
        assertFalse(SqlQueryGuard.check("SELECT id FROM t ; -- fin").contains(";"));
    }

    @Test
    void lasPalabrasDentroDeLiteralesComentariosOComillasNoCuentan() {
        SqlQueryGuard.check("SELECT 'INSERT INTO x; FOR UPDATE' AS texto FROM t");
        SqlQueryGuard.check("SELECT \"into\", \"for update\" FROM t -- INTO ; DELETE");
        SqlQueryGuard.check("SELECT id FROM t /* SELECT INTO /* anidado; */ ; */");
        SqlQueryGuard.check("SELECT $$; INTO$$ AS a, $tag$ FOR SHARE $tag$ AS b FROM t");
        SqlQueryGuard.check("SELECT pt.name->>'en_US' AS name, 'it''s; fine' FROM product_template pt");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "SELECT id INTO copia FROM t | INTO",
            "SELECT id FROM t FOR UPDATE | FOR UPDATE",
            "SELECT id FROM t FOR NO KEY UPDATE | FOR NO KEY UPDATE",
            "SELECT id FROM t FOR SHARE | FOR SHARE",
            "SELECT id FROM t for  key share | FOR KEY SHARE",
            "SELECT 1; DROP TABLE t | ';'",
            "SELECT 1; SELECT 2; | ';'",
            "UPDATE t SET a = 1 | found UPDATE",
            "DELETE FROM t | found DELETE",
            "WITH d AS (DELETE FROM t RETURNING id) SELECT id FROM d | DELETE",
            "WITH x AS (SELECT 1) INSERT INTO t SELECT * FROM x | INSERT",
            "SELECT 'sin cerrar FROM t | unterminated string literal",
            "SELECT id FROM t /* sin cerrar | unterminated comment",
    })
    void rechazaYDiceQue(String sql, String motivo) {
        var error = assertThrows(SqlQueryGuard.RejectedQuery.class, () -> SqlQueryGuard.check(sql));
        assertTrue(error.getMessage().contains(motivo), error.getMessage());
    }

    @Test
    void lasCadenasDeEscapeNoEscondenUnPuntoYComa() {
        // Reproducción de la segunda revisión: antes se aceptaba entera.
        var error = assertThrows(SqlQueryGuard.RejectedQuery.class,
                () -> SqlQueryGuard.check("SELECT E'\\'' ; COMMIT; DELETE FROM t; --'"));
        assertTrue(error.getMessage().contains("';' is not allowed"), error.getMessage());
        // E'a\'b' es una sola cadena: la comilla escapada no la cierra.
        assertEquals("SELECT E'a\\'b' AS x FROM t", SqlQueryGuard.check("SELECT E'a\\'b' AS x FROM t"));
        assertEquals("SELECT e'a\\'b' AS x FROM t", SqlQueryGuard.check("SELECT e'a\\'b' AS x FROM t"));
        // E'\\' es una barra escapada: la cadena termina ahí y el ';' siguiente es intermedio.
        var barra = assertThrows(SqlQueryGuard.RejectedQuery.class,
                () -> SqlQueryGuard.check("SELECT E'\\\\' ; DELETE FROM t"));
        assertTrue(barra.getMessage().contains("';' is not allowed"), barra.getMessage());
    }

    @Test
    void unaBarraEnUnaCadenaComunSeRechazaPorqueDependeDelServidor() {
        var error = assertThrows(SqlQueryGuard.RejectedQuery.class,
                () -> SqlQueryGuard.check("SELECT '\\'' ; DELETE FROM t; --'"));
        assertTrue(error.getMessage().contains("standard_conforming_strings"), error.getMessage());
        SqlQueryGuard.check("SELECT U&'data' AS x FROM t"); // U&'…' sin barra: igual que una cadena común
    }

    @Test
    void elProcesadorRechazaElMapeoConElMotivo() {
        String mapping = mapping("SELECT id FROM t FOR UPDATE");
        var error = assertThrows(RuntimeException.class, () -> R2RMLProcessor.parse(mapping, "http://e.org/"));
        assertTrue(error.getMessage().contains("FOR UPDATE is not allowed"), error.getMessage());
    }

    @Test
    void superarElTopeDeFilasEsUnErrorNoUnTruncado() throws Exception {
        String jdbc = "jdbc:h2:mem:tope_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(jdbc, "sa", "")) {
            var parsed = R2RMLProcessor.parse(mapping("SELECT X AS ID FROM SYSTEM_RANGE(1, 10)"), "http://e.org/");
            assertEquals(10, R2RMLProcessor.execute(parsed, connection, new R2RMLProcessor.Limits(5, 10)).getDefaultGraph().size());
            var error = assertThrows(R2RMLProcessor.R2RMLDataException.class,
                    () -> R2RMLProcessor.execute(parsed, connection, new R2RMLProcessor.Limits(5, 9)));
            assertTrue(error.getMessage().contains("returned more than 9 rows"), error.getMessage());
        }
    }

    @Test
    void unaConsultaQueSuperaElTiempoMaximoFallaConMensaje() throws Exception {
        String jdbc = "jdbc:h2:mem:lenta_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(jdbc, "sa", "")) {
            // Recorre miles de millones de filas: con 1 s de tiempo máximo la base la cancela.
            var parsed = R2RMLProcessor.parse(mapping(
                    "SELECT X AS ID FROM SYSTEM_RANGE(1, 5000000000) WHERE MOD(X * 7, 1000000007) = -1"), "http://e.org/");
            long start = System.nanoTime();
            var error = assertThrows(R2RMLProcessor.R2RMLDataException.class,
                    () -> R2RMLProcessor.execute(parsed, connection, new R2RMLProcessor.Limits(1, 100)));
            assertTrue(error.getMessage().contains("exceeded the query timeout of 1 s"), error.getMessage());
            assertTrue((System.nanoTime() - start) / 1_000_000_000L < 30, "el tiempo máximo no cortó la consulta");
        }
    }

    static String mapping(String sql) {
        return """
                @prefix rr: <http://www.w3.org/ns/r2rml#> .
                <#T> rr:logicalTable [ rr:sqlQuery \"\"\"%s\"\"\" ] ;
                     rr:subjectMap [ rr:template "http://e.org/item/{ID}" ; rr:class <http://e.org/Item> ] .
                """.formatted(sql);
    }
}
