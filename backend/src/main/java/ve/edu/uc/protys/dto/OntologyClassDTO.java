package ve.edu.uc.protys.dto;

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

    private Integer propertyCount;

    private List<OntologyClassDTO> subclasses;

    private List<String> superClassUris;

    private List<OntologyIndividualDTO> individuals;

    private List<PropertyDTO> dataProperties;

    private List<PropertyDTO> objectProperties;

    /**
     * Get the human-readable label, falling back to localName if not available.
     */
    public String getDisplayName() {
        return label != null && !label.isBlank() ? label : localName;
    }
}
