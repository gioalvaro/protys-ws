package org.protys.ws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Resultado de un alta, una modificación, una baja o un deshacer, con la validación que se ejecutó después. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualEditResult {
    /** CREATE, UPDATE, DELETE o UNDO. */
    private String operation;
    private String uri;
    private String moduleId;
    private String moduleName;
    /** Identificador para deshacer esta edición con POST /api/ontology/individuals/edits/{editId}/undo. */
    private String editId;
    private long writeTimeMs;
    private Validation validation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Validation {
        /** CONSISTENT, INCONSISTENT o NOT_EVALUATED (por ejemplo, fuera del perfil OWL 2 DL). */
        private String status;
        private Boolean consistent;
        /** Tiempo total de la validación medido por el servicio, en milisegundos. */
        private long timeMs;
        private boolean cacheHit;
        private String message;
        private List<String> violations;
    }
}
