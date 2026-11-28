package org.protys.ws.dto;

import lombok.*;

import java.util.List;

/**
 * Propiedad de objeto o de datos tal como está declarada en el grafo de un módulo (sin inferencia).
 *
 * En el árbol de GET /api/ontology/modules/{id}/properties cada nodo trae sus subpropiedades en
 * {@code subproperties}. En las listas de {@link OntologyClassDTO} la propiedad va sin subpropiedades
 * ({@code subproperties} es null) y con sus dominios completos, aunque la clase entre por una unión.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDTO {

    public static final String OBJECT = "OBJECT";
    public static final String DATA = "DATA";

    private String uri;

    private String localName;

    private String label;

    private String comment;

    /** {@link #OBJECT} (owl:ObjectProperty) o {@link #DATA} (owl:DatatypeProperty). */
    private String type;

    /** Cada rdfs:domain declarado; si hay varios, van todos (no son alternativas). */
    private List<DomainRangeDTO> domains;

    /** Cada rdfs:range declarado; si hay varios, van todos. */
    private List<DomainRangeDTO> ranges;

    /** Propiedades declaradas como inversas con owl:inverseOf, en cualquiera de los dos sentidos. */
    private List<String> inverseOf;

    /** FUNCTIONAL, INVERSE_FUNCTIONAL, TRANSITIVE o SYMMETRIC, según los tipos declarados. */
    private List<String> characteristics;

    /** Superpropiedades nombradas (rdfs:subPropertyOf), del módulo o de otros. */
    private List<String> superPropertyUris;

    private List<PropertyDTO> subproperties;
}
