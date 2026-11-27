package org.protys.ws.service;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * Texto legible de una regla SWRL guardada en RDF, para mostrarla en la interfaz:
 * {@code Clase(?x) ^ propiedad(?x, ?y) -> OtraClase(?x)}. Antes se guardaba el identificador
 * del nodo en blanco del cuerpo, que no dice nada.
 */
public final class SwrlText {
    static final String SWRL = "http://www.w3.org/2003/11/swrl#";
    static final String SWRLB = "http://www.w3.org/2003/11/swrlb#";
    private static final int MAX_ATOMS = 500;

    private SwrlText() {}

    /** Cuerpo y cabeza de la regla; una parte ausente queda vacía (la columna no admite nulos). */
    public static String of(Resource rule) {
        Model model = rule.getModel();
        Resource body = rule.getPropertyResourceValue(model.createProperty(SWRL + "body"));
        Resource head = rule.getPropertyResourceValue(model.createProperty(SWRL + "head"));
        return (body == null ? "" : atoms(body)) + " -> " + (head == null ? "" : atoms(head));
    }

    /** rdfs:label o, si no hay, rdfs:comment de la regla. */
    public static String description(Resource rule) {
        for (var property : List.of(RDFS.label, RDFS.comment)) {
            Statement statement = rule.getProperty(property);
            if (statement != null && statement.getObject().isLiteral()) return statement.getString();
        }
        return null;
    }

    private static String atoms(Resource list) {
        List<String> atoms = new ArrayList<>();
        for (RDFNode atom : members(list)) {
            if (atom.isResource()) atoms.add(atom(atom.asResource()));
        }
        return String.join(" ^ ", atoms);
    }

    private static List<RDFNode> members(Resource list) {
        List<RDFNode> members = new ArrayList<>();
        Resource current = list;
        while (current != null && !RDF.nil.equals(current) && members.size() < MAX_ATOMS) {
            Statement first = current.getProperty(RDF.first);
            if (first == null) break;
            members.add(first.getObject());
            current = current.getPropertyResourceValue(RDF.rest);
        }
        return members;
    }

    private static String atom(Resource atom) {
        Model model = atom.getModel();
        RDFNode predicate = null;
        for (String name : List.of("classPredicate", "propertyPredicate", "builtin", "dataRange")) {
            Statement statement = atom.getProperty(model.createProperty(SWRL + name));
            if (statement != null) { predicate = statement.getObject(); break; }
        }
        List<String> arguments = new ArrayList<>();
        for (String name : List.of("argument1", "argument2")) {
            Statement statement = atom.getProperty(model.createProperty(SWRL + name));
            if (statement != null) arguments.add(term(statement.getObject()));
        }
        Resource builtinArguments = atom.getPropertyResourceValue(model.createProperty(SWRL + "arguments"));
        if (builtinArguments != null) members(builtinArguments).forEach(node -> arguments.add(term(node)));
        String name;
        if (predicate != null) {
            name = term(predicate);
        } else if (atom.hasProperty(RDF.type, model.createResource(SWRL + "SameIndividualAtom"))) {
            name = "sameAs";
        } else if (atom.hasProperty(RDF.type, model.createResource(SWRL + "DifferentIndividualsAtom"))) {
            name = "differentFrom";
        } else {
            name = "?";
        }
        return name + "(" + String.join(", ", arguments) + ")";
    }

    private static String term(RDFNode node) {
        if (node.isLiteral()) return "\"" + node.asLiteral().getLexicalForm() + "\"";
        Resource resource = node.asResource();
        if (resource.isAnon()) return "_";
        String uri = resource.getURI();
        String local = resource.getLocalName();
        if (local == null || local.isEmpty()) local = uri;
        if (resource.hasProperty(RDF.type, resource.getModel().createResource(SWRL + "Variable"))) return "?" + local;
        return uri.startsWith(SWRLB) ? "swrlb:" + local : local;
    }
}
