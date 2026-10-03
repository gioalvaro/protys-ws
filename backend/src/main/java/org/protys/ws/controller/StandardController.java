package org.protys.ws.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.protys.ws.dto.StandardIncorporationResult;
import org.protys.ws.service.StandardIncorporationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Standard incorporation entry point.
 *
 * This controller exposes the {@code POST /api/standards} endpoint documented
 * in Section 5.3 of the doctoral thesis ("Incorporación de nuevos estándares"):
 * it initiates the standard-incorporation pipeline by delegating to the first
 * step of the multi-step wizard ({@link StandardIncorporationService#step1_upload}).
 * The remaining pipeline steps (consistency validation, alignment-rule
 * definition, inference verification and final commit) are driven through the
 * wizard endpoints under {@code /api/wizard/*} using the wizard session id
 * (tempModuleId) returned by this endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/standards")
@Tag(name = "Standard Incorporation", description = "Entry point for the standard incorporation pipeline (thesis Section 5.3)")
@RequiredArgsConstructor
public class StandardController {

    private final StandardIncorporationService standardIncorporationService;

    /**
     * Initiates the standard incorporation pipeline.
     * Equivalent to Step 1 of the wizard (see Section 5.3 of the thesis);
     * the response contains the wizard session id ({@code tempModuleId})
     * used to drive the subsequent pipeline steps.
     *
     * @param file         the OWL/RDF/TTL file containing the standard ontology
     * @param standardName the name of the standard being incorporated
     * @return StandardIncorporationResult including the wizard session id
     */
    @PostMapping
    @Operation(summary = "Start standard incorporation pipeline",
            description = "Uploads a standard ontology and initiates the incorporation pipeline "
                    + "(thesis Section 5.3). Returns the wizard session id (tempModuleId) used by "
                    + "the /api/wizard/* endpoints for the remaining steps.")
    public ResponseEntity<StandardIncorporationResult> startIncorporation(
            @RequestParam("file") MultipartFile file,
            @RequestParam("standardName") String standardName) {
        log.info("POST /api/standards: starting incorporation pipeline for standard '{}'", standardName);
        try {
            if (file.isEmpty()) {
                log.warn("Empty file upload attempted");
                return ResponseEntity.badRequest().build();
            }

            if (!isValidOwlFile(file.getOriginalFilename())) {
                log.warn("Invalid file type: {}", file.getOriginalFilename());
                return ResponseEntity.badRequest().build();
            }

            StandardIncorporationResult result = standardIncorporationService.step1_upload(file, standardName);
            UUID sessionId = result.getTempModuleId();

            log.info("Incorporation pipeline started, wizard session id: {}", sessionId);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("Error starting standard incorporation pipeline", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isValidOwlFile(String filename) {
        return filename != null && (filename.endsWith(".owl") ||
                filename.endsWith(".rdf") ||
                filename.endsWith(".ttl"));
    }
}
