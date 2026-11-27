package org.protys.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.protys.ws.dto.IndividualEditResult;
import org.protys.ws.dto.IndividualRequest;
import org.protys.ws.dto.OntologyIndividualDTO;
import org.protys.ws.service.IndividualService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestión de individuos.
 *
 * <p>Forma única de pasar el IRI de un individuo: parámetro de consulta {@code uri}, con el IRI absoluto
 * codificado como URL (por ejemplo {@code ?uri=http%3A%2F%2Fexample.org%2Fx}). El IRI nunca va en la ruta.
 * Las ediciones devuelven el resultado de la validación de consistencia ejecutada después y un
 * {@code editId} para deshacerlas.
 */
@RestController
@RequestMapping("/api/ontology/individuals")
@Tag(name = "Individuals", description = "Create, read, update and delete individuals; the IRI travels in the uri query parameter")
@RequiredArgsConstructor
public class IndividualController {

    private final IndividualService individualService;

    @GetMapping(params = "classUri")
    @Operation(summary = "List individuals of a class", description = "Individuals whose rdf:type is exactly classUri, in every registered module")
    public List<OntologyIndividualDTO> list(@RequestParam String classUri) {
        return individualService.listByClass(classUri);
    }

    @GetMapping(params = "uri")
    @Operation(summary = "Get an individual", description = "404 if no registered module has the individual")
    public OntologyIndividualDTO get(@RequestParam String uri) {
        return individualService.get(uri);
    }

    @PostMapping
    @Operation(summary = "Create an individual", description = "Body {uri?, label?, properties?}. 400 for a malformed IRI or an undeclared class, 409 if the individual already exists. Runs the consistency validation afterwards")
    public ResponseEntity<IndividualEditResult> create(@RequestParam String classUri, @RequestBody(required = false) IndividualRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(individualService.create(classUri, body));
    }

    @PutMapping(params = "uri")
    @Operation(summary = "Update an individual", description = "Body {label?, properties?}: replaces the label and the listed properties; other triples are kept. Runs the consistency validation afterwards")
    public IndividualEditResult update(@RequestParam String uri, @RequestBody(required = false) IndividualRequest body) {
        return individualService.update(uri, body);
    }

    @DeleteMapping(params = "uri")
    @Operation(summary = "Delete an individual", description = "Removes the triples whose subject or object is the individual. Runs the consistency validation afterwards")
    public IndividualEditResult delete(@RequestParam String uri) {
        return individualService.delete(uri);
    }

    @PostMapping("/edits/{editId}/undo")
    @Operation(summary = "Undo an edit", description = "Restores the module graph as it was before the edit; 409 if the graph changed afterwards")
    public IndividualEditResult undo(@PathVariable String editId) {
        return individualService.undo(editId);
    }
}
