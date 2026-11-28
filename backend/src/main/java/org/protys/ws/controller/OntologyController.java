package org.protys.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.protys.ws.dto.OntologyClassDTO;
import org.protys.ws.dto.OntologyModuleDTO;
import org.protys.ws.service.OntologyService;

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
    public ResponseEntity<?> uploadModule(
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
                return ResponseEntity.badRequest().body(Map.of("message", "Unsupported file type; use .owl or .rdf (RDF/XML), .ttl (Turtle), .n3 or .nt"));
            }

            OntologyModuleDTO module = ontologyService.loadModule(file, name);
            log.info("Module uploaded successfully with id: {}", module.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(module);
        } catch (org.protys.ws.exception.ProtysValidationException invalid) {
            return ResponseEntity.badRequest().body(Map.of("message", invalid.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading ontology module", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/modules/{id}/classes")
    @Operation(summary = "Get class hierarchy", description = "Lista de clases raíz del módulo, cada una con sus subclases (campo subclasses). Lista vacía si el módulo no tiene clases.")
    public ResponseEntity<?> getClassHierarchy(@PathVariable UUID id) {
        log.info("Fetching class hierarchy for module: {}", id);
        try {
            return ResponseEntity.ok(ontologyService.getClassHierarchy(id));
        } catch (org.protys.ws.exception.ModuleNotFoundException missing) {
            return notFound(missing);
        } catch (Exception e) {
            log.error("Error retrieving class hierarchy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/modules/{id}/properties")
    @Operation(summary = "Get property hierarchy", description = "Árboles de propiedades de objeto y de datos del módulo (raíces con sus subpropiedades en el campo subproperties), leídos del grafo declarado sin inferencia, y la cantidad de propiedades de anotación, que no entran a los árboles.")
    public ResponseEntity<?> getPropertyHierarchy(@PathVariable UUID id) {
        log.info("Fetching property hierarchy for module: {}", id);
        try {
            return ResponseEntity.ok(ontologyService.getPropertyHierarchy(id));
        } catch (org.protys.ws.exception.ModuleNotFoundException missing) {
            return notFound(missing);
        } catch (Exception e) {
            log.error("Error retrieving property hierarchy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Los individuos se gestionan en IndividualController (/api/ontology/individuals, IRI en el parámetro uri).

    // Validation Endpoint

    @PostMapping("/modules/{id}/validate")
    @Operation(summary = "Validate ontology consistency", description = "Trigger ontology consistency validation using a reasoner")
    public ResponseEntity<?> validateModule(@PathVariable UUID id) {
        log.info("Validating module: {}", id);
        try {
            var validation=ontologyService.validateDetailed(id);
            Map<String,Object> result=new java.util.LinkedHashMap<>();result.put("moduleId",id.toString());result.put("status",validation.getValidationStatus());result.put("consistent",validation.getValidationStatus().equals("NOT_EVALUATED")?null:validation.isConsistent());result.put("cacheHit",validation.isCacheHit());result.put("reasoningTimeMs",validation.getReasoningTimeMs());result.put("error",validation.getErrorMessage());result.put("cleaningEvaluation",validation.getCleaningEvaluation());result.put("contextEvaluation",validation.getContextEvaluation());result.put("numericEvaluation",validation.getNumericEvaluation());result.put("owlValidationStatus",validation.getOwlValidationStatus());
            log.debug("Module validation completed");
            return ResponseEntity.ok(result);
        } catch (org.protys.ws.exception.ModuleNotFoundException missing) {
            return notFound(missing);
        } catch (Exception e) {
            log.error("Error validating module", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper Methods

    private static ResponseEntity<Map<String, Object>> notFound(org.protys.ws.exception.ModuleNotFoundException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing.getMessage()));
    }

    private boolean isValidOwlFile(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        return java.util.List.of(".owl", ".rdf", ".ttl", ".n3", ".nt").stream().anyMatch(name::endsWith);
    }
}
