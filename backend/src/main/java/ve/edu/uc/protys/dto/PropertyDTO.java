package ve.edu.uc.protys.dto;

import lombok.*;

/**
 * DTO for ontology property information.
 * Represents both datatype properties and object properties with their constraints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDTO {

    private String uri;

    private String localName;

    private String label;

    private String domain;

    private String range;

    @Builder.Default
    private Boolean functional = false;

    /**
     * Get the human-readable label, falling back to localName if not available.
     */
    public String getDisplayName() {
        return label != null && !label.isBlank() ? label : localName;
    }

    /**
     * Determine if this is a datatype property (range is a datatype).
     */
    public boolean isDataProperty() {
        return range != null && (
                range.contains("xsd#") ||
                        range.contains("rdfs#Literal") ||
                        range.contains("string") ||
                        range.contains("int") ||
                        range.contains("boolean") ||
                        range.contains("date")
        );
    }
}
