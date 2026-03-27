package ve.edu.uc.protys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ve.edu.uc.protys.dto.MaterializationResult;
import ve.edu.uc.protys.model.ERPConnector;
import ve.edu.uc.protys.service.ERPConnectorService;

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

    // Connector Management Endpoints

    @GetMapping("/connectors")
    @Operation(summary = "List all ERP connectors", description = "Retrieve all registered ERP connectors and their configurations")
    public ResponseEntity<List<ERPConnector>> getAllConnectors() {
        log.info("Fetching all ERP connectors");
        try {
            List<ERPConnector> connectors = erpConnectorService.getConnectors();
            log.debug("Retrieved {} ERP connectors", connectors.size());
            return ResponseEntity.ok(connectors);
        } catch (Exception e) {
            log.error("Error retrieving ERP connectors", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/connectors/{id}")
    @Operation(summary = "Get connector details", description = "Retrieve detailed information about a specific ERP connector")
    public ResponseEntity<ERPConnector> getConnectorById(@PathVariable UUID id) {
        log.info("Fetching ERP connector with id: {}", id);
        try {
            ERPConnector connector = erpConnectorService.getConnector(id);
            log.debug("Connector retrieved successfully");
            return ResponseEntity.ok(connector);
        } catch (Exception e) {
            log.error("Error retrieving connector", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/connectors")
    @Operation(summary = "Register new ERP connector", description = "Create and register a new ERP system connector with JDBC configuration")
    public ResponseEntity<ERPConnector> registerConnector(@RequestBody ERPConnector connector) {
        log.info("Registering new ERP connector");
        try {
            if (connector.getName() == null || connector.getName().trim().isEmpty()) {
                log.warn("Invalid connector configuration: missing name");
                return ResponseEntity.badRequest().build();
            }

            ERPConnector registeredConnector = erpConnectorService.registerConnector(connector);
            log.info("ERP connector registered successfully with id: {}", registeredConnector.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(registeredConnector);
        } catch (Exception e) {
            log.error("Error registering ERP connector", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/connectors/{id}")
    @Operation(summary = "Update connector configuration", description = "Update the configuration of an existing ERP connector")
    public ResponseEntity<ERPConnector> updateConnector(
            @PathVariable UUID id,
            @RequestBody ERPConnector connectorConfig) {
        log.info("Updating ERP connector: {}", id);
        try {
            ERPConnector updatedConnector = erpConnectorService.updateConnector(id, connectorConfig);
            log.info("Connector updated successfully");
            return ResponseEntity.ok(updatedConnector);
        } catch (Exception e) {
            log.error("Error updating connector", e);
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
            log.error("Error deleting connector", e);
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
            log.error("Error testing connection", e);
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
            log.error("Error introspecting schema", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Materialization Endpoint

    @PostMapping("/connectors/{id}/materialize")
    @Operation(summary = "Execute R2RML materialization", description = "Materialize RDF data from the ERP database using R2RML mapping rules")
    public ResponseEntity<MaterializationResult> materializeData(@PathVariable UUID id) {
        log.info("Starting R2RML materialization for connector: {}", id);
        try {
            MaterializationResult result = erpConnectorService.materialize(id);
            log.info("Materialization completed successfully. Created {} triples", result.getTripleCount());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("Error during materialization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
            log.error("Error retrieving materialization history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
