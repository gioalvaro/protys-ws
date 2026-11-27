package org.protys.ws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Cuerpo del alta y de la modificación de un individuo.
 *
 * <p>{@code uri}: IRI absoluto del individuo (sólo en el alta; si falta, la plataforma genera uno bajo
 * {@code http://w3id.org/protys/instances/}). {@code label}: se guarda como {@code rdfs:label}.
 * {@code properties}: IRI absoluto de la propiedad → valor literal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualRequest {
    private String uri;
    private String label;
    private Map<String, String> properties;
}
