package ve.edu.uc.protys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ve.edu.uc.protys.model.AlignmentRule;
import ve.edu.uc.protys.model.OntologyModule;
import ve.edu.uc.protys.dto.StandardIncorporationResult;
import ve.edu.uc.protys.exception.ProtysMappingException;
import ve.edu.uc.protys.repository.AlignmentRuleRepository;
import ve.edu.uc.protys.repository.OntologyModuleRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 4-step wizard service for incorporating new manufacturing standards.
 * Guides users through validation, alignment, and verification process.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StandardIncorporationService {

    private final OntologyService ontologyService;
    private final AlignmentService alignmentService;
    private final FusekiService fusekiService;
    private final OntologyModuleRepository ontologyModuleRepository;
    private final AlignmentRuleRepository alignmentRuleRepository;

    // Temporary storage for multi-step process
    private final Map<UUID, StandardIncorporationState> incorporationStates = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Step 1: Upload and parse a new OWL standard.
     *
     * @param owlFile      the OWL file containing the standard
     * @param standardName the name of the standard
     * @return StandardIncorporationResult with step 1 status
     * @throws ProtysMappingException if parsing fails
     */
    @Transactional
    public StandardIncorporationResult step1_upload(MultipartFile owlFile, String standardName) {
        log.info("STEP 1: Uploading and parsing new standard: {}", standardName);
        long startTime = System.currentTimeMillis();

        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setStandardName(standardName);
        result.setCurrentStep(1);
        result.setStartedAt(LocalDateTime.now());

        try {
            // Parse OWL file
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, owlFile.getInputStream(), Lang.RDFXML);

            log.debug("Parsed OWL file with {} triples", model.size());

            // Create temporary module entity for this standard
            OntologyModule tempModule = new OntologyModule();
            tempModule.setId(UUID.randomUUID());
            tempModule.setName(standardName + "-temp");
            tempModule.setNamedGraph("http://protys.ontology/temp/" + standardName);
            tempModule.setTripleCount((int) model.size());
            tempModule.setCreatedAt(LocalDateTime.now());

            // Store temporary module
            OntologyModule savedTemp = ontologyModuleRepository.save(tempModule);
            fusekiService.loadModel(tempModule.getNamedGraph(), owlFile.getInputStream());

            // Store state for subsequent steps
            StandardIncorporationState state = new StandardIncorporationState();
            state.setTempModuleId(savedTemp.getId());
            state.setStandardName(standardName);
            state.setTripleCount((int) model.size());
            incorporationStates.put(savedTemp.getId(), state);

            // Prepare result
            long duration = System.currentTimeMillis() - startTime;
            result.setTempModuleId(savedTemp.getId());
            result.setTripleCount((int) model.size());
            result.setExecutionTimeMs(duration);
            result.setStep1_parsed(true);
            result.setStep1_message(String.format("Successfully parsed OWL file with %d triples", model.size()));

            log.info("STEP 1 completed in {}ms: {} triples", duration, model.size());
            return result;

        } catch (IOException e) {
            log.error("STEP 1 failed: File read error", e);
            result.setStep1_message("File read error: " + e.getMessage());
            result.setStep1_error(true);
            throw new ProtysMappingException("Failed to upload OWL file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("STEP 1 failed: Parse error", e);
            result.setStep1_message("Parse error: " + e.getMessage());
            result.setStep1_error(true);
            throw new ProtysMappingException("Failed to parse OWL: " + e.getMessage(), e);
        }
    }

    /**
     * Step 2: Validate consistency with existing modules.
     *
     * @param tempModuleId the temporary module ID
     * @return StandardIncorporationResult with step 2 status
     */
    @Transactional(readOnly = true)
    public StandardIncorporationResult step2_validateConsistency(UUID tempModuleId) {
        log.info("STEP 2: Validating consistency for temp module: {}", tempModuleId);
        long startTime = System.currentTimeMillis();

        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setTempModuleId(tempModuleId);
        result.setCurrentStep(2);

        try {
            StandardIncorporationState state = incorporationStates.get(tempModuleId);
            if (state == null) {
                throw new ProtysMappingException("Incorporation state not found");
            }

            // Load temp module
            OntologyModule tempModule = ontologyModuleRepository.findById(tempModuleId)
                    .orElseThrow(() -> new ProtysMappingException("Temp module not found"));

            // Check consistency with existing modules
            boolean consistent = true;
            List<String> conflictingModules = new ArrayList<>();

            List<OntologyModule> existingModules = ontologyModuleRepository.findAll().stream()
                    .filter(m -> !m.getId().equals(tempModuleId))
                    .toList();

            for (OntologyModule existing : existingModules) {
                Model tempModel = fusekiService.getModel(tempModule.getNamedGraph());
                Model existingModel = fusekiService.getModel(existing.getNamedGraph());

                // Create OntModel for reasoning
                OntModel tempOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, tempModel);
                OntModel existingOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, existingModel);

                // Check for class name conflicts
                var tempClasses = tempOntModel.listNamedClasses().toList();
                var existingClasses = existingOntModel.listNamedClasses().toList();

                for (var tempClass : tempClasses) {
                    for (var existingClass : existingClasses) {
                        if (tempClass.getLocalName().equals(existingClass.getLocalName())
                                && !tempClass.getURI().equals(existingClass.getURI())) {
                            consistent = false;
                            conflictingModules.add(existing.getName());
                            log.warn("Class name conflict: {} in {}", tempClass.getLocalName(), existing.getName());
                        }
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            result.setStep2_consistent(consistent);
            result.setStep2_conflictingModules(conflictingModules);
            result.setStep2_message(consistent ?
                    "No conflicts detected with existing modules" :
                    "Conflicts detected with modules: " + String.join(", ", conflictingModules));
            result.setExecutionTimeMs(duration);

            if (!consistent) {
                state.setConsistencyErrors(conflictingModules);
                result.setStep2_error(true);
            }

            log.info("STEP 2 completed in {}ms: consistent={}, conflicts={}",
                    duration, consistent, conflictingModules.size());

            return result;

        } catch (Exception e) {
            log.error("STEP 2 failed", e);
            result.setStep2_message("Validation error: " + e.getMessage());
            result.setStep2_error(true);
            return result;
        }
    }

    /**
     * Step 3: Define alignment rules between new and existing standards.
     *
     * @param tempModuleId the temporary module ID
     * @param rules        list of alignment rules to apply
     * @return StandardIncorporationResult with step 3 status
     */
    @Transactional
    public StandardIncorporationResult step3_defineAlignments(UUID tempModuleId, List<AlignmentRule> rules) {
        log.info("STEP 3: Defining {} alignment rules for temp module: {}", rules.size(), tempModuleId);
        long startTime = System.currentTimeMillis();

        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setTempModuleId(tempModuleId);
        result.setCurrentStep(3);

        try {
            StandardIncorporationState state = incorporationStates.get(tempModuleId);
            if (state == null) {
                throw new ProtysMappingException("Incorporation state not found");
            }

            // Save alignment rules
            List<AlignmentRule> savedRules = new ArrayList<>();
            for (AlignmentRule rule : rules) {
                rule.setId(UUID.randomUUID());
                rule.setCreatedAt(LocalDateTime.now());
                rule.setUpdatedAt(LocalDateTime.now());

                AlignmentRule saved = alignmentRuleRepository.save(rule);
                savedRules.add(saved);

                log.debug("Saved alignment rule: {}", rule.getName());
            }

            state.setAlignmentRules(savedRules);

            long duration = System.currentTimeMillis() - startTime;
            result.setStep3_rulesCount(savedRules.size());
            result.setStep3_message(String.format("Created %d alignment rules", savedRules.size()));
            result.setExecutionTimeMs(duration);

            log.info("STEP 3 completed in {}ms: {} rules created", duration, savedRules.size());
            return result;

        } catch (Exception e) {
            log.error("STEP 3 failed", e);
            result.setStep3_message("Alignment definition error: " + e.getMessage());
            result.setStep3_error(true);
            return result;
        }
    }

    /**
     * Step 4: Verify inferences by running reasoning over combined graph.
     *
     * @param tempModuleId the temporary module ID
     * @return StandardIncorporationResult with step 4 status
     */
    @Transactional
    public StandardIncorporationResult step4_verifyInferences(UUID tempModuleId) {
        log.info("STEP 4: Verifying inferences for temp module: {}", tempModuleId);
        long startTime = System.currentTimeMillis();

        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setTempModuleId(tempModuleId);
        result.setCurrentStep(4);

        try {
            StandardIncorporationState state = incorporationStates.get(tempModuleId);
            if (state == null) {
                throw new ProtysMappingException("Incorporation state not found");
            }

            // Load temp module
            OntologyModule tempModule = ontologyModuleRepository.findById(tempModuleId)
                    .orElseThrow(() -> new ProtysMappingException("Temp module not found"));

            // Get all ontology modules
            List<OntologyModule> allModules = ontologyModuleRepository.findAll();
            Model combinedModel = ModelFactory.createDefaultModel();

            for (OntologyModule module : allModules) {
                Model model = fusekiService.getModel(module.getNamedGraph());
                combinedModel.add(model);
            }

            // Run reasoning
            OntModel reasonedModel = ModelFactory.createOntologyModel(
                    OntModelSpec.OWL_MEM_MICRO_RULE_INF, combinedModel);

            long inferenceSize = reasonedModel.getDeductionsModel().size();

            long duration = System.currentTimeMillis() - startTime;
            result.setStep4_inferredTriples((int) inferenceSize);
            result.setStep4_message(String.format("Reasoning produced %d inferred triples", inferenceSize));
            result.setExecutionTimeMs(duration);

            state.setInferredTriples((int) inferenceSize);

            log.info("STEP 4 completed in {}ms: {} inferred triples", duration, inferenceSize);
            return result;

        } catch (Exception e) {
            log.error("STEP 4 failed", e);
            result.setStep4_message("Inference verification error: " + e.getMessage());
            result.setStep4_error(true);
            return result;
        }
    }

    /**
     * Final step: Complete incorporation by moving temp module to permanent and cleaning up.
     *
     * @param tempModuleId the temporary module ID
     * @return StandardIncorporationResult with final status
     */
    @Transactional
    public StandardIncorporationResult completeIncorporation(UUID tempModuleId) {
        log.info("COMPLETING incorporation for temp module: {}", tempModuleId);
        long startTime = System.currentTimeMillis();

        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setTempModuleId(tempModuleId);
        result.setCurrentStep(5);

        try {
            StandardIncorporationState state = incorporationStates.get(tempModuleId);
            if (state == null) {
                throw new ProtysMappingException("Incorporation state not found");
            }

            // Load temp module
            OntologyModule tempModule = ontologyModuleRepository.findById(tempModuleId)
                    .orElseThrow(() -> new ProtysMappingException("Temp module not found"));

            // Create permanent module
            OntologyModule permanentModule = new OntologyModule();
            permanentModule.setId(UUID.randomUUID());
            permanentModule.setName(state.getStandardName());
            permanentModule.setNamedGraph("http://protys.ontology/" + state.getStandardName());
            permanentModule.setTripleCount(state.getTripleCount());
            permanentModule.setCreatedAt(LocalDateTime.now());
            permanentModule.setUpdatedAt(LocalDateTime.now());

            // Copy data from temp to permanent graph
            Model tempModel = fusekiService.getModel(tempModule.getNamedGraph());
            fusekiService.putModel(permanentModule.getNamedGraph(), tempModel);

            // Save permanent module
            OntologyModule saved = ontologyModuleRepository.save(permanentModule);

            // Delete temp module and graph
            ontologyModuleRepository.delete(tempModule);
            fusekiService.deleteGraph(tempModule.getNamedGraph());

            // Clean up state
            incorporationStates.remove(tempModuleId);

            long duration = System.currentTimeMillis() - startTime;
            result.setFinalModuleId(saved.getId());
            result.setCompletedAt(LocalDateTime.now());
            result.setExecutionTimeMs(duration);
            result.setStatus("COMPLETED");
            result.setMessage(String.format("Successfully incorporated %s standard", state.getStandardName()));

            log.info("Incorporation COMPLETED in {}ms: final module ID {}", duration, saved.getId());
            return result;

        } catch (Exception e) {
            log.error("Incorporation completion failed", e);
            result.setStatus("FAILED");
            result.setMessage("Completion error: " + e.getMessage());
            return result;
        }
    }

    /**
     * Gets the current state of an in-progress incorporation.
     *
     * @param tempModuleId the temporary module ID
     * @return StandardIncorporationState
     */
    @Transactional(readOnly = true)
    public StandardIncorporationState getIncorporationState(UUID tempModuleId) {
        log.debug("Fetching incorporation state: {}", tempModuleId);
        return incorporationStates.get(tempModuleId);
    }

    /**
     * Cancels an in-progress incorporation and cleans up.
     *
     * @param tempModuleId the temporary module ID
     */
    @Transactional
    public void cancelIncorporation(UUID tempModuleId) {
        log.info("Cancelling incorporation: {}", tempModuleId);

        try {
            OntologyModule tempModule = ontologyModuleRepository.findById(tempModuleId).orElse(null);
            if (tempModule != null) {
                // Delete alignment rules
                List<AlignmentRule> rules = alignmentRuleRepository.findAll().stream()
                        .filter(r -> r.getCreatedAt().isAfter(LocalDateTime.now().minusHours(1)))
                        .toList();
                alignmentRuleRepository.deleteAll(rules);

                // Delete module
                ontologyModuleRepository.delete(tempModule);
                fusekiService.deleteGraph(tempModule.getNamedGraph());
            }

            // Clean up state
            incorporationStates.remove(tempModuleId);
            log.info("Incorporation cancelled and cleaned up");

        } catch (Exception e) {
            log.error("Failed to cancel incorporation", e);
        }
    }

    /**
     * Internal state holder for multi-step incorporation process.
     */
    public static class StandardIncorporationState {
        private UUID tempModuleId;
        private String standardName;
        private int tripleCount;
        private List<String> consistencyErrors;
        private List<AlignmentRule> alignmentRules;
        private int inferredTriples;

        // Getters and setters
        public UUID getTempModuleId() { return tempModuleId; }
        public void setTempModuleId(UUID tempModuleId) { this.tempModuleId = tempModuleId; }

        public String getStandardName() { return standardName; }
        public void setStandardName(String standardName) { this.standardName = standardName; }

        public int getTripleCount() { return tripleCount; }
        public void setTripleCount(int tripleCount) { this.tripleCount = tripleCount; }

        public List<String> getConsistencyErrors() { return consistencyErrors; }
        public void setConsistencyErrors(List<String> consistencyErrors) { this.consistencyErrors = consistencyErrors; }

        public List<AlignmentRule> getAlignmentRules() { return alignmentRules; }
        public void setAlignmentRules(List<AlignmentRule> alignmentRules) { this.alignmentRules = alignmentRules; }

        public int getInferredTriples() { return inferredTriples; }
        public void setInferredTriples(int inferredTriples) { this.inferredTriples = inferredTriples; }
    }
}
