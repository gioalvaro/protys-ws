package org.protys.ws.dto;

import lombok.*;

import java.util.List;

/**
 * Un dominio o un rango declarado. Si es nombrado, trae {@code uri}. Si es anónimo, {@code uri} es
 * null, {@code operator} dice cómo se forma (unionOf, intersectionOf u other) y {@code members} trae
 * las clases (o tipos de datos) nombradas que lo componen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainRangeDTO {

    private String uri;

    private String operator;

    private List<String> members;
}
