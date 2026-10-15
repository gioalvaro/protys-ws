package org.protys.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.protys.ws.dto.MaterializationResult;
import org.protys.ws.dto.ERPConnectorResponse;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.exception.ERPMaterializationException;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.service.ERPConnectorService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/erp")
@Tag(name = "ERP Connector", description = "ERP system integration and data materialization")
@RequiredArgsConstructor
public class ERPController {

    private final ERPConnectorService erpConnectorService;

    /** Malformed credentials must not be echoed by generic deserialization errors. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidRequestBody() {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ERP_REQUEST",
                "message", "Invalid ERP request body; check field names and value types"));
    }

    // Connector Management Endpoints

    @GetMapping("/connectors")
    @Operation(summary = "List all ERP connectors", description = "Retrieve all registered ERP connectors and their configurations")
    public ResponseEntity<List<ERPConnectorResponse>> getAllConnectors() {
        log.info("Fetching all ERP connectors");
        try {
            List<ERPConnector> connectors = erpConnectorService.getConnectors();
            log.debug("Retrieved {} ERP connectors", connectors.size());
            return ResponseEntity.ok(connectors.stream().map(ERPConnectorResponse::from).toList());
        } catch (Exception e) {
            log.warn("Error retrieving ERP connectors ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/connectors/{id}")
    @Operation(summary = "Get connector details", description = "Retrieve detailed information about a specific ERP connector")
    public ResponseEntity<ERPConnectorResponse> getConnectorById(@PathVariable UUID id) {
        log.info("Fetching ERP connector with id: {}", id);
        try {
            ERPConnector connector = erpConnectorService.getConnector(id);
            log.debug("Connector retrieved successfully");
            return ResponseEntity.ok(ERPConnectorResponse.from(connector));
        } catch (Exception e) {
            log.warn("Error retrieving connector ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/connectors")
    @Operation(summary = "Register new ERP connector", description = "Create and register a new ERP system connector with JDBC configuration")
    public ResponseEntity<ERPConnectorResponse> registerConnector(@RequestBody ERPConnector connector) {
        log.info("Registering new ERP connector");
        try {
            if (connector.getName() == null || connector.getName().trim().isEmpty()) {
                log.warn("Invalid connector configuration: missing name");
                return ResponseEntity.badRequest().build();
            }

            ERPConnector registeredConnector = erpConnectorService.registerConnector(connector);
            log.info("ERP connector registered successfully with id: {}", registeredConnector.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ERPConnectorResponse.from(registeredConnector));
        } catch (Exception e) {
            log.warn("Error registering ERP connector ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/connectors/{id}")
    @Operation(summary = "Update connector configuration", description = "Partially update a connector; omitted/null fields and blank passwords preserve stored values")
    public ResponseEntity<ERPConnectorResponse> updateConnector(
            @PathVariable UUID id,
            @RequestBody ERPConnector connectorConfig) {
        log.info("Updating ERP connector: {}", id);
        try {
            ERPConnector updatedConnector = erpConnectorService.updateConnector(id, connectorConfig);
            log.info("Connector updated successfully");
            return ResponseEntity.ok(ERPConnectorResponse.from(updatedConnector));
        } catch (Exception e) {
            log.warn("Error updating connector ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/connectors/{id}")
    @Operation(summary = "Remove ERP connector", description = "Delete a registered ERP connector and its configuration")
    public ResponseEntity<Void> deleteConnector(@PathVariable UUID id) {
        log.info("Deleting ERP connector: {}", id);
        try {
            erpConnectorService.deleteConnector(id);
            log.info("Connector deleted successfully");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.warn("Error deleting connector ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Connection Testing Endpoint

    @PostMapping("/connectors/{id}/test")
    @Operation(summary = "Test JDBC connection", description = "Test the JDBC connection to the ERP database")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID id) {
        log.info("Testing JDBC connection for connector: {}", id);
        try {
            boolean testResult = erpConnectorService.testConnection(id);
            Map<String, Object> response = Map.of(
                    "connectorId", id.toString(),
                    "connected", testResult,
                    "timestamp", java.time.LocalDateTime.now()
            );
            log.debug("Connection test completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error testing connection ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Schema Introspection Endpoint

    @PostMapping("/connectors/{id}/introspect")
    @Operation(summary = "Introspect database schema", description = "Retrieve database schema metadata (tables, columns, data types)")
    public ResponseEntity<Map<String, ?>> introspectSchema(@PathVariable UUID id) {
        log.info("Introspecting database schema for connector: {}", id);
        try {
            Map<String, ?> schemaMetadata = erpConnectorService.introspectSchema(id);
            log.debug("Schema introspection completed");
            return ResponseEntity.ok(schemaMetadata);
        } catch (Exception e) {
            log.warn("Error introspecting schema ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Materialization Endpoint

    @PostMapping("/connectors/{id}/materialize")
    @Operation(summary = "Execute legacy SQL materialization", description = "Limited XML SQL adapter, not general R2RML. Empty results preserve the existing graph and return EMPTY")
    public ResponseEntity<?> materializeData(@PathVariable UUID id) {
        log.info("Starting legacy SQL materialization for connector: {}", id);
        try {
            MaterializationResult result = erpConnectorService.materialize(id);
            if ("EMPTY".equals(result.getStatus())) return ResponseEntity.ok(result);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (ProtysMappingException e) {
            boolean unsupported = e.getMessage() != null && e.getMessage().startsWith("UNSUPPORTED_MAPPING");
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", unsupported ? "UNSUPPORTED_MAPPING" : "MAPPING_UNAVAILABLE",
                    "message", unsupported
                            ? "Only the limited legacy XML SQL mapping is supported; general R2RML is not supported. Existing graph preserved."
                            : "The configured mapping cannot be read. Existing graph preserved."));
        } catch (ERPMaterializationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "code", "MATERIALIZATION_FAILED", "message", e.getMessage(),
                    "operationId", e.getOperationId(), "compensationStatus", e.getCompensationStatus()));
        } catch (Exception e) {
            log.warn("Error during materialization ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "code", "MATERIALIZATION_FAILED", "message", "ERP materialization failed; no successful result was recorded"));
        }
    }

    // Materialization History Endpoint

    @GetMapping("/connectors/{id}/history")
    @Operation(summary = "Get materialization history", description = "Retrieve the history of materialization executions for a connector")
    public ResponseEntity<Map<String, Object>> getMaterializationHistory(@PathVariable UUID id) {
        log.info("Fetching materialization history for connector: {}", id);
        try {
            List<MaterializationResult> history = erpConnectorService.getMaterializationHistory(id);
            Map<String, Object> response = Map.of(
                    "connectorId", id.toString(),
                    "history", history,
                    "totalCount", history.size()
            );

            log.debug("Materialization history retrieved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Error retrieving materialization history ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
