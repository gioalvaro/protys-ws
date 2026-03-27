package ve.edu.uc.protys.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * DTO for ontology individual (instance) information.
 * Represents OWL individuals with their types, data properties, and object properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyIndividualDTO {

    private String uri;

    private String localName;

    private String label;

    /** Primary rdf:type URI */
    private String type;

    /** All rdf:type URIs */
    private List<String> types;

    /** All properties mapped as predicate URI → list of values */
    private Map<String, List<String>> properties;

    /** Data properties specifically */
    private Map<String, List<String>> dataPropertyValues;

    /** Object properties specifically */
    private Map<String, List<String>> objectPropertyValues;

    /**
     * Get the human-readable label, falling back to localName if not available.
     */
    public String getDisplayName() {
        return label != null && !label.isBlank() ? label : localName;
    }
}
