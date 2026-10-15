package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.protys.ws.dto.StandardIncorporationResult;
import org.protys.ws.model.*;
import org.protys.ws.repository.*;
import org.springframework.mock.web.MockMultipartFile;

/** Public service API regression, also compilable against the pre-fix service. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WizardSelectionRegressionTest {
    @Mock OntologyService ontologyService;
    @Mock AlignmentService alignmentService;
    @Mock FusekiService fuseki;
    @Mock ReasoningService reasoning;
    @Mock OntologyModuleRepository modules;
    @Mock AlignmentRuleRepository rules;
    @InjectMocks StandardIncorporationService service;
    final Map<String, Model> graphs = new HashMap<>();
    final Map<UUID, OntologyModule> registered = new LinkedHashMap<>();
    final Map<UUID, Object> sessions = new HashMap<>();
    final Map<UUID, AlignmentRule> ruleRecords = new HashMap<>();
    AlignmentRule a, b;

    @BeforeEach void setup() throws Exception {
        // Reflection keeps this regression compilable on the snapshot without the new entity.
        try {
            Field field = StandardIncorporationService.class.getDeclaredField("sessionRepository");
            Object repository = mock(field.getType(), invocation -> {
                String name = invocation.getMethod().getName();
                if (name.equals("findById")) return Optional.ofNullable(copySession(sessions.get(invocation.getArgument(0))));
                if (name.equals("saveAndFlush")) {
                    Object value = invocation.getArgument(0);
                    sessions.put((UUID) value.getClass().getMethod("getId").invoke(value), copySession(value));
                    return copySession(value);
                }
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            });
            field.setAccessible(true); field.set(service, repository);
        } catch (NoSuchFieldException historicalService) { /* Baseline has in-memory state. */ }
        when(modules.save(any())).thenAnswer(call -> saveModule(call.getArgument(0)));
        when(modules.saveAndFlush(any())).thenAnswer(call -> saveModule(call.getArgument(0)));
        when(modules.findById(any())).thenAnswer(call -> Optional.ofNullable(registered.get(call.getArgument(0))));
        when(modules.findAll()).thenAnswer(call -> new ArrayList<>(registered.values()));
        when(modules.findByNamedGraph(anyString())).thenAnswer(call -> registered.values().stream().filter(m -> m.getNamedGraph().equals(call.getArgument(0))).findFirst());
        doAnswer(call -> { registered.remove(((OntologyModule) call.getArgument(0)).getId()); return null; }).when(modules).delete(any());
        when(fuseki.getModel(anyString())).thenAnswer(call -> ModelFactory.createDefaultModel().add(graphs.getOrDefault(call.getArgument(0), ModelFactory.createDefaultModel())));
        doAnswer(call -> { graphs.put(call.getArgument(0), ModelFactory.createDefaultModel().add((Model) call.getArgument(1))); return null; }).when(fuseki).putModel(anyString(), any(Model.class));
        doAnswer(call -> { Model model = ModelFactory.createDefaultModel(); RDFDataMgr.read(model, (java.io.InputStream) call.getArgument(1), Lang.RDFXML); graphs.put(call.getArgument(0), model); return null; }).when(fuseki).loadModel(anyString(), any());
        doAnswer(call -> { graphs.remove(call.getArgument(0)); return null; }).when(fuseki).deleteGraph(anyString());
        when(reasoning.classify(any(Model.class))).thenReturn(pass(0));
        when(reasoning.classify(any(Model.class), anyList())).thenAnswer(call -> pass(((List<?>) call.getArgument(1)).size()));
        when(rules.findById(any())).thenAnswer(call -> Optional.ofNullable(ruleRecords.get(call.getArgument(0))));
        a = rule("R01", false); b = rule("R02", true);
        Model definitions = ModelFactory.createDefaultModel();
        definitions.add(definitions.createResource("https://example.org/definitions"), RDF.type, OWL.Ontology);
        graphs.put("http://w3id.org/protys/ontology/active-rule-definitions", definitions);
        when(rules.findAll()).thenAnswer(call -> new ArrayList<>(ruleRecords.values()));
        when(alignmentService.getActiveRules()).thenReturn(List.of(b));
    }

    static Object copySession(Object source) throws Exception {
        if (source == null) return null;
        Object copy = source.getClass().getDeclaredConstructor().newInstance();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    OntologyModule saveModule(OntologyModule module) {
        if (module.getId() == null) module.setId(UUID.randomUUID());
        registered.put(module.getId(), module); return module;
    }
    AlignmentRule rule(String name, boolean active) {
        AlignmentRule rule = new AlignmentRule(); rule.setId(UUID.randomUUID()); rule.setRuleId(name); rule.setName(name); rule.setActive(active); rule.setCreatedAt(LocalDateTime.now());
        ruleRecords.put(rule.getId(), rule); return rule;
    }
    static ReasoningService.ClassificationResult pass(int imported) {
        return ReasoningService.ClassificationResult.builder().consistent(true).validationStatus("CONSISTENT").owlValidationStatus("CONSISTENT").importedRuleCount(imported).deductions(ModelFactory.createDefaultModel()).build();
    }
    UUID upload(String name) {
        Model model = ModelFactory.createDefaultModel(); model.add(model.createResource("https://example.org/" + name), RDF.type, OWL.Class);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); RDFDataMgr.write(out, model, Lang.RDFXML);
        StandardIncorporationResult result = service.step1_upload(new MockMultipartFile("file", "standard.owl", "application/rdf+xml", out.toByteArray()), name);
        assertEquals(true, result.getStep1_parsed(), result.getStep1_message()); return result.getTempModuleId();
    }
    void select(UUID id, List<AlignmentRule> selection) {
        assertEquals(true, service.step2_validateConsistency(id).getStep2_consistent());
        assertNotEquals(true, service.step3_defineAlignments(id, selection).getStep3_error());
    }

    @Test void selectedInactiveAExecutesInsteadOfGloballyActiveB() {
        UUID id = upload("SessionA"); select(id, List.of(a));
        assertEquals(true, service.step4_verifyInferences(id).getStep4_verified());
        verify(reasoning).classify(any(Model.class), eq(List.of("R01")));
        verify(rules, never()).save(any()); verify(rules, never()).saveAll(any());
        assertFalse(a.getActive()); assertTrue(b.getActive());
    }

    @Test void sessionAValidationDoesNotReadSessionB() {
        UUID id = upload("SessionA"); upload("SessionB");
        assertEquals(true, service.step2_validateConsistency(id).getStep2_consistent());
        verify(reasoning).classify(argThat(model -> model.contains(model.createResource("https://example.org/SessionA"), RDF.type, OWL.Class)
                && !model.contains(model.createResource("https://example.org/SessionB"), RDF.type, OWL.Class)));
    }

    @Test void cancellationCannotDeleteRecentGlobalRules() {
        UUID id = upload("SessionA"); service.cancelIncorporation(id); service.cancelIncorporation(id);
        verify(rules, never()).deleteAll(any()); verify(rules, never()).deleteById(any());
    }
}
