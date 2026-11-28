package org.protys.ws.dto;

import lombok.*;

import java.util.List;

/**
 * Respuesta de GET /api/ontology/modules/{id}/properties: los árboles de propiedades de objeto y de
 * datos del grafo declarado del módulo (raíces con sus subpropiedades), la cantidad de propiedades
 * distintas de cada uno y la cantidad de propiedades de anotación, que no entran a los árboles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModulePropertiesDTO {

    private List<PropertyDTO> objectProperties;

    private List<PropertyDTO> dataProperties;

    private int objectPropertyCount;

    private int dataPropertyCount;

    private int annotationPropertyCount;
}
