package org.protys.ws.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro de forma para las consultas de los mapeos ERP ({@code rr:sqlQuery} y el formato XML heredado).
 *
 * <p>Acepta una sola consulta {@code SELECT …} o {@code WITH … SELECT …}. Fuera de literales, identificadores
 * entre comillas y comentarios rechaza {@code INTO}, {@code FOR UPDATE}/{@code FOR SHARE} (y sus variantes
 * {@code NO KEY}/{@code KEY}), las CTE que modifican datos ({@code INSERT}, {@code UPDATE}, {@code DELETE},
 * {@code MERGE}) y cualquier {@code ;}, salvo uno solo al final, que se descarta (los casos W3C D011 y D015
 * terminan así).
 *
 * <p>Cadenas: en {@code E'…'} la barra invertida escapa el carácter siguiente; en las comunes (y {@code U&'…'})
 * sólo {@code ''} escapa una comilla, y una barra invertida se rechaza, porque su sentido depende de
 * {@code standard_conforming_strings} en el servidor. Ante la duda se rechaza: nunca se acepta algo que este
 * análisis no entiende.
 *
 * <p>Rechazos conocidos de consultas válidas, a propósito: una consulta que empieza con paréntesis
 * ({@code (SELECT …) UNION (SELECT …)}); un {@code WITH} con una columna sin comillas llamada {@code insert},
 * {@code update}, {@code delete} o {@code merge} (entre comillas se acepta); y una barra invertida en una cadena
 * que no sea {@code E'…'}, también en {@code U&'…'}.
 *
 * <p><b>No es una barrera de seguridad.</b> Un filtro de texto no puede garantizar que una consulta no
 * escriba (por ejemplo, una función con efectos llamada desde un SELECT). La conexión que abre
 * {@link ERPConnectorService} rechaza casi todas las escrituras, pero no un {@code BEGIN READ WRITE} explícito
 * en una consulta de varias sentencias, que hoy frena este filtro. La garantía que no depende de la consulta es
 * el rol de la base, con permiso de SELECT sólo sobre las tablas mapeadas.
 */
public final class SqlQueryGuard {

    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*([A-Za-z]+)");
    private static final Pattern SELECT = Pattern.compile("\\bSELECT\\b");
    private static final Pattern INTO = Pattern.compile("\\bINTO\\b");
    private static final Pattern LOCKING = Pattern.compile("\\bFOR\\s+(?:NO\\s+KEY\\s+UPDATE|KEY\\s+SHARE|UPDATE|SHARE)\\b");
    private static final Pattern WRITE_IN_CTE = Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE)\\b");

    private SqlQueryGuard() {}

    /** Error de forma: el mensaje dice qué se rechazó. */
    public static class RejectedQuery extends IllegalArgumentException {
        RejectedQuery(String message) { super(message); }
    }

    /** Devuelve la consulta sin el {@code ;} final, o lanza {@link RejectedQuery} con el motivo. */
    public static String check(String sql) {
        if (sql == null || sql.isBlank()) throw new RejectedQuery("empty SQL query");
        String code = codeOnly(sql);
        String trimmed = sql.strip();
        String codeTrimmed = code.strip();
        if (codeTrimmed.endsWith(";")) {
            // Un único ';' al final: se quita del texto original en la misma posición.
            int last = code.lastIndexOf(';');
            trimmed = (sql.substring(0, last) + sql.substring(last + 1)).strip();
            code = code.substring(0, last) + " " + code.substring(last + 1);
        }
        if (code.indexOf(';') >= 0) throw new RejectedQuery("';' is not allowed: a mapping query must be a single statement");
        String upper = code.toUpperCase(Locale.ROOT);
        Matcher first = FIRST_WORD.matcher(upper);
        String keyword = first.find() ? first.group(1) : "";
        if (keyword.equals("WITH")) {
            if (!SELECT.matcher(upper).find()) throw new RejectedQuery("WITH must be followed by a SELECT");
            Matcher write = WRITE_IN_CTE.matcher(upper);
            if (write.find()) throw new RejectedQuery(write.group(1) + " is not allowed: data-modifying WITH is rejected");
        } else if (!keyword.equals("SELECT")) {
            throw new RejectedQuery("only SELECT or WITH … SELECT is allowed, found " + (keyword.isEmpty() ? "no keyword" : keyword));
        }
        if (INTO.matcher(upper).find()) throw new RejectedQuery("INTO is not allowed: SELECT … INTO creates a table");
        Matcher lock = LOCKING.matcher(upper);
        if (lock.find()) throw new RejectedQuery(lock.group().replaceAll("\\s+", " ") + " is not allowed: row locks need write access");
        return trimmed;
    }

    /**
     * El texto con literales, identificadores entre comillas, comentarios y cadenas con dólar
     * (PostgreSQL) reemplazados por espacios, conservando las posiciones.
     */
    static String codeOnly(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0, n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                // Cadena de escape E'…' (PostgreSQL): la barra invertida escapa el carácter siguiente.
                boolean escape = c == '\'' && i > 0 && (sql.charAt(i - 1) == 'E' || sql.charAt(i - 1) == 'e')
                        && (i == 1 || !isIdentifierChar(sql.charAt(i - 2)));
                int end = i + 1;
                while (end < n) {
                    char d = sql.charAt(end);
                    if (escape && d == '\\') { end += 2; continue; }
                    // En una cadena común la barra es literal sólo si standard_conforming_strings está activo:
                    // como depende del servidor, se rechaza (ante la duda, rechazar).
                    if (!escape && c == '\'' && d == '\\')
                        throw new RejectedQuery("backslash in a string literal is ambiguous (depends on standard_conforming_strings); use E'…'");
                    if (d == c) {
                        if (end + 1 < n && sql.charAt(end + 1) == c) { end += 2; continue; }
                        break;
                    }
                    end++;
                }
                if (end >= n) throw new RejectedQuery("unterminated " + (c == '\'' ? "string literal" : "quoted identifier"));
                blank(out, end - i + 1);
                i = end + 1;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                blank(out, end - i);
                i = end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int depth = 1, end = i + 2;
                while (end < n && depth > 0) {
                    if (sql.startsWith("/*", end)) { depth++; end += 2; }
                    else if (sql.startsWith("*/", end)) { depth--; end += 2; }
                    else end++;
                }
                if (depth > 0) throw new RejectedQuery("unterminated comment");
                blank(out, end - i);
                i = end;
            } else if (c == '$') {
                Matcher tag = Pattern.compile("\\$[A-Za-z_]?[A-Za-z0-9_]*\\$").matcher(sql);
                if (tag.find(i) && tag.start() == i && (i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1)))) {
                    int close = sql.indexOf(tag.group(), tag.end());
                    if (close < 0) throw new RejectedQuery("unterminated dollar-quoted string");
                    int end = close + tag.group().length();
                    blank(out, end - i);
                    i = end;
                } else {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static void blank(StringBuilder out, int length) {
        for (int k = 0; k < length; k++) out.append(' ');
    }
}
