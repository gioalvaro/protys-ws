package ve.edu.uc.protys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ve.edu.uc.protys.dto.OntologyClassDTO;
import ve.edu.uc.protys.dto.OntologyIndividualDTO;
import ve.edu.uc.protys.dto.OntologyModuleDTO;
import ve.edu.uc.protys.service.OntologyService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ontology")
@Tag(name = "Ontology", description = "Ontology module management and class/individual operations")
@RequiredArgsConstructor
public class OntologyController {

    private final OntologyService ontologyService;

    // Module Management Endpoints

    @GetMapping("/modules")
    @Operation(summary = "List all ontology modules", description = "Retrieve a list of all available ontology modules")
    public ResponseEntity<List<OntologyModuleDTO>> getAllModules() {
        log.info("Fetching all ontology modules");
        try {
            List<OntologyModuleDTO> modules = ontologyService.getAllModules();
            log.debug("Retrieved {} modules", modules.size());
            return ResponseEntity.ok(modules);
        } catch (Exception e) {
            log.error("Error retrieving ontology modules", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/modules/upload")
    @Operation(summary = "Upload OWL ontology file", description = "Upload a new OWL ontology file with a specified module name")
    public ResponseEntity<OntologyModuleDTO> uploadModule(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name) {
        log.info("Uploading ontology module: {}", name);
        try {
            if (file.isEmpty()) {
                log.warn("Empty file upload attempted");
                return ResponseEntity.badRequest().build();
            }
            if (!isValidOwlFile(file.getOriginalFilename())) {
                log.warn("Invalid file type: {}", file.getOriginalFilename());
                return ResponseEntity.badRequest().build();
            }

            OntologyModuleDTO module = ontologyService.loadModule(file, name);
            log.info("Module uploaded successfully with id: {}", module.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(module);
        } catch (Exception e) {
            log.error("Error uploading ontology module", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/modules/{id}/classes")
    @Operation(summary = "Get class hierarchy tree", description = "Retrieve the complete class hierarchy for a specific module")
    public ResponseEntity<OntologyClassDTO> getClassHierarchy(@PathVariable UUID id) {
        log.info("Fetching class hierarchy for module: {}", id);
        try {
            OntologyClassDTO hierarchy = ontologyService.getClassHierarchy(id);
            if (hierarchy == null) {
                log.warn("Class hierarchy not found for module: {}", id);
                return ResponseEntity.notFound().build();
            }
            log.debug("Class hierarchy retrieved");
            return ResponseEntity.ok(hierarchy);
        } catch (Exception e) {
            log.error("Error retrieving class hierarchy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Individual Management Endpoints

    @GetMapping("/individuals/{uri}")
    @Operation(summary = "Get individual details", description = "Retrieve detailed information about a specific individual with all properties")
    public ResponseEntity<OntologyIndividualDTO> getIndividual(@PathVariable String uri) {
        log.info("Fetching individual details: {}", uri);
        try {
            OntologyIndividualDTO individual = ontologyService.getIndividual(uri);
            log.debug("Individual retrieved successfully");
            return ResponseEntity.ok(individual);
        } catch (Exception e) {
            log.error("Error retrieving individual details", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/individuals")
    @Operation(summary = "Create new individual", description = "Create a new ontology individual with specified class and properties")
    public ResponseEntity<String> createIndividual(
            @RequestParam("classUri") String classUri,
            @RequestBody Map<String, String> properties) {
        log.info("Creating new individual for class: {}", classUri);
        try {
            String individualUri = ontologyService.createIndividual(classUri, properties);
            log.info("Individual created successfully with uri: {}", individualUri);
            return ResponseEntity.status(HttpStatus.CREATED).body(individualUri);
        } catch (Exception e) {
            log.error("Error creating individual", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/individuals/{uri}")
    @Operation(summary = "Update individual", description = "Update properties of an existing ontology individual")
    public ResponseEntity<Void> updateIndividual(
            @PathVariable String uri,
            @RequestBody Map<String, String> properties) {
        log.info("Updating individual: {}", uri);
        try {
            ontologyService.updateIndividual(uri, properties);
            log.info("Individual updated successfully");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error updating individual", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/individuals/{uri}")
    @Operation(summary = "Delete individual", description = "Remove an ontology individual")
    public ResponseEntity<Void> deleteIndividual(@PathVariable String uri) {
        log.info("Deleting individual: {}", uri);
        try {
            ontologyService.deleteIndividual(uri);
            log.info("Individual deleted successfully");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting individual", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Validation Endpoint

    @PostMapping("/modules/{id}/validate")
    @Operation(summary = "Validate ontology consistency", description = "Trigger ontology consistency validation using a reasoner")
    public ResponseEntity<Map<String, Object>> validateModule(@PathVariable UUID id) {
        log.info("Validating module: {}", id);
        try {
            boolean isConsistent = ontologyService.validateConsistency(id);
            Map<String, Object> result = Map.of(
                    "moduleId", id.toString(),
                    "consistent", isConsistent,
                    "timestamp", java.time.LocalDateTime.now()
            );
            log.debug("Module validation completed");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error validating module", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper Methods

    private boolean isValidOwlFile(String filename) {
        return filename != null && (filename.endsWith(".owl") || filename.endsWith(".rdf") || filename.endsWith(".ttl"));
    }
}
