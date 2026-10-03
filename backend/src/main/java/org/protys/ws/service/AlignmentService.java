package org.protys.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.protys.ws.model.AlignmentRule;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.dto.InferenceStats;
import org.protys.ws.exception.ProtysFusekiException;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.OntologyModuleRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alignment and reasoning service.
 * Handles alignment rule management and inference execution.
 * Reasoning itself (HermiT integration, caching, timeouts) is delegated
 * to {@link ReasoningService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlignmentService {

    private final FusekiService fusekiService;
    private final AlignmentRuleRepository alignmentRuleRepository;
    private final OntologyModuleRepository ontologyModuleRepository;
    private final ReasoningService reasoningService;

    /**
     * Loads alignment rules (SWRL rules) from an OWL file.
     *
     * @param rulesFile the MultipartFile containing SWRL rules
     * @return List of loaded AlignmentRule entities
     * @throws ProtysMappingException if parsing fails
     */
    @Transactional
    public List<AlignmentRule> loadAlignmentRules(MultipartFile rulesFile) {
        log.info("Loading alignment rules from file");

        try {
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, rulesFile.getInputStream(), Lang.RDFXML);

            List<AlignmentRule> rules = new ArrayList<>();

            // Find all SWRL Imp resources (SWRL rules)
            StmtIterator iter = model.listStatements(null,
                    model.createProperty("http://www.w3.org/2003/11/swrl#body"),
                    (org.apache.jena.rdf.model.RDFNode) null);

            while (iter.hasNext()) {
                Statement stmt = iter.nextStatement();
                Resource ruleResource = stmt.getSubject();

                AlignmentRule rule = alignmentRuleRepository.findByRuleId(ruleResource.getLocalName()).orElseGet(AlignmentRule::new);
                rule.setRuleId(ruleResource.getLocalName());
                rule.setRuleType(AlignmentRule.RuleType.INFERENCE);
                rule.setName(ruleResource.getLocalName());
                rule.setSwrlExpression(stmt.getObject().toString());
                rule.setActive(true);
                rule.setCreatedAt(LocalDateTime.now());
                rule.setUpdatedAt(LocalDateTime.now());

                rules.add(rule);
                log.debug("Parsed alignment rule: {}", rule.getName());
            }

            // Preserve the executable RDF lists, variable declarations and rule heads.
            fusekiService.putModel("http://w3id.org/protys/ontology/active-rule-definitions", model);

            // Save all rules
            List<AlignmentRule> saved = alignmentRuleRepository.saveAll(rules);
            log.info("Loaded {} alignment rules", saved.size());
            return saved;

        } catch (IOException e) {
            log.error("Failed to read alignment rules file", e);
            throw new ProtysMappingException("Failed to load rules file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to load alignment rules", e);
            throw new ProtysMappingException("Failed to load alignment rules: " + e.getMessage(), e);
        }
    }

    /**
     * Executes reasoning over the combined knowledge graph.
     * Applies all active alignment rules and materializes inferences.
     *
     * @return InferenceStats with results
     * @throws ProtysFusekiException if reasoning fails
     */
    @Transactional
    public InferenceStats executeReasoning() {
        log.info("Starting reasoning execution");
        long startTime = System.currentTimeMillis();

        InferenceStats stats = new InferenceStats();
        stats.setStartedAt(LocalDateTime.now());

        try {
            // Load all ontology modules into a combined model
            List<OntologyModule> modules = ontologyModuleRepository.findAll();
            if(modules.isEmpty())throw new ProtysFusekiException("NOT_EVALUATED: no registered ontology modules");
            Model combinedModel = ModelFactory.createDefaultModel();

            long inputTriples = 0;
            for (OntologyModule module : modules) {
                Model moduleModel = fusekiService.getModel(module.getNamedGraph());
                if(moduleModel.isEmpty())throw new ProtysFusekiException("NOT_EVALUATED: required module graph is empty: "+module.getName());
                combinedModel.add(withoutRuleDefinitions(moduleModel));
                inputTriples += moduleModel.size();
            }

            combinedModel.add(fusekiService.getModel("http://w3id.org/protys/ontology/active-rule-definitions"));
            inputTriples = combinedModel.size();
            stats.setInputTripleCount((int) inputTriples);
            log.debug("Combined model contains {} triples from {} modules",
                    inputTriples, modules.size());

            // Delegate reasoning to the ReasoningService (HermiT-backed,
            // cached by ontology content hash, timeout-bounded)
            ReasoningService.ClassificationResult classification =
                    reasoningService.classify(combinedModel, getActiveRules().stream().map(AlignmentRule::getName).toList());
            stats.setValidationStatus(classification.getValidationStatus());
            stats.setCleaningEvaluation(classification.getCleaningEvaluation());
            stats.setContextEvaluation(classification.getContextEvaluation());
            stats.setNumericEvaluation(classification.getNumericEvaluation());
            stats.setOwlValidationStatus(classification.getOwlValidationStatus());
            if (!classification.isConsistent()) throw new ProtysFusekiException(classification.getValidationStatus()+": "+classification.getErrorMessage());
            Model inferredModel = classification.getDeductions();
            long inferredTriples = classification.getInferredTripleCount();

            stats.setInferredTripleCount((int) inferredTriples);
            stats.setValidationStatus(classification.getValidationStatus());
            stats.setCacheHit(classification.isCacheHit());
            stats.setReasoningTimeMs(classification.getReasoningTimeMs());
            stats.setActiveRules(classification.getImportedRuleCount());
            log.info("Reasoning produced {} new inferred triples", inferredTriples);

            // Store inferred graph
            String inferenceGraphUri = "http://w3id.org/protys/ontology/inference-results";
            fusekiService.putModel(inferenceGraphUri, inferredModel);

            // Count new classes and individuals inferred
            int newClasses = 0;
            int newIndividuals = 0;

            List<OntClass> originalClasses = ModelFactory.createOntologyModel(
                    OntModelSpec.OWL_MEM, combinedModel)
                    .listNamedClasses().toList();

            newClasses = (int) inferredModel.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, org.apache.jena.vocabulary.OWL.Class).toList().stream().filter(r -> !combinedModel.contains(r, org.apache.jena.vocabulary.RDF.type, org.apache.jena.vocabulary.OWL.Class)).count();

            stats.setNewClassCount(newClasses);
            stats.setNewIndividualCount(newIndividuals);

            long duration = System.currentTimeMillis() - startTime;
            stats.setExecutionTimeMs(duration);
            stats.setCompletedAt(LocalDateTime.now());
            stats.setStatus("SUCCESS");

            log.info("Reasoning completed in {}ms: {} inferred triples, {} new classes",
                    duration, inferredTriples, newClasses);

            return stats;

        } catch (Exception e) {
            log.error("Reasoning execution failed", e);
            try {fusekiService.putModel("http://w3id.org/protys/ontology/inference-results",ModelFactory.createDefaultModel());}
            catch(Exception cleanup){log.error("Failed to invalidate earlier inferred results",cleanup);e.addSuppressed(cleanup);}
            stats.setStatus("FAILED");
            if(stats.getValidationStatus()==null)stats.setValidationStatus("NOT_EVALUATED");
            stats.setErrorMessage(e.getMessage());
            stats.setCompletedAt(LocalDateTime.now());
            return stats;
        }
    }

    /**
     * Retrieves all active alignment rules.
     *
     * @return List of active AlignmentRule entities
     */
    @Transactional(readOnly = true)
    public List<AlignmentRule> getActiveRules() {
        log.debug("Fetching active alignment rules");

        List<AlignmentRule> rules = alignmentRuleRepository.findAll().stream()
                .filter(AlignmentRule::getActive)
                .collect(Collectors.toList());

        log.debug("Found {} active rules", rules.size());
        return rules;
    }

    /**
     * Toggles a rule's active status.
     *
     * @param ruleId the AlignmentRule ID
     * @param active the new active status
     * @return updated AlignmentRule
     */
    @Transactional
    public AlignmentRule toggleRule(UUID ruleId, boolean active) {
        log.info("Toggling rule {} to active={}", ruleId, active);

        AlignmentRule rule = alignmentRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ProtysFusekiException("Rule not found: " + ruleId));

        rule.setActive(active);
        rule.setUpdatedAt(LocalDateTime.now());

        AlignmentRule updated = alignmentRuleRepository.save(rule);
        log.info("Rule {} toggled", ruleId);
        return updated;
    }

    /**
     * Retrieves comprehensive inference statistics.
     *
     * @return InferenceStats with current state
     */
    @Transactional(readOnly = true)
    public InferenceStats getInferenceStats() {
        log.debug("Computing inference statistics");

        InferenceStats stats = new InferenceStats();

        try {
            // Load all ontologies
            List<OntologyModule> modules = ontologyModuleRepository.findAll();
            Model combinedModel = ModelFactory.createDefaultModel();

            for (OntologyModule module : modules) {
                Model moduleModel = fusekiService.getModel(module.getNamedGraph());
                combinedModel.add(moduleModel);
            }

            stats.setInputTripleCount((int) combinedModel.size());

            // Try to fetch inference graph
            try {
                Model inferenceModel = fusekiService.getModel("http://w3id.org/protys/ontology/inference-results");
                stats.setInferredTripleCount((int) inferenceModel.size());
            } catch (Exception e) {
                log.debug("Inference graph not found yet");
                stats.setInferredTripleCount(0);
            }

            stats.setActiveRules(getActiveRules().size());
            stats.setStatus("READY");

            log.debug("Inference stats: input={}, inferred={}, activeRules={}",
                    stats.getInputTripleCount(),
                    stats.getInferredTripleCount(),
                    stats.getActiveRules());

            return stats;

        } catch (Exception e) {
            log.error("Failed to compute inference statistics", e);
            stats.setStatus("ERROR");
            stats.setErrorMessage(e.getMessage());
            return stats;
        }
    }

    /**
     * Validates a single alignment rule for consistency.
     *
     * @param ruleId the AlignmentRule ID
     * @return true if rule is valid, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean validateAlignment(UUID ruleId) {
        log.info("Validating alignment rule: {}", ruleId);

        try {
            AlignmentRule rule = alignmentRuleRepository.findById(ruleId)
                    .orElseThrow(() -> new ProtysFusekiException("Rule not found: " + ruleId));

            Model combined = ModelFactory.createDefaultModel();
            for (OntologyModule module : ontologyModuleRepository.findAll()) combined.add(withoutRuleDefinitions(fusekiService.getModel(module.getNamedGraph())));
            combined.add(fusekiService.getModel("http://w3id.org/protys/ontology/active-rule-definitions"));
            var validation=reasoningService.classify(combined,List.of(rule.getName()));
            return validation.isConsistent() && validation.getImportedRuleCount()==1;

        } catch (Exception e) {
            log.error("Rule validation failed: {}", ruleId, e);
            return false;
        }
    }

    /** Executable definitions have one authoritative graph; copied RDF lists must not coexist. */
    static Model withoutRuleDefinitions(Model source) {
        Model model=ModelFactory.createDefaultModel().add(source);
        var swrlImp=model.createResource("http://www.w3.org/2003/11/swrl#Imp");
        var queue=new java.util.ArrayDeque<Resource>(model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type,swrlImp).toList());
        var seen=new java.util.HashSet<Resource>();
        while(!queue.isEmpty()){
            Resource subject=queue.remove();if(!seen.add(subject))continue;
            for(Statement statement:model.listStatements(subject,null,(org.apache.jena.rdf.model.RDFNode)null).toList())
                if(statement.getObject().isAnon())queue.add(statement.getObject().asResource());
        }
        for(Resource subject:seen)model.removeAll(subject,null,(org.apache.jena.rdf.model.RDFNode)null);
        return model;
    }

    /**
     * Gets alignment rule details.
     *
     * @param ruleId the AlignmentRule ID
     * @return the AlignmentRule
     */
    @Transactional(readOnly = true)
    public AlignmentRule getRule(UUID ruleId) {
        log.debug("Fetching alignment rule: {}", ruleId);

        return alignmentRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ProtysFusekiException("Rule not found: " + ruleId));
    }

    /**
     * Gets all alignment rules.
     *
     * @return List of all AlignmentRule entities
     */
    @Transactional(readOnly = true)
    public List<AlignmentRule> getAllRules() {
        log.debug("Fetching all alignment rules");
        return alignmentRuleRepository.findAll();
    }

    /**
     * Deletes an alignment rule.
     *
     * @param ruleId the AlignmentRule ID
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        log.info("Deleting alignment rule: {}", ruleId);
        alignmentRuleRepository.deleteById(ruleId);
        log.info("Rule deleted: {}", ruleId);
    }

    /**
     * Updates an alignment rule.
     *
     * @param ruleId the AlignmentRule ID
     * @param updated the updated AlignmentRule
     * @return updated AlignmentRule
     */
    @Transactional
    public AlignmentRule updateRule(UUID ruleId, AlignmentRule updated) {
        log.info("Updating alignment rule: {}", ruleId);

        AlignmentRule rule = alignmentRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ProtysFusekiException("Rule not found: " + ruleId));

        rule.setName(updated.getName());
        rule.setSwrlExpression(updated.getSwrlExpression());
        rule.setActive(updated.getActive());
        rule.setUpdatedAt(LocalDateTime.now());

        AlignmentRule saved = alignmentRuleRepository.save(rule);
        log.info("Rule updated: {}", ruleId);
        return saved;
    }
}
