package org.protys.ws.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for class hierarchy information.
 * Represents OWL classes with their properties, relationships, and individuals.
 * Supports recursive tree structure via subclasses field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyClassDTO {

    private String uri;

    private String localName;

    private String label;

    private String description;

    /** Cantidad de propiedades de objeto y de datos cuyo dominio declarado incluye la clase. */
    private Integer propertyCount;

    private List<OntologyClassDTO> subclasses;

    private List<String> superClassUris;

    private List<OntologyIndividualDTO> individuals;

    /**
     * Propiedades de datos cuyo dominio declarado incluye la clase: la clase es uno de sus rdfs:domain
     * o forma parte de una unión declarada como dominio. Cada una trae sus dominios completos.
     */
    private List<PropertyDTO> dataProperties;

    /** Igual que {@link #dataProperties}, para las propiedades de objeto. */
    private List<PropertyDTO> objectProperties;

    /**
     * Get the human-readable label, falling back to localName if not available.
     */
    public String getDisplayName() {
        return label != null && !label.isBlank() ? label : localName;
    }
}
