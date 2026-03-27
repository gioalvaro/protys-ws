package ve.edu.uc.protys.dto;

import lombok.*;

/**
 * DTO for database column metadata from ERP schema introspection.
 * Used by ERPConnectorService.introspectSchema() which returns
 * Map<String, List<SchemaMetadata>> (table name → columns).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaMetadata {

    private String name;

    private String type;

    private Boolean nullable;

    private Boolean primaryKey;

    private String foreignKey;

    private String comment;
}
