package ve.edu.uc.protys.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for ontology module information.
 * Transfers module metadata and associated ontology classes to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyModuleDTO {

    private UUID id;

    private String name;

    private String description;

    private String baseUri;

    private String namedGraph;

    private Integer classCount;

    private Integer individualCount;

    private Long tripleCount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<OntologyClassDTO> classes;
}
