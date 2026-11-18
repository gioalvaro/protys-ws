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
            Model model = parse(file);
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

    /**
     * Datos de instancia opcionales de la sesión (por ejemplo, los registros del piloto), en un grafo
     * temporal propio. Reemplaza los datos anteriores de la misma sesión e invalida la validación previa:
     * los pasos 2 y 4 razonan con el estándar más estos datos.
     */
    public synchronized StandardIncorporationResult step1_uploadData(UUID id, MultipartFile file) {
        StandardIncorporationResult result = result(id, 1);
        try {
            StandardIncorporationSession session = editable(id);
            Model data = parse(file);
            if (data.isEmpty()) throw new ProtysMappingException("El archivo de datos está vacío");
            session.setConsistencyVerified(false);
            session.setInferenceVerified(false);
            session.setExecutedRulesJson(null);
            session.setRuleDefinitionsSnapshot(null);
            session.setRuleDefinitionsSha256(null);
            session.setPhase("UPLOADED");
            session = sessionRepository.saveAndFlush(session);
            fusekiService.putModel(dataGraph(session.getTemporaryGraph()), data);
            session.setDataTripleCount(Math.toIntExact(data.size()));
            session = sessionRepository.saveAndFlush(session);
            result.setStandardName(session.getStandardName());
            result.setTripleCount(session.getTripleCount());
            result.setDataTripleCount(session.getDataTripleCount());
            result.setStatus("UPLOADED");
            result.setStep1_parsed(true);
            result.setStep1_message("Datos de instancia cargados en la sesión: " + data.size() + " triples");
        } catch (Exception e) {
            log.error("Wizard data upload failed", e);
            result.setStatus("NOT_EVALUATED");
            result.setStep1_parsed(false);
            result.setStep1_error(true);
            result.setStep1_message("Data upload error: " + e.getMessage());
        }
        return result;
    }

    /** Grafo de datos de instancia asociado a un grafo de sesión (temporal o permanente). */
    static String dataGraph(String sessionGraph) { return sessionGraph + "/data"; }

    /** Lee RDF eligiendo el formato por la extensión del archivo; sin extensión conocida, RDF/XML. */
    static Model parse(MultipartFile file) throws java.io.IOException {
        Lang lang = file.getOriginalFilename() == null ? null : org.apache.jena.riot.RDFLanguages.filenameToLang(file.getOriginalFilename());
        Model model = ModelFactory.createDefaultModel();
        try (var input = file.getInputStream()) { RDFDataMgr.read(model, input, lang == null ? Lang.RDFXML : lang); }
        return model;
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

    /**
     * Reglas SWRL nuevas que trae la sesión (por ejemplo, las reglas de vínculo de un estándar). Se
     * guardan en un grafo temporal propio; el paso 4 razona con ellas y al completar se registran,
     * se suman al grafo de definiciones y quedan activas. No pueden usar identificadores ya registrados.
     */
    public synchronized StandardIncorporationResult step3_uploadRules(UUID id, MultipartFile file) {
        StandardIncorporationResult result = result(id, 3);
        try {
            StandardIncorporationSession session = editable(id);
            session.setInferenceVerified(false);
            session.setExecutedRulesJson(null);
            session.setRuleDefinitionsSnapshot(null);
            session.setRuleDefinitionsSha256(null);
            if ("VERIFIED".equals(session.getPhase())) session.setPhase("SELECTED");
            session = sessionRepository.saveAndFlush(session);
            Model rules = parse(file);
            var imp = rules.createResource(SWRL_IMP);
            var names = new java.util.TreeSet<String>();
            for (var rule : rules.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, imp).toList()) {
                if (rule.isAnon()) throw new ProtysMappingException("Cada regla SWRL necesita un IRI");
                String name = rule.getLocalName();
                if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]*")) throw new ProtysMappingException("Identificador de regla no ejecutable: " + rule.getURI());
                if (alignmentRuleRepository.findByRuleId(name).isPresent()) throw new ProtysMappingException("La regla " + name + " ya está registrada; selecciónela en lugar de cargarla");
                if (!names.add(name)) throw new ProtysMappingException("Regla repetida en el archivo: " + name);
            }
            if (names.isEmpty()) throw new ProtysMappingException("El archivo no contiene reglas SWRL (swrl:Imp)");
            fusekiService.putModel(rulesGraph(session.getTemporaryGraph()), rules);
            session.setSessionRuleIds(String.join(",", names));
            sessionRepository.saveAndFlush(session);
            result.setSessionRuleIds(new ArrayList<>(names));
            result.setStatus(session.getPhase());
            result.setStep3_error(false);
            result.setStep3_message("Reglas de la sesión cargadas: " + String.join(", ", names));
        } catch (Exception e) {
            result.setStatus("NOT_EVALUATED");
            result.setStep3_error(true);
            result.setStep3_message("Session rules error: " + e.getMessage());
        }
        return result;
    }

    private static final String SWRL_IMP = "http://www.w3.org/2003/11/swrl#Imp";

    /** Grafo temporal con las reglas SWRL nuevas de la sesión. */
    static String rulesGraph(String temporaryGraph) { return temporaryGraph + "/rules"; }

    private List<String> sessionRules(StandardIncorporationSession session) {
        if (session.getSessionRuleIds() == null || session.getSessionRuleIds().isEmpty()) return List.of();
        return List.of(session.getSessionRuleIds().split(","));
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
            List<String> extra = sessionRules(session);
            // Se combinan sin duplicar: en un reintento de completar, las reglas de la sesión ya pueden
            // estar sumadas (con la misma definición) al grafo de definiciones.
            if (!extra.isEmpty()) definitions = AlignmentService.mergeRuleDefinitions(definitions, requiredGraph(rulesGraph(session.getTemporaryGraph())));
            List<String> enabled = new ArrayList<>(selected.stream().map(StandardIncorporationService::executionName).toList());
            for (String name : extra) {
                if (enabled.contains(name)) throw new ProtysMappingException("La regla " + name + " está a la vez seleccionada y cargada en la sesión");
                enabled.add(name);
            }
            model.add(definitions);
            // Exact supplied RDF snapshot (including blank node IDs), not a canonical RDF hash.
            var snapshot = new java.io.ByteArrayOutputStream();
            RDFDataMgr.write(snapshot, definitions, Lang.NTRIPLES);
            String definitionsHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()));
            var validation = reasoningService.classify(model, enabled);
            validationFields(result, validation);
            if (!validation.isConsistent() || !"CONSISTENT".equals(validation.getValidationStatus()))
                throw new ProtysMappingException(validation.getValidationStatus() + ": " + validation.getErrorMessage());
            if (validation.getImportedRuleCount() != enabled.size())
                throw new ProtysMappingException("Executed rule count does not match the explicit selection");
            session.setInferredTriples(Math.toIntExact(validation.getInferredTripleCount()));
            List<ExecutedRule> executed = new ArrayList<>(selected.stream().map(r -> new ExecutedRule(r.getId(), r.getName())).toList());
            // Las reglas de la sesión todavía no tienen registro: su id se completa al incorporarlas.
            for (String name : extra) executed.add(new ExecutedRule(alignmentRuleRepository.findByRuleId(name).map(AlignmentRule::getId).orElse(null), name));
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
            result.setSessionRuleIds(extra);
            result.setStep4_message("Verified " + selected.size() + " selected rules and " + extra.size() + " session rules; inferred " + session.getInferredTriples() + " triples");
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
            boolean withData = session.getDataTripleCount() != null;
            String dataTarget = dataGraph(session.getPermanentGraph());
            Model dataSource = withData ? requiredGraph(dataGraph(session.getTemporaryGraph())) : null;
            Model dataDestination = withData ? optionalGraph(dataTarget) : ModelFactory.createDefaultModel();
            if (!dataDestination.isEmpty() && (!session.isPermanentWriteStarted() || !dataDestination.isIsomorphicWith(dataSource)))
                throw new ProtysMappingException("Data destination graph already contains different data; it will not be overwritten");
            session.setPhase("COMPLETING");
            session.setPermanentWriteStarted(true);
            session = sessionRepository.saveAndFlush(session);
            if (destination.isEmpty()) fusekiService.putModel(session.getPermanentGraph(), source);
            if (withData && dataDestination.isEmpty()) fusekiService.putModel(dataTarget, dataSource);
            session = activateVerifiedRules(session, verified.getExecutedRules());
            // Los datos son un módulo aparte (incorporation_session_id es único y lo lleva el estándar);
            // se registran antes, así un reintento reconoce el estándar como la marca de completado.
            if (withData && ontologyModuleRepository.findByNamedGraph(dataTarget).isEmpty()) {
                OntologyModule data = new OntologyModule();
                data.setName(session.getStandardName() + " — datos de instancia");
                data.setBaseUri(dataTarget);
                data.setNamedGraph(dataTarget);
                data.setVersion("1.0");
                data.setStatus(OntologyModule.ModuleStatus.VALIDATED);
                data.setTripleCount((long) session.getDataTripleCount());
                data.setLoadedAt(LocalDateTime.now());
                data.setValidatedAt(LocalDateTime.now());
                ontologyModuleRepository.saveAndFlush(data);
            }
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
            return completed(session);
        } catch (Exception e) {
            log.error("Wizard completion failed; ownership checkpoint retained", e);
            result.setStatus("NOT_EVALUATED");
            result.setMessage("Completion error: " + e.getMessage());
            return result;
        }
    }

    /** Lo que el completado cambió fuera de la sesión, para poder deshacerlo al cancelar. */
    public static class RuleChanges {
        public LinkedHashSet<String> created = new LinkedHashSet<>();
        public LinkedHashSet<String> reactivated = new LinkedHashSet<>();
        public LinkedHashSet<String> defined = new LinkedHashSet<>();
        /** Triples sin nodos en blanco, fuera de las definiciones, que no estaban en el grafo de reglas (N-Triples). */
        public LinkedHashSet<String> addedTriples = new LinkedHashSet<>();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    static RuleChanges ruleChanges(StandardIncorporationSession session) {
        try {
            return session.getRuleChangesJson() == null ? new RuleChanges() : JSON.readValue(session.getRuleChangesJson(), RuleChanges.class);
        } catch (Exception e) {
            throw new ProtysMappingException("The persisted rule changes are unreadable", e);
        }
    }

    private static String nTriple(org.apache.jena.rdf.model.Statement statement) {
        Model one = ModelFactory.createDefaultModel().add(statement);
        var out = new java.io.StringWriter();
        RDFDataMgr.write(out, one, Lang.NTRIPLES);
        return out.toString().trim();
    }

    /** Registradas e inactivas entre las verificadas: el completado las activa para toda la plataforma. */
    private List<String> inactiveRegistered(List<ExecutedRule> executed) {
        List<String> names = new ArrayList<>();
        for (ExecutedRule run : executed) {
            if (run.getId() == null) continue;
            alignmentRuleRepository.findById(run.getId()).filter(r -> !Boolean.TRUE.equals(r.getActive()))
                    .ifPresent(r -> names.add(r.getRuleId()));
        }
        return names;
    }

    /**
     * Al completar: suma al grafo de definiciones las reglas nuevas de la sesión (sin duplicar si es un
     * reintento), las registra y deja activas todas las reglas verificadas en el paso 4. Va antes de
     * registrar el módulo del estándar, que es la marca de completado; todo es idempotente. Antes de
     * tocar nada guarda en la sesión qué va a cambiar (sumado a lo de intentos anteriores), para que
     * cancelar un completado interrumpido lo deshaga.
     */
    private StandardIncorporationSession activateVerifiedRules(StandardIncorporationSession session, List<ExecutedRule> executed) throws Exception {
        List<String> extra = sessionRules(session);
        Model definitions = extra.isEmpty() ? ModelFactory.createDefaultModel() : requiredGraph(rulesGraph(session.getTemporaryGraph()));
        // Sin reglas de sesión el grafo global de reglas no se toca ni se lee.
        Model current = extra.isEmpty() ? ModelFactory.createDefaultModel() : fusekiService.getModelOrEmpty(AlignmentService.RULE_GRAPH);
        Model merged = extra.isEmpty() ? current : AlignmentService.mergeRuleDefinitions(current, definitions);

        RuleChanges changes = ruleChanges(session);
        var imp = current.createResource(SWRL_IMP);
        for (String name : extra) {
            var existing = alignmentRuleRepository.findByRuleId(name);
            if (existing.isEmpty()) changes.created.add(name);
            else if (!Boolean.TRUE.equals(existing.get().getActive())) changes.reactivated.add(name);
            if (!current.contains(current.createResource(definitionIri(definitions, name)), org.apache.jena.vocabulary.RDF.type, imp))
                changes.defined.add(name);
        }
        changes.reactivated.addAll(inactiveRegistered(executed));
        for (var statement : AlignmentService.withoutRuleDefinitions(definitions).listStatements().toList()) {
            boolean ground = !statement.getSubject().isAnon() && !statement.getObject().isAnon();
            if (ground && !current.contains(statement)) changes.addedTriples.add(nTriple(statement));
        }
        session.setRuleChangesJson(JSON.writeValueAsString(changes));
        session = sessionRepository.saveAndFlush(session);

        if (!extra.isEmpty()) {
            fusekiService.putModel(AlignmentService.RULE_GRAPH, merged);
            for (String name : extra) {
                AlignmentRule rule = alignmentRuleRepository.findByRuleId(name).orElseGet(AlignmentRule::new);
                if (rule.getId() == null) {
                    var resource = definitions.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, definitions.createResource(SWRL_IMP)).toList()
                            .stream().filter(r -> name.equals(r.getLocalName())).findFirst().orElseThrow();
                    rule.setRuleId(name);
                    rule.setName(name);
                    rule.setRuleType(AlignmentRule.RuleType.INFERENCE);
                    rule.setSwrlExpression(SwrlText.of(resource));
                    rule.setDescription(SwrlText.description(resource));
                    rule.setCreatedAt(LocalDateTime.now());
                }
                rule.setActive(true);
                rule.setUpdatedAt(LocalDateTime.now());
                alignmentRuleRepository.saveAndFlush(rule);
            }
        }
        for (ExecutedRule run : executed) {
            if (run.getId() == null) continue; // regla de la sesión: ya quedó activa arriba
            AlignmentRule rule = alignmentRuleRepository.findById(run.getId()).orElseThrow(() -> new ProtysMappingException("Registered rule not found: " + run.getId()));
            if (Boolean.TRUE.equals(rule.getActive())) continue;
            rule.setActive(true);
            rule.setUpdatedAt(LocalDateTime.now());
            alignmentRuleRepository.saveAndFlush(rule);
        }
        // El comprobante guardado pasa a llevar el id de las reglas recién registradas.
        List<ExecutedRule> receipt = executed.stream().map(r -> r.getId() != null ? r
                : new ExecutedRule(alignmentRuleRepository.findByRuleId(r.getName()).map(AlignmentRule::getId).orElse(null), r.getName())).toList();
        session.setExecutedRulesJson(JSON.writeValueAsString(receipt));
        return sessionRepository.saveAndFlush(session);
    }

    private static String definitionIri(Model definitions, String name) {
        return definitions.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, definitions.createResource(SWRL_IMP)).toList()
                .stream().filter(r -> name.equals(r.getLocalName())).findFirst()
                .orElseThrow(() -> new ProtysMappingException("Session rule without definition: " + name)).getURI();
    }

    /**
     * Deshace lo que un completado interrumpido cambió en las reglas globales: borra los registros y las
     * definiciones de las reglas que creó, saca los triples sueltos que sumó y vuelve a inactivas las que
     * activó. Repetirlo no cambia nada más.
     */
    private void revertRuleChanges(StandardIncorporationSession session) throws Exception {
        RuleChanges changes = ruleChanges(session);
        if (!changes.defined.isEmpty() || !changes.addedTriples.isEmpty()) {
            Model graph = fusekiService.getModelOrEmpty(AlignmentService.RULE_GRAPH);
            var imp = graph.createResource(SWRL_IMP);
            for (var rule : graph.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, imp).toList())
                if (!rule.isAnon() && changes.defined.contains(rule.getLocalName()))
                    graph.remove(AlignmentService.ruleDefinition(graph, rule));
            for (String triple : changes.addedTriples) {
                Model one = ModelFactory.createDefaultModel();
                RDFDataMgr.read(one, new java.io.StringReader(triple), null, Lang.NTRIPLES);
                graph.remove(one);
            }
            fusekiService.putModel(AlignmentService.RULE_GRAPH, graph);
        }
        for (String name : changes.created)
            alignmentRuleRepository.findByRuleId(name).ifPresent(alignmentRuleRepository::delete);
        for (String name : changes.reactivated)
            alignmentRuleRepository.findByRuleId(name).filter(r -> Boolean.TRUE.equals(r.getActive())).ifPresent(rule -> {
                rule.setActive(false);
                rule.setUpdatedAt(LocalDateTime.now());
                alignmentRuleRepository.saveAndFlush(rule);
            });
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
        if (session.getDataTripleCount() != null) deleteOwnedGraph(dataGraph(session.getTemporaryGraph()));
        if (!sessionRules(session).isEmpty()) deleteOwnedGraph(rulesGraph(session.getTemporaryGraph()));
        if (session.isPermanentWriteStarted()) {
            deleteOwnedGraph(session.getPermanentGraph());
            if (session.getDataTripleCount() != null) {
                String dataTarget = dataGraph(session.getPermanentGraph());
                // Un completado interrumpido pudo registrar ya el módulo de datos de esta sesión.
                ontologyModuleRepository.findByNamedGraph(dataTarget).ifPresent(ontologyModuleRepository::delete);
                deleteOwnedGraph(dataTarget);
            }
            try {
                revertRuleChanges(session);
            } catch (Exception e) {
                throw new ProtysMappingException("Could not revert the rule changes of the interrupted completion", e);
            }
        }
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
        result.setSessionRuleIds(sessionRules(session));
        result.setActivatedRules(result.getExecutedRules().stream().map(ExecutedRule::getName).toList());
        result.setInferencesGenerated(session.getInferredTriples());
        result.setAlignmentRulesCreated(0);
        result.setMessage("Successfully incorporated " + session.getStandardName());
        result.setCleanupPending(false);
        try {
            deleteOwnedGraph(session.getTemporaryGraph());
            if (session.getDataTripleCount() != null) deleteOwnedGraph(dataGraph(session.getTemporaryGraph()));
            if (!sessionRules(session).isEmpty()) deleteOwnedGraph(rulesGraph(session.getTemporaryGraph()));
        }
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
        if (session.getDataTripleCount() != null) combined.add(requiredGraph(dataGraph(session.getTemporaryGraph())));
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
