package org.protys.ws.dto;

import org.protys.ws.model.ERPConnector;
import java.time.LocalDateTime;
import java.util.UUID;

/** Public connector configuration. Stored credentials are never response fields. */
public record ERPConnectorResponse(
        UUID id, String name, ERPConnector.ERPType type, String jdbcUrl, String username,
        String databaseName, String namedGraph, String r2rmlMappingPath,
        ERPConnector.ConnectorStatus status, LocalDateTime lastMaterializationAt,
        Long tripleCount, Long materializationTimeMs, Boolean active,
        LocalDateTime createdAt, LocalDateTime updatedAt, boolean hasPassword) {

    public static ERPConnectorResponse from(ERPConnector connector) {
        return new ERPConnectorResponse(connector.getId(), connector.getName(), connector.getType(),
                publicJdbcUrl(connector.getJdbcUrl()), connector.getUsername(), connector.getDatabaseName(),
                connector.getNamedGraph(), connector.getR2rmlMappingPath(), connector.getStatus(),
                connector.getLastMaterializationAt(), connector.getTripleCount(),
                connector.getMaterializationTimeMs(), connector.getActive(), connector.getCreatedAt(),
                connector.getUpdatedAt(), connector.getPassword() != null && !connector.getPassword().isBlank());
    }

    private static String publicJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        // Older configurations can contain credentials in JDBC parameters or user-info.
        return jdbcUrl.replaceAll("(?i)([?;&](?:password|sslpassword|pwd)=)[^;&]*", "$1[REDACTED]")
                .replaceAll("(//)[^/@]+@", "$1[REDACTED]@");
    }
}
