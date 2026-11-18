package org.protys.ws.service;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.irix.IRIs;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;
import org.protys.ws.exception.ProtysMappingException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Procesador R2RML (W3C, 2012) sobre Jena.
 *
 * <p>Implementa los términos de R2RML: tablas lógicas ({@code rr:tableName},
 * {@code rr:sqlQuery}), mapas de sujeto, predicado, objeto y grafo (constantes, columnas y
 * plantillas), {@code rr:termType}, {@code rr:datatype}, {@code rr:language}, {@code rr:class},
 * mapas de objeto referenciados con {@code rr:joinCondition} y la correspondencia natural de
 * tipos SQL a XSD. {@code rr:inverseExpression} se acepta y no se usa (la especificación lo
 * permite: es una pista de optimización).
 *
 * <p>Restricciones propias de PROTYS-WS, más estrictas que la especificación:
 * <ul>
 *   <li>{@code rr:sqlQuery} pasa por {@link SqlQueryGuard}: una sola consulta {@code SELECT} o
 *       {@code WITH … SELECT}, sin {@code INTO}, {@code FOR UPDATE/SHARE} ni {@code ;} intermedios. Es un
 *       control de forma, no de seguridad: la garantía de no escribir la dan la conexión de sólo lectura y
 *       el rol de la base (ver {@link ERPConnectorService}).</li>
 *   <li>{@code rr:tableName} sólo admite identificadores SQL (con o sin comillas, calificados con
 *       esquema).</li>
 *   <li>Cada consulta tiene tiempo máximo y tope de filas ({@link Limits}); superar el tope es un error,
 *       nunca se trunca.</li>
 *   <li>Cualquier término del espacio {@code rr:} que no se reconozca hace fallar el mapeo: nada se ignora
 *       en silencio.</li>
 * </ul>
 *
 * <p>Todo el mapeo se valida en {@link #parse} sin tocar la base; {@link #execute} sólo lee.
 */
public final class R2RMLProcessor {

    public static final String RR = "http://www.w3.org/ns/r2rml#";

    private static final Set<String> RR_TERMS = Set.of(
            "TriplesMap", "LogicalTable", "R2RMLView", "BaseTableOrView", "TermMap", "SubjectMap",
            "PredicateMap", "ObjectMap", "GraphMap", "PredicateObjectMap", "RefObjectMap", "Join",
            "logicalTable", "tableName", "sqlQuery", "sqlVersion", "SQL2008", "subjectMap", "subject",
            "predicateObjectMap", "predicateMap", "predicate", "objectMap", "object", "graphMap", "graph",
            "constant", "column", "template", "termType", "IRI", "BlankNode", "Literal", "datatype",
            "language", "class", "parentTriplesMap", "joinCondition", "child", "parent", "defaultGraph",
            "inverseExpression");

    // Identificador SQL simple o entre comillas dobles, opcionalmente calificado (esquema.tabla).
    private static final Pattern TABLE_NAME = Pattern.compile(
            "(?:[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")+\")(?:\\.(?:[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")+\")){0,2}");
    // Etiqueta BCP 47 con subetiqueta primaria ISO 639 (2–3 letras); rechaza "english" (caso R2RMLTC0015b).
    private static final Pattern LANGUAGE_TAG = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    enum Kind { CONSTANT, COLUMN, TEMPLATE }
    enum TermType { IRI, BLANK, LITERAL }
    enum Position { SUBJECT, PREDICATE, OBJECT, GRAPH }

    /** Mapa de término ya validado. {@code value}: nombre de columna o plantilla. */
    record TermMap(Kind kind, Node constant, String value, TermType termType, String datatype, String language) {}

    record Join(String child, String parent) {}

    record RefObjectMap(String parentId, List<Join> joins) {}

    record PredicateObjectMap(List<TermMap> predicates, List<TermMap> objects,
                              List<RefObjectMap> refs, List<TermMap> graphs) {}

    record TriplesMap(String id, String sql, TermMap subject, List<Node> classes,
                      List<TermMap> subjectGraphs, List<PredicateObjectMap> poms) {}

    /** Mapeo R2RML validado, listo para ejecutar. */
    public record Mapping(List<TriplesMap> triplesMaps, String baseIri) {
        public int size() { return triplesMaps.size(); }

        /** Verdadero si algún mapa de grafo apunta a un grafo distinto del grafo por defecto. */
        public boolean usesNamedGraphs() {
            for (TriplesMap tm : triplesMaps) {
                if (tm.subjectGraphs().stream().anyMatch(R2RMLProcessor::isNamedGraphMap)) return true;
                for (PredicateObjectMap pom : tm.poms()) {
                    if (pom.graphs().stream().anyMatch(R2RMLProcessor::isNamedGraphMap)) return true;
                }
            }
            return false;
        }
    }

    /** Error de datos según R2RML §11: el mapeo es válido pero una fila genera un término inválido. */
    public static class R2RMLDataException extends RuntimeException {
        public R2RMLDataException(String message) { super("R2RML_DATA_ERROR: " + message); }
    }

    private R2RMLProcessor() {}

    // ------------------------------------------------------------------ análisis del mapeo

    public static Mapping parse(String turtle, String baseIri) {
        Model model;
        try {
            model = RDFParser.fromString(turtle).lang(Lang.TURTLE).base(baseIri).toModel();
        } catch (Exception e) {
            throw unsupported("the mapping is not valid Turtle");
        }
        for (Statement st : model.listStatements().toList()) {
            checkVocabulary(st.getPredicate());
            if (st.getObject().isURIResource()) checkVocabulary(st.getObject().asResource());
        }

        Set<Resource> candidates = new LinkedHashSet<>();
        model.listSubjectsWithProperty(rr(model, "logicalTable")).forEachRemaining(candidates::add);
        model.listSubjectsWithProperty(RDF.type, rr(model, "TriplesMap")).forEachRemaining(candidates::add);
        if (candidates.isEmpty()) throw unsupported("no rr:TriplesMap with rr:logicalTable found");

        Map<Resource, String> ids = new HashMap<>();
        int n = 0;
        for (Resource r : candidates) ids.put(r, r.isURIResource() ? r.getURI() : "_:tm" + (n++));

        List<TriplesMap> maps = new ArrayList<>();
        for (Resource r : candidates) maps.add(parseTriplesMap(model, r, ids));
        return new Mapping(List.copyOf(maps), baseIri);
    }

    private static TriplesMap parseTriplesMap(Model m, Resource tm, Map<Resource, String> ids) {
        String id = ids.get(tm);
        Resource table = single(m, tm, "logicalTable", id).asResource();
        String sql = logicalTableSql(m, table, id);

        RDFNode subjectNode = optionalSingle(m, tm, "subjectMap", id);
        RDFNode subjectShortcut = optionalSingle(m, tm, "subject", id);
        if ((subjectNode == null) == (subjectShortcut == null)) {
            throw unsupported("triples map " + id + " needs exactly one rr:subjectMap or rr:subject");
        }
        TermMap subject;
        List<Node> classes = new ArrayList<>();
        List<TermMap> subjectGraphs = new ArrayList<>();
        if (subjectShortcut != null) {
            subject = constantMap(subjectShortcut, Position.SUBJECT, id);
        } else {
            Resource sm = subjectNode.asResource();
            subject = termMap(m, sm, Position.SUBJECT, id);
            for (RDFNode c : m.listObjectsOfProperty(sm, rr(m, "class")).toList()) {
                if (!c.isURIResource()) throw unsupported("rr:class must be an IRI in " + id);
                classes.add(c.asNode());
            }
            subjectGraphs.addAll(graphMaps(m, sm, id));
        }

        List<PredicateObjectMap> poms = new ArrayList<>();
        for (RDFNode p : m.listObjectsOfProperty(tm, rr(m, "predicateObjectMap")).toList()) {
            poms.add(parsePom(m, p.asResource(), ids, id));
        }
        return new TriplesMap(id, sql, subject, List.copyOf(classes), List.copyOf(subjectGraphs), List.copyOf(poms));
    }

    private static String logicalTableSql(Model m, Resource table, String id) {
        RDFNode tableName = optionalSingle(m, table, "tableName", id);
        RDFNode query = optionalSingle(m, table, "sqlQuery", id);
        if ((tableName == null) == (query == null)) {
            throw unsupported("logical table of " + id + " needs exactly one rr:tableName or rr:sqlQuery");
        }
        if (tableName != null) {
            String name = literal(tableName, "rr:tableName", id).trim();
            if (!TABLE_NAME.matcher(name).matches()) throw unsupported("rr:tableName is not a SQL identifier in " + id);
            return "SELECT * FROM " + name;
        }
        try {
            return SqlQueryGuard.check(literal(query, "rr:sqlQuery", id));
        } catch (SqlQueryGuard.RejectedQuery rejected) {
            throw unsupported("rr:sqlQuery rejected in " + id + ": " + rejected.getMessage());
        }
    }

    private static PredicateObjectMap parsePom(Model m, Resource pom, Map<Resource, String> ids, String id) {
        List<TermMap> predicates = new ArrayList<>();
        for (RDFNode p : m.listObjectsOfProperty(pom, rr(m, "predicate")).toList()) {
            predicates.add(constantMap(p, Position.PREDICATE, id));
        }
        for (RDFNode p : m.listObjectsOfProperty(pom, rr(m, "predicateMap")).toList()) {
            predicates.add(termMap(m, p.asResource(), Position.PREDICATE, id));
        }
        List<TermMap> objects = new ArrayList<>();
        List<RefObjectMap> refs = new ArrayList<>();
        for (RDFNode o : m.listObjectsOfProperty(pom, rr(m, "object")).toList()) {
            objects.add(constantMap(o, Position.OBJECT, id));
        }
        for (RDFNode o : m.listObjectsOfProperty(pom, rr(m, "objectMap")).toList()) {
            Resource om = o.asResource();
            RDFNode parent = optionalSingle(m, om, "parentTriplesMap", id);
            if (parent == null) {
                objects.add(termMap(m, om, Position.OBJECT, id));
                continue;
            }
            if (!ids.containsKey(parent.asResource())) {
                throw unsupported("rr:parentTriplesMap of " + id + " is not a triples map");
            }
            List<Join> joins = new ArrayList<>();
            for (RDFNode j : m.listObjectsOfProperty(om, rr(m, "joinCondition")).toList()) {
                joins.add(new Join(
                        literal(single(m, j.asResource(), "child", id), "rr:child", id),
                        literal(single(m, j.asResource(), "parent", id), "rr:parent", id)));
            }
            refs.add(new RefObjectMap(ids.get(parent.asResource()), List.copyOf(joins)));
        }
        if (predicates.isEmpty() || (objects.isEmpty() && refs.isEmpty())) {
            throw unsupported("predicate-object map of " + id + " needs a predicate and an object");
        }
        return new PredicateObjectMap(List.copyOf(predicates), List.copyOf(objects), List.copyOf(refs),
                List.copyOf(graphMaps(m, pom, id)));
    }

    private static List<TermMap> graphMaps(Model m, Resource owner, String id) {
        List<TermMap> graphs = new ArrayList<>();
        for (RDFNode g : m.listObjectsOfProperty(owner, rr(m, "graph")).toList()) {
            graphs.add(constantMap(g, Position.GRAPH, id));
        }
        for (RDFNode g : m.listObjectsOfProperty(owner, rr(m, "graphMap")).toList()) {
            graphs.add(termMap(m, g.asResource(), Position.GRAPH, id));
        }
        return graphs;
    }

    private static TermMap termMap(Model m, Resource tm, Position position, String id) {
        RDFNode constant = optionalSingle(m, tm, "constant", id);
        RDFNode column = optionalSingle(m, tm, "column", id);
        RDFNode template = optionalSingle(m, tm, "template", id);
        int kinds = (constant != null ? 1 : 0) + (column != null ? 1 : 0) + (template != null ? 1 : 0);
        if (kinds != 1) throw unsupported("term map in " + id + " needs exactly one of rr:constant, rr:column, rr:template");
        if (constant != null) return constantMap(constant, position, id);

        RDFNode language = optionalSingle(m, tm, "language", id);
        RDFNode datatype = optionalSingle(m, tm, "datatype", id);
        RDFNode termTypeNode = optionalSingle(m, tm, "termType", id);

        TermType termType;
        if (termTypeNode != null) {
            if (!termTypeNode.isURIResource()) throw unsupported("rr:termType must be an IRI in " + id);
            termType = switch (termTypeNode.asResource().getURI().substring(RR.length())) {
                case "IRI" -> TermType.IRI;
                case "BlankNode" -> TermType.BLANK;
                case "Literal" -> TermType.LITERAL;
                default -> throw unsupported("unknown rr:termType in " + id);
            };
        } else if (position == Position.OBJECT && (column != null || language != null || datatype != null)) {
            termType = TermType.LITERAL;
        } else {
            termType = TermType.IRI;
        }
        if (termType == TermType.LITERAL && position != Position.OBJECT) {
            throw unsupported("only object maps can generate literals (" + id + ")");
        }
        if (termType == TermType.BLANK && (position == Position.PREDICATE || position == Position.GRAPH)) {
            throw unsupported("predicate and graph maps cannot generate blank nodes (" + id + ")");
        }
        if ((language != null || datatype != null) && termType != TermType.LITERAL) {
            throw unsupported("rr:language and rr:datatype need a literal term map (" + id + ")");
        }
        if (language != null && datatype != null) {
            throw unsupported("rr:language and rr:datatype cannot be used together (" + id + ")");
        }
        String lang = language == null ? null : literal(language, "rr:language", id);
        if (lang != null && !LANGUAGE_TAG.matcher(lang).matches()) {
            throw unsupported("rr:language is not a valid language tag in " + id);
        }
        if (datatype != null && !datatype.isURIResource()) throw unsupported("rr:datatype must be an IRI in " + id);

        if (column != null) {
            return new TermMap(Kind.COLUMN, null, literal(column, "rr:column", id), termType,
                    datatype == null ? null : datatype.asResource().getURI(), lang);
        }
        String tpl = literal(template, "rr:template", id);
        templateColumns(tpl, id); // valida llaves balanceadas y escapes
        return new TermMap(Kind.TEMPLATE, null, tpl, termType,
                datatype == null ? null : datatype.asResource().getURI(), lang);
    }

    private static TermMap constantMap(RDFNode value, Position position, String id) {
        if (value.isAnon()) throw unsupported("rr:constant cannot be a blank node (" + id + ")");
        if (value.isLiteral() && position != Position.OBJECT) {
            throw unsupported("only object maps can have a literal constant (" + id + ")");
        }
        TermType type = value.isLiteral() ? TermType.LITERAL : TermType.IRI;
        return new TermMap(Kind.CONSTANT, value.asNode(), null, type, null, null);
    }

    private static boolean isNamedGraphMap(TermMap g) {
        return !(g.kind() == Kind.CONSTANT && g.constant().isURI() && g.constant().getURI().equals(RR + "defaultGraph"));
    }

    // ------------------------------------------------------------------ ejecución

    /**
     * Ejecuta el mapeo y devuelve las cuádruplas generadas. Sólo lee de la conexión.
     * Las consultas de cada tabla lógica se ejecutan una vez.
     */
    /**
     * Límites de cada consulta: tiempo máximo en segundos (0 = sin límite, como JDBC) y cantidad máxima de
     * filas por tabla lógica (superarla es un error, nunca se trunca).
     */
    public record Limits(int queryTimeoutSeconds, long maxRows) {
        public static final Limits DEFAULT = new Limits(60, 500_000);
        public Limits {
            if (queryTimeoutSeconds < 0 || maxRows < 1) throw new IllegalArgumentException("invalid query limits");
        }
    }

    public static DatasetGraph execute(Mapping mapping, Connection connection) throws SQLException {
        return execute(mapping, connection, Limits.DEFAULT);
    }

    public static DatasetGraph execute(Mapping mapping, Connection connection, Limits limits) throws SQLException {
        Map<String, TriplesMap> byId = new LinkedHashMap<>();
        for (TriplesMap tm : mapping.triplesMaps()) byId.put(tm.id(), tm);
        Map<String, Table> tables = new HashMap<>();
        try {
            for (TriplesMap tm : mapping.triplesMaps()) tables.computeIfAbsent(tm.sql(), sql -> query(connection, sql, tm.id(), limits));
        } catch (SqlFailure failure) {
            throw (SQLException) failure.getCause();
        }

        Generator gen = new Generator(mapping.baseIri());
        DatasetGraph out = DatasetGraphFactory.create();
        for (TriplesMap tm : mapping.triplesMaps()) {
            Table table = tables.get(tm.sql());
            for (Map<String, Value> row : table.rows()) {
                Node subject = gen.term(tm.subject(), row, table, Position.SUBJECT);
                if (subject == null) continue;
                List<Node> subjectGraphs = gen.graphs(tm.subjectGraphs(), row, table);
                for (Node cls : tm.classes()) emit(out, targetGraphs(subjectGraphs, List.of()), subject, RDF.type.asNode(), cls);

                for (PredicateObjectMap pom : tm.poms()) {
                    List<Node> graphs = targetGraphs(subjectGraphs, gen.graphs(pom.graphs(), row, table));
                    List<Node> predicates = new ArrayList<>();
                    for (TermMap p : pom.predicates()) {
                        Node pn = gen.term(p, row, table, Position.PREDICATE);
                        if (pn != null) predicates.add(pn);
                    }
                    List<Node> objects = new ArrayList<>();
                    for (TermMap o : pom.objects()) {
                        Node on = gen.term(o, row, table, Position.OBJECT);
                        if (on != null) objects.add(on);
                    }
                    for (RefObjectMap ref : pom.refs()) {
                        TriplesMap parent = byId.get(ref.parentId());
                        objects.addAll(referencedObjects(gen, tm, table, row, parent, tables.get(parent.sql()), ref));
                    }
                    for (Node p : predicates) for (Node o : objects) emit(out, graphs, subject, p, o);
                }
            }
        }
        return out;
    }

    /**
     * Grafos destino de una tripla (R2RML §11.1): unión de los grafos del mapa de sujeto y del mapa
     * predicado-objeto; si no hay ninguno, el grafo por defecto.
     */
    private static List<Node> targetGraphs(List<Node> subjectGraphs, List<Node> pomGraphs) {
        List<Node> graphs = new ArrayList<>(subjectGraphs);
        for (Node g : pomGraphs) if (!graphs.contains(g)) graphs.add(g);
        if (graphs.isEmpty()) graphs.add(Quad.defaultGraphIRI);
        return graphs;
    }

    private static List<Node> referencedObjects(Generator gen, TriplesMap child, Table childTable,
                                                Map<String, Value> childRow, TriplesMap parent, Table parentTable,
                                                RefObjectMap ref) {
        List<Node> objects = new ArrayList<>();
        if (ref.joins().isEmpty()) {
            if (!child.sql().equals(parent.sql())) {
                throw unsupported("rr:parentTriplesMap without rr:joinCondition needs the same logical table ("
                        + child.id() + ")");
            }
            Node o = gen.term(parent.subject(), childRow, childTable, Position.OBJECT);
            if (o != null) objects.add(o);
            return objects;
        }
        for (Map<String, Value> parentRow : parentTable.rows()) {
            boolean match = true;
            for (Join j : ref.joins()) {
                Value c = childRow.get(childTable.resolve(j.child()));
                Value p = parentRow.get(parentTable.resolve(j.parent()));
                if (c == null || p == null || c.raw() == null || p.raw() == null || !c.joins(p)) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            Node o = gen.term(parent.subject(), parentRow, parentTable, Position.OBJECT);
            if (o != null && !objects.contains(o)) objects.add(o);
        }
        return objects;
    }

    private static void emit(DatasetGraph out, List<Node> graphs, Node s, Node p, Node o) {
        for (Node g : graphs) {
            out.add(Quad.isDefaultGraph(g) ? Quad.defaultGraphIRI : g, s, p, o);
        }
    }

    // ------------------------------------------------------------------ filas SQL

    record Value(Object raw, int sqlType, String lexical) {
        /**
         * Igualdad de claves de join (R2RML §8, igualdad SQL): si las dos son numéricas se comparan por valor
         * (un INTEGER 8 y un NUMERIC 8.0 son la misma clave); si no, por su forma léxica.
         */
        boolean joins(Value other) {
            if (raw instanceof Number a && other.raw instanceof Number b) {
                try {
                    return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
                } catch (NumberFormatException notFinite) {
                    return false; // NaN o infinito: nunca es igual en SQL
                }
            }
            return lexical.equals(other.lexical);
        }
    }

    /** Cómo pliega la base los identificadores SQL sin comillas. */
    enum Fold { UPPER, LOWER, NONE }

    record Table(List<String> columns, List<Map<String, Value>> rows, Fold fold) {
        /**
         * Resuelve un nombre de columna R2RML (identificador SQL) contra las columnas del resultado.
         * Entre comillas dobles es sensible a mayúsculas; sin comillas se pliega como lo hace la base
         * (SQL estándar y H2: mayúsculas; PostgreSQL: minúsculas). Caso W3C R2RMLTC0002f.
         */
        String resolve(String name) {
            String target;
            if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
                target = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            } else {
                target = switch (fold) {
                    case UPPER -> name.toUpperCase(Locale.ROOT);
                    case LOWER -> name.toLowerCase(Locale.ROOT);
                    case NONE -> name;
                };
            }
            if (!columns.contains(target)) throw unsupported("column " + name + " not found in logical table");
            return target;
        }
    }

    private static Fold fold(Connection connection) throws SQLException {
        var md = connection.getMetaData();
        if (md.storesUpperCaseIdentifiers()) return Fold.UPPER;
        if (md.storesLowerCaseIdentifiers()) return Fold.LOWER;
        return Fold.NONE;
    }

    private static Table query(Connection connection, String sql, String id, Limits limits) {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(limits.queryTimeoutSeconds());
            // Una fila de más alcanza para saber que se superó el tope sin leer el resto.
            if (limits.maxRows() < Integer.MAX_VALUE) statement.setMaxRows((int) limits.maxRows() + 1);
            return read(connection, statement.executeQuery(sql), id, limits);
        } catch (java.sql.SQLTimeoutException e) {
            throw new R2RMLDataException("logical table of " + id + " exceeded the query timeout of "
                    + limits.queryTimeoutSeconds() + " s");
        } catch (SQLException e) {
            if ("57014".equals(e.getSQLState())) // PostgreSQL: consulta cancelada por tiempo
                throw new R2RMLDataException("logical table of " + id + " exceeded the query timeout of "
                        + limits.queryTimeoutSeconds() + " s");
            throw new SqlFailure(e);
        }
    }

    private static Table read(Connection connection, ResultSet result, String id, Limits limits) throws SQLException {
        try (ResultSet rs = result) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String label = md.getColumnLabel(i);
                // Una tabla lógica no puede tener nombres de columna repetidos (R2RML §5; caso R2RMLTC0002h).
                if (columns.contains(label)) throw unsupported("duplicate column name " + label + " in logical table");
                columns.add(label);
            }
            List<Map<String, Value>> rows = new ArrayList<>();
            while (rs.next()) {
                if (rows.size() >= limits.maxRows())
                    throw new R2RMLDataException("logical table of " + id + " returned more than " + limits.maxRows()
                            + " rows (limit protys.erp.max-rows); nothing was materialized");
                Map<String, Value> row = new HashMap<>();
                for (int i = 1; i <= columns.size(); i++) {
                    int type = md.getColumnType(i);
                    Object raw;
                    if (type == Types.TIMESTAMP_WITH_TIMEZONE || "timestamptz".equalsIgnoreCase(md.getColumnTypeName(i))) {
                        // Con zona (pgjdbc informa timestamptz como TIMESTAMP): se conserva el instante, no el
                        // desplazamiento escrito; pgjdbc lo devuelve en UTC ('10:15:30-03' queda '13:15:30Z').
                        raw = rs.getObject(i, java.time.OffsetDateTime.class);
                        type = Types.TIMESTAMP_WITH_TIMEZONE;
                    } else {
                        raw = rs.getObject(i);
                    }
                    row.put(columns.get(i - 1), new Value(raw, type, raw == null ? null : naturalLexical(raw, type)));
                }
                rows.add(row);
            }
            return new Table(List.copyOf(columns), rows, fold(connection));
        }
    }

    /** Envuelve SQLException para propagarla desde computeIfAbsent sin perder el tipo. */
    static final class SqlFailure extends RuntimeException {
        SqlFailure(SQLException cause) { super(cause); }
    }

    // ------------------------------------------------------------------ generación de términos

    private static final class Generator {
        private final String base;
        private final Map<String, Node> blanks = new HashMap<>();

        Generator(String base) { this.base = base; }

        List<Node> graphs(List<TermMap> maps, Map<String, Value> row, Table table) {
            List<Node> graphs = new ArrayList<>();
            for (TermMap g : maps) {
                Node n = term(g, row, table, Position.GRAPH);
                if (n == null) continue;
                if (n.isURI() && n.getURI().equals(RR + "defaultGraph")) n = Quad.defaultGraphIRI;
                if (!graphs.contains(n)) graphs.add(n);
            }
            return graphs;
        }

        Node term(TermMap tm, Map<String, Value> row, Table table, Position position) {
            if (tm.kind() == Kind.CONSTANT) return tm.constant();
            String text;
            Value columnValue = null;
            if (tm.kind() == Kind.COLUMN) {
                columnValue = row.get(table.resolve(tm.value()));
                if (columnValue == null || columnValue.raw() == null) return null;
                text = columnValue.lexical();
            } else {
                text = expandTemplate(tm.value(), row, table, tm.termType() == TermType.IRI);
                if (text == null) return null;
            }
            return switch (tm.termType()) {
                case IRI -> iri(text);
                case BLANK -> blanks.computeIfAbsent(text, k -> NodeFactory.createBlankNode());
                case LITERAL -> literal(tm, text, columnValue);
            };
        }

        private Node iri(String text) {
            String resolved = text;
            try {
                if (!IRIs.check(text) || org.apache.jena.irix.IRIx.create(text).isRelative()) {
                    resolved = IRIs.resolve(base, text);
                }
                if (!IRIs.check(resolved)) throw new R2RMLDataException("invalid IRI generated");
            } catch (R2RMLDataException e) {
                throw e;
            } catch (Exception e) {
                throw new R2RMLDataException("invalid IRI generated");
            }
            return NodeFactory.createURI(resolved);
        }

        private Node literal(TermMap tm, String text, Value columnValue) {
            if (tm.language() != null) return NodeFactory.createLiteral(text, tm.language());
            if (tm.datatype() != null) {
                RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(tm.datatype());
                if (dt instanceof XSDDatatype xsd && !xsd.isValid(text)) {
                    throw new R2RMLDataException("value is not valid for datatype " + tm.datatype());
                }
                return NodeFactory.createLiteral(text, dt);
            }
            if (columnValue != null) {
                String natural = naturalDatatype(columnValue.sqlType());
                if (natural != null) {
                    return NodeFactory.createLiteral(text, TypeMapper.getInstance().getSafeTypeByName(natural));
                }
            }
            return NodeFactory.createLiteral(text);
        }
    }

    // ------------------------------------------------------------------ plantillas

    /** Columnas referenciadas por una plantilla; valida llaves y escapes. */
    static List<String> templateColumns(String template, String id) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = null;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= template.length()) throw unsupported("dangling escape in rr:template of " + id);
                if (current != null) current.append(template.charAt(i + 1));
                i++;
            } else if (ch == '{') {
                if (current != null) throw unsupported("nested braces in rr:template of " + id);
                current = new StringBuilder();
            } else if (ch == '}') {
                if (current == null || current.isEmpty()) throw unsupported("unbalanced braces in rr:template of " + id);
                columns.add(current.toString());
                current = null;
            } else if (current != null) {
                current.append(ch);
            }
        }
        if (current != null) throw unsupported("unbalanced braces in rr:template of " + id);
        return columns;
    }

    private static String expandTemplate(String template, Map<String, Value> row, Table table, boolean iriSafe) {
        StringBuilder out = new StringBuilder();
        StringBuilder column = null;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '\\') {
                (column != null ? column : out).append(template.charAt(++i));
            } else if (ch == '{') {
                column = new StringBuilder();
            } else if (ch == '}') {
                Value v = row.get(table.resolve(column.toString()));
                if (v == null || v.raw() == null) return null;
                out.append(iriSafe ? iriSafe(v.lexical()) : v.lexical());
                column = null;
            } else {
                (column != null ? column : out).append(ch);
            }
        }
        return out.toString();
    }

    /** Versión "IRI-safe" de R2RML §7.3: se codifica en porcentaje todo lo que no sea iunreserved. */
    static String iriSafe(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (isIUnreserved(cp)) {
                out.appendCodePoint(cp);
            } else {
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    out.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xFF));
                }
            }
        });
        return out.toString();
    }

    private static boolean isIUnreserved(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')
                || cp == '-' || cp == '.' || cp == '_' || cp == '~'
                || (cp >= 0xA0 && cp <= 0xD7FF) || (cp >= 0xF900 && cp <= 0xFDCF) || (cp >= 0xFDF0 && cp <= 0xFFEF)
                || (cp >= 0x10000 && cp <= 0xEFFFD && (cp & 0xFFFE) != 0xFFFE);
    }

    // ------------------------------------------------------------------ correspondencia natural SQL → XSD (R2RML §10.2)

    static String naturalDatatype(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.SMALLINT, Types.BIGINT, Types.TINYINT -> XSDDatatype.XSDinteger.getURI();
            case Types.DECIMAL, Types.NUMERIC -> XSDDatatype.XSDdecimal.getURI();
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> XSDDatatype.XSDdouble.getURI();
            case Types.BOOLEAN, Types.BIT -> XSDDatatype.XSDboolean.getURI();
            case Types.DATE -> XSDDatatype.XSDdate.getURI();
            case Types.TIME -> XSDDatatype.XSDtime.getURI();
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> XSDDatatype.XSDdateTime.getURI();
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> XSDDatatype.XSDhexBinary.getURI();
            default -> null;
        };
    }

    /** Forma léxica canónica del valor según su tipo SQL. */
    static String naturalLexical(Object raw, int sqlType) {
        return switch (sqlType) {
            case Types.DECIMAL, Types.NUMERIC -> {
                BigDecimal d = raw instanceof BigDecimal b ? b : new BigDecimal(raw.toString());
                d = d.stripTrailingZeros();
                yield d.scale() < 0 ? d.setScale(0).toPlainString() : d.toPlainString();
            }
            // Un REAL llega como Float: se parte de su representación decimal para no arrastrar
            // los dígitos espurios de ensancharlo a double (caso W3C R2RMLTC0016b).
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> raw instanceof Float f
                    ? canonicalDecimal(new BigDecimal(Float.toString(f)), f)
                    : canonicalDouble(((Number) raw).doubleValue());
            case Types.BOOLEAN, Types.BIT -> Boolean.toString(raw instanceof Boolean b ? b : !"0".equals(raw.toString()));
            case Types.DATE -> raw instanceof java.sql.Date d ? d.toLocalDate().toString() : raw.toString();
            case Types.TIME -> raw instanceof java.sql.Time t ? t.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME) : raw.toString();
            case Types.TIMESTAMP -> raw instanceof java.sql.Timestamp ts
                    ? ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : raw.toString().replace(' ', 'T');
            case Types.TIMESTAMP_WITH_TIMEZONE -> raw instanceof java.time.OffsetDateTime odt
                    ? odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : raw instanceof java.time.ZonedDateTime zdt ? zdt.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : raw.toString().replace(' ', 'T');
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    raw instanceof byte[] bytes ? HexFormat.of().withUpperCase().formatHex(bytes) : raw.toString();
            default -> raw.toString();
        };
    }

    /** Forma canónica de xsd:double: mantisa con un dígito entero y exponente, por ejemplo 8.025E1. */
    static String canonicalDouble(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "INF" : "-INF";
        return canonicalDecimal(new BigDecimal(Double.toString(v)), v);
    }

    private static String canonicalDecimal(BigDecimal value, double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "INF" : "-INF";
        if (v == 0) return (1 / v < 0) ? "-0.0E0" : "0.0E0";
        BigDecimal d = value.stripTrailingZeros();
        int exponent = d.precision() - d.scale() - 1;
        BigDecimal mantissa = d.movePointLeft(exponent);
        String m = mantissa.toPlainString();
        if (!m.contains(".")) m = m + ".0";
        return m + "E" + exponent;
    }

    // ------------------------------------------------------------------ utilidades

    private static Property rr(Model m, String local) { return m.createProperty(RR + local); }

    private static void checkVocabulary(Resource r) {
        if (r.isURIResource() && r.getURI().startsWith(RR) && !RR_TERMS.contains(r.getURI().substring(RR.length()))) {
            throw unsupported("unknown R2RML term rr:" + r.getURI().substring(RR.length()));
        }
    }

    private static RDFNode single(Model m, Resource s, String p, String id) {
        RDFNode n = optionalSingle(m, s, p, id);
        if (n == null) throw unsupported("rr:" + p + " is required in " + id);
        return n;
    }

    private static RDFNode optionalSingle(Model m, Resource s, String p, String id) {
        List<RDFNode> values = m.listObjectsOfProperty(s, rr(m, p)).toList();
        if (values.size() > 1) throw unsupported("rr:" + p + " appears more than once in " + id);
        return values.isEmpty() ? null : values.get(0);
    }

    private static String literal(RDFNode n, String what, String id) {
        if (!n.isLiteral()) throw unsupported(what + " must be a literal in " + id);
        Literal l = n.asLiteral();
        return l.getLexicalForm();
    }

    private static ProtysMappingException unsupported(String detail) {
        return new ProtysMappingException("UNSUPPORTED_MAPPING: " + detail);
    }
}
