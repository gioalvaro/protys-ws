package org.protys.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.protys.ws.dto.StandardIncorporationResult;
import org.protys.ws.dto.StandardIncorporationResult.ExecutedRule;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.model.AlignmentRule;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.model.StandardIncorporationSession;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.OntologyModuleRepository;
import org.protys.ws.repository.StandardIncorporationSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Session-owned staging, with durable checkpoints around non-transactional Fuseki writes.
 * Repository operations commit separately: a failed graph write must not erase its owner.
 * Mutations are serialized within the single backend instance used by this prototype.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StandardIncorporationService {
    public static class IncorporationAlreadyCompletedException extends IllegalStateException {
        public IncorporationAlreadyCompletedException() {
            super("The session is already incorporated; cancellation cannot delete a permanent module");
        }
    }
    static final String TEMP_PREFIX = "http://w3id.org/protys/ontology/temp/wizard/";
    static final String PERMANENT_PREFIX = "http://w3id.org/protys/ontology/incorporated/";
    private final FusekiService fusekiService;
    private final ReasoningService reasoningService;
    private final OntologyModuleRepository ontologyModuleRepository;
    private final AlignmentRuleRepository alignmentRuleRepository;
    private final StandardIncorporationSessionRepository sessionRepository;

    public synchronized StandardIncorporationResult step1_upload(MultipartFile file, String standardName) {
        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setCurrentStep(1);
        long start = System.currentTimeMillis();
        try {
            if (standardName == null || standardName.isBlank() || standardName.trim().length() > 255)
                throw new ProtysMappingException("A standard name of 1 to 255 characters is required");
            Model model = ModelFactory.createDefaultModel();
            try (var input = file.getInputStream()) { RDFDataMgr.read(model, input, Lang.RDFXML); }
            if (model.isEmpty()) throw new ProtysMappingException("The uploaded ontology is empty");
            StandardIncorporationSession session = new StandardIncorporationSession();
            UUID id = UUID.randomUUID();
            session.setId(id);
            session.setStandardName(standardName.trim());
            session.setTemporaryGraph(TEMP_PREFIX + id);
            session.setPermanentGraph(PERMANENT_PREFIX + id);
            session.setStartedAt(LocalDateTime.now());
            session.setTripleCount(Math.toIntExact(model.size()));
            session.setPhase("UPLOADING");
            session = sessionRepository.saveAndFlush(session);
            result.setTempModuleId(id); // Retained on graph-write failure so the caller can cancel.
            result.setStandardName(session.getStandardName());
            result.setStartedAt(session.getStartedAt());
            fusekiService.putModel(session.getTemporaryGraph(), model);
            session.setPhase("UPLOADED");
            sessionRepository.saveAndFlush(session);
            result.setTripleCount(session.getTripleCount());
            result.setStatus("UPLOADED");
            result.setStep1_parsed(true);
            result.setStep1_message("Parsed ontology into an isolated incorporation session");
        } catch (Exception e) {
            log.error("Wizard upload failed", e);
            result.setStatus("NOT_EVALUATED");
            result.setStep1_parsed(false);
            result.setStep1_error(true);
            result.setStep1_message("Upload error: " + e.getMessage());
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    public synchronized StandardIncorporationResult step2_validateConsistency(UUID id) {
        StandardIncorporationResult result = result(id, 2);
        long start = System.currentTimeMillis();
        try {
            StandardIncorporationSession session = editable(id);
            session.setConsistencyVerified(false);
            session.setInferenceVerified(false);
            session.setExecutedRulesJson(null);
            session.setRuleDefinitionsSnapshot(null);
            session.setRuleDefinitionsSha256(null);
            session.setPhase("UPLOADED");
            session = sessionRepository.saveAndFlush(session);
            var validation = reasoningService.classify(combined(session));
            validationFields(result, validation);
            boolean passed = validation.isConsistent() && "CONSISTENT".equals(validation.getValidationStatus());
            session.setConsistencyVerified(passed);
            session.setPhase(passed ? "VALIDATED" : "UPLOADED");
            sessionRepository.saveAndFlush(session);
            result.setStep2_consistent("NOT_EVALUATED".equals(validation.getValidationStatus()) ? null : passed);
            result.setStep2_error(!passed);
            result.setStep2_conflictingModules(passed ? List.of() : List.of(validation.getValidationStatus()));
            result.setStep2_message(passed ? "OWL consistency verified for this session and permanent modules" : "Validation status: " + validation.getValidationStatus());
        } catch (Exception e) {
            result.setStatus("NOT_EVALUATED");
            result.setStep2_consistent(null);
            result.setStep2_error(true);
            result.setStep2_message("Validation error: " + e.getMessage());
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    /** A selection refers only to registered IDs; [] explicitly requests no SWRL rules. */
    public synchronized StandardIncorporationResult step3_defineAlignments(UUID id, List<AlignmentRule> requested) {
        StandardIncorporationResult result = result(id, 3);
        try {
            StandardIncorporationSession session = editable(id);
            // Invalidate the old receipt even when the replacement selection is rejected.
            session.setInferenceVerified(false);
            session.setSelectedRuleIds(null);
            session.setExecutedRulesJson(null);
            session.setRuleDefinitionsSnapshot(null);
            session.setRuleDefinitionsSha256(null);
            session = sessionRepository.saveAndFlush(session);
            if (!session.isConsistencyVerified()) throw new ProtysMappingException("A completed consistency validation is required");
            if (requested == null) throw new ProtysMappingException("An explicit rule selection is required");
            var ids = new LinkedHashSet<UUID>();
            for (AlignmentRule rule : requested) {
                if (rule == null || rule.getId() == null) throw new ProtysMappingException("Select registered rule IDs; new global rules cannot be created by a session");
                if (!ids.add(rule.getId())) throw new ProtysMappingException("Duplicate selected rule ID");
            }
            List<AlignmentRule> selected = resolveRules(new ArrayList<>(ids));
            session.setSelectedRuleIds(ids.stream().map(UUID::toString).collect(Collectors.joining(",")));
            session.setPhase("SELECTED");
            sessionRepository.saveAndFlush(session);
            result.setSelectedRuleIds(new ArrayList<>(ids));
            result.setStep3_rulesCount(selected.size());
            result.setStep3_rulesGenerated(0);
            result.setAlignmentRulesCreated(0);
            result.setStatus("SELECTED");
            result.setStep3_error(false);
            result.setStep3_message("Selected " + selected.size() + " registered rules for this session");
        } catch (Exception e) {
            result.setStatus("NOT_EVALUATED");
            result.setStep3_error(true);
            result.setStep3_message("Rule selection error: " + e.getMessage());
        }
        return result;
    }

    public synchronized StandardIncorporationResult step4_verifyInferences(UUID id) {
        StandardIncorporationResult result = result(id, 4);
        long start = System.currentTimeMillis();
        try {
            StandardIncorporationSession session = editable(id);
            session.setInferenceVerified(false);
            session.setExecutedRulesJson(null);
            session.setRuleDefinitionsSnapshot(null);
            session.setRuleDefinitionsSha256(null);
            session = sessionRepository.saveAndFlush(session);
            if (!session.isConsistencyVerified() || session.getSelectedRuleIds() == null)
                throw new ProtysMappingException("Consistency validation and an explicit rule selection are required");
            List<UUID> ids = selection(session);
            List<AlignmentRule> selected = resolveRules(ids);
            Model model = combined(session);
            Model definitions = selected.isEmpty() ? ModelFactory.createDefaultModel() : requiredGraph(AlignmentService.RULE_GRAPH);
            model.add(definitions);
            // Exact supplied RDF snapshot (including blank node IDs), not a canonical RDF hash.
            var snapshot = new java.io.ByteArrayOutputStream();
            RDFDataMgr.write(snapshot, definitions, Lang.NTRIPLES);
            String definitionsHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()));
            var validation = reasoningService.classify(model, selected.stream().map(StandardIncorporationService::executionName).toList());
            validationFields(result, validation);
            if (!validation.isConsistent() || !"CONSISTENT".equals(validation.getValidationStatus()))
                throw new ProtysMappingException(validation.getValidationStatus() + ": " + validation.getErrorMessage());
            if (validation.getImportedRuleCount() != selected.size())
                throw new ProtysMappingException("Executed rule count does not match the explicit selection");
            session.setInferredTriples(Math.toIntExact(validation.getInferredTripleCount()));
            List<ExecutedRule> executed = selected.stream().map(r -> new ExecutedRule(r.getId(), r.getName())).toList();
            session.setExecutedRulesJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(executed));
            session.setRuleDefinitionsSnapshot(snapshot.toString(java.nio.charset.StandardCharsets.UTF_8));
            session.setRuleDefinitionsSha256(definitionsHash);
            session.setInferenceVerified(true);
            session.setPhase("VERIFIED");
            sessionRepository.saveAndFlush(session);
            result.setSelectedRuleIds(ids);
            result.setExecutedRules(executed);
            result.setRuleDefinitionsSha256(definitionsHash);
            result.setStep4_verified(true);
            result.setStep4_error(false);
            result.setStep4_inferredTriples(session.getInferredTriples());
            result.setStep4_message("Verified " + selected.size() + " selected rules; inferred " + session.getInferredTriples() + " triples");
        } catch (Exception e) {
            if (result.getStatus() == null || "CONSISTENT".equals(result.getStatus())) result.setStatus("NOT_EVALUATED");
            result.setStep4_verified(false);
            result.setStep4_error(true);
            result.setStep4_message("Inference verification error: " + e.getMessage());
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    public synchronized StandardIncorporationResult completeIncorporation(UUID id) {
        StandardIncorporationResult result = result(id, 5);
        try {
            StandardIncorporationSession session = owned(id);
            if ("COMPLETED".equals(session.getPhase())) return completed(session);
            if ("CANCELLED".equals(session.getPhase()) || "CANCELLING".equals(session.getPhase()))
                throw new ProtysMappingException("The session has been cancelled or awaits cleanup");
            // Recover a DB commit whose response/checkpoint was lost. Never rewrite its graph.
            var existing = ontologyModuleRepository.findByNamedGraph(session.getPermanentGraph());
            if (existing.isPresent()) {
                if (!"COMPLETING".equals(session.getPhase()) || !id.equals(existing.get().getIncorporationSessionId()))
                    throw new ProtysMappingException("Destination is already registered and is not owned by this completion");
                session.setFinalModuleId(existing.get().getId());
                session.setPhase("COMPLETED");
                session.setCompletedAt(LocalDateTime.now());
                session = sessionRepository.saveAndFlush(session);
                return completed(session);
            }
            if (!session.isConsistencyVerified() || !session.isInferenceVerified())
                throw new ProtysMappingException("Consistency and inference verification must pass before completion");
            if ("COMPLETING".equals(session.getPhase())) {
                // A graph-only partial completion is safe to revalidate and retry.
                session.setPhase("VERIFIED");
                sessionRepository.saveAndFlush(session);
            }
            var verified = step4_verifyInferences(id);
            if (!Boolean.TRUE.equals(verified.getStep4_verified())) throw new ProtysMappingException(verified.getStep4_message());
            session = owned(id);
            Model source = requiredGraph(session.getTemporaryGraph());
            Model destination = optionalGraph(session.getPermanentGraph());
            // A nonempty graph is accepted only as an identical retry of this session's write.
            if (!destination.isEmpty() && (!session.isPermanentWriteStarted() || !destination.isIsomorphicWith(source)))
                throw new ProtysMappingException("Destination graph already contains different data; it will not be overwritten");
            session.setPhase("COMPLETING");
            session.setPermanentWriteStarted(true);
            session = sessionRepository.saveAndFlush(session);
            if (destination.isEmpty()) fusekiService.putModel(session.getPermanentGraph(), source);
            OntologyModule module = new OntologyModule();
            module.setName(session.getStandardName());
            module.setBaseUri(session.getPermanentGraph());
            module.setNamedGraph(session.getPermanentGraph());
            module.setIncorporationSessionId(id);
            module.setVersion("1.0");
            module.setStatus(OntologyModule.ModuleStatus.VALIDATED);
            module.setTripleCount((long) session.getTripleCount());
            module.setLoadedAt(LocalDateTime.now());
            module.setValidatedAt(LocalDateTime.now());
            OntologyModule saved = ontologyModuleRepository.saveAndFlush(module);
            session.setFinalModuleId(saved.getId());
            session.setPhase("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            session = sessionRepository.saveAndFlush(session);
            result = completed(session);
            result.setExecutedRules(verified.getExecutedRules());
            return result;
        } catch (Exception e) {
            log.error("Wizard completion failed; ownership checkpoint retained", e);
            result.setStatus("NOT_EVALUATED");
            result.setMessage("Completion error: " + e.getMessage());
            return result;
        }
    }

    /** No match is an idempotent no-op. A completed permanent module can never be cancelled. */
    public synchronized void cancelIncorporation(UUID id) {
        var found = sessionRepository.findById(id);
        if (found.isEmpty()) return;
        StandardIncorporationSession session = found.get();
        checkOwnership(session);
        if ("CANCELLED".equals(session.getPhase())) return;
        if ("COMPLETED".equals(session.getPhase()) || ontologyModuleRepository.findByNamedGraph(session.getPermanentGraph()).isPresent())
            throw new IncorporationAlreadyCompletedException();
        session.setPhase("CANCELLING");
        session.setInferenceVerified(false);
        session.setConsistencyVerified(false);
        session = sessionRepository.saveAndFlush(session);
        // Delete graph before marking success; lost responses are safely retried by graph ID.
        deleteOwnedGraph(session.getTemporaryGraph());
        if (session.isPermanentWriteStarted()) deleteOwnedGraph(session.getPermanentGraph());
        session.setPhase("CANCELLED");
        sessionRepository.saveAndFlush(session);
    }

    public synchronized StandardIncorporationSession getIncorporationState(UUID id) {
        return sessionRepository.findById(id).orElse(null);
    }

    private StandardIncorporationResult completed(StandardIncorporationSession session) {
        StandardIncorporationResult result = result(session.getId(), 5);
        result.setStatus("COMPLETED");
        result.setStandardName(session.getStandardName());
        result.setFinalModuleId(session.getFinalModuleId());
        result.setCompletedAt(session.getCompletedAt());
        result.setSelectedRuleIds(selection(session));
        result.setRuleDefinitionsSha256(session.getRuleDefinitionsSha256());
        try {
            result.setExecutedRules(new com.fasterxml.jackson.databind.ObjectMapper().readValue(session.getExecutedRulesJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ExecutedRule>>() {}));
        } catch (Exception e) {
            throw new ProtysMappingException("The persisted execution receipt is unavailable", e);
        }
        result.setInferencesGenerated(session.getInferredTriples());
        result.setAlignmentRulesCreated(0);
        result.setMessage("Successfully incorporated " + session.getStandardName());
        result.setCleanupPending(false);
        try { deleteOwnedGraph(session.getTemporaryGraph()); }
        catch (Exception cleanup) {
            result.setCleanupPending(true);
            result.setWarnings(List.of("Incorporation is complete; temporary graph cleanup will be retried on completion retry"));
        }
        return result;
    }

    private StandardIncorporationSession editable(UUID id) {
        StandardIncorporationSession session = owned(id);
        if (List.of("UPLOADING", "COMPLETING", "COMPLETED", "CANCELLING", "CANCELLED").contains(session.getPhase()))
            throw new ProtysMappingException("Session is not editable in phase " + session.getPhase());
        return session;
    }

    private StandardIncorporationSession owned(UUID id) {
        StandardIncorporationSession session = sessionRepository.findById(id).orElseThrow(() -> new ProtysMappingException("Incorporation session not found"));
        checkOwnership(session);
        return session;
    }

    private void checkOwnership(StandardIncorporationSession session) {
        if (!(TEMP_PREFIX + session.getId()).equals(session.getTemporaryGraph()) || !(PERMANENT_PREFIX + session.getId()).equals(session.getPermanentGraph()))
            throw new ProtysMappingException("Session graph ownership is invalid");
    }

    private Model combined(StandardIncorporationSession session) {
        Model combined = ModelFactory.createDefaultModel();
        for (OntologyModule module : ontologyModuleRepository.findAll()) {
            if (!AlignmentService.isPermanentModule(module)) continue;
            combined.add(AlignmentService.withoutRuleDefinitions(requiredGraph(module.getNamedGraph())));
        }
        combined.add(AlignmentService.withoutRuleDefinitions(requiredGraph(session.getTemporaryGraph())));
        return combined;
    }

    private Model requiredGraph(String graph) {
        Model model = fusekiService.getModel(graph);
        if (model.isEmpty()) throw new ProtysMappingException("Required graph is empty: " + graph);
        return model;
    }

    private Model optionalGraph(String graph) {
        try { return fusekiService.getModel(graph); }
        catch (RuntimeException failure) {
            if (isMissingGraph(failure)) return ModelFactory.createDefaultModel();
            throw failure;
        }
    }

    private void deleteOwnedGraph(String graph) {
        try { fusekiService.deleteGraph(graph); }
        catch (RuntimeException failure) {
            if (!isMissingGraph(failure)) throw failure;
        }
    }

    private boolean isMissingGraph(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.apache.jena.atlas.web.HttpException http && http.getStatusCode() == 404) return true;
            if (cause instanceof org.apache.jena.web.JenaHttpException http && http.getStatusCode() == 404) return true;
        }
        return false;
    }

    private List<UUID> selection(StandardIncorporationSession session) {
        if (session.getSelectedRuleIds() == null || session.getSelectedRuleIds().isEmpty()) return List.of();
        return java.util.Arrays.stream(session.getSelectedRuleIds().split(",")).map(UUID::fromString).toList();
    }

    private List<AlignmentRule> resolveRules(List<UUID> ids) {
        List<AlignmentRule> rules = new ArrayList<>();
        var executionNames = new LinkedHashSet<String>();
        for (UUID id : ids) {
            AlignmentRule rule = alignmentRuleRepository.findById(id).orElseThrow(() -> new ProtysMappingException("Registered rule not found: " + id));
            if (!executionNames.add(executionName(rule))) throw new ProtysMappingException("Selected IDs refer to the same executable rule");
            rules.add(rule);
        }
        return rules;
    }

    private static String executionName(AlignmentRule rule) {
        String name = rule.getRuleId();
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*")) throw new ProtysMappingException("Registered rule has no supported executable identifier");
        return name.replaceFirst("^(R[0-9]+)_.*$", "$1");
    }

    private StandardIncorporationResult result(UUID id, int step) {
        StandardIncorporationResult result = new StandardIncorporationResult();
        result.setTempModuleId(id);
        result.setCurrentStep(step);
        return result;
    }

    private void validationFields(StandardIncorporationResult result, ReasoningService.ClassificationResult validation) {
        result.setStatus(validation.getValidationStatus());
        result.setCleaningEvaluation(validation.getCleaningEvaluation());
        result.setContextEvaluation(validation.getContextEvaluation());
        result.setNumericEvaluation(validation.getNumericEvaluation());
        result.setOwlValidationStatus(validation.getOwlValidationStatus());
    }
}
