package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.*;

/** Reglas SWRL nuevas de la sesión, paso 4 con ellas y completar que las registra y activa (huecos 1 y 4). */
class WizardSessionRulesTest extends WizardSelectionRegressionTest {
    static final String IMP = AlignmentRuleUploadTest.SWRL + "Imp";

    @BeforeEach void sessionRuleStubs() {
        when(fuseki.getModelOrEmpty(anyString())).thenAnswer(c -> ModelFactory.createDefaultModel().add(graphs.getOrDefault(c.getArgument(0), ModelFactory.createDefaultModel())));
        when(rules.findByRuleId(anyString())).thenAnswer(c -> ruleRecords.values().stream().filter(r -> r.getRuleId().equals(c.getArgument(0))).findFirst());
        doAnswer(c -> { ruleRecords.remove(((AlignmentRule) c.getArgument(0)).getId()); return null; }).when(rules).delete(any());
        when(rules.saveAndFlush(any())).thenAnswer(c -> { AlignmentRule r = c.getArgument(0); if (r.getId() == null) r.setId(UUID.randomUUID()); ruleRecords.put(r.getId(), r); return r; });
    }

    StandardIncorporationSession session(UUID id) { return (StandardIncorporationSession) sessions.get(id); }
    String rulesGraph(UUID id) { return StandardIncorporationService.rulesGraph(session(id).getTemporaryGraph()); }
    Model definitions() { return graphs.get(AlignmentService.RULE_GRAPH); }
    boolean defines(Model m, String id) { return m.contains(m.createResource(AlignmentRuleUploadTest.A + id), RDF.type, m.createResource(IMP)); }
    AlignmentRule row(String id) { return ruleRecords.values().stream().filter(r -> r.getRuleId().equals(id)).findFirst().orElse(null); }

    UUID readyWithR27(String name) {
        UUID id = upload(name); select(id, List.of(a));
        var up = service.step3_uploadRules(id, AlignmentRuleUploadTest.file(AlignmentRuleUploadTest.rule("R27", "https://e.org/C", "https://e.org/D")));
        assertEquals(false, up.getStep3_error(), up.getStep3_message());
        assertEquals(List.of("R27"), up.getSessionRuleIds());
        return id;
    }

    @Test void lasReglasDeLaSesionVanASuGrafoTemporalSinRegistrarse() {
        UUID id = readyWithR27("Guarda");
        assertTrue(defines(graphs.get(rulesGraph(id)), "R27"));
        assertTrue(rulesGraph(id).startsWith(StandardIncorporationService.TEMP_PREFIX));
        assertNull(row("R27")); assertFalse(defines(definitions(), "R27"));
    }

    @Test void rechazaIdentificadoresRegistradosYArchivosSinReglas() {
        UUID id = upload("Rechaza"); select(id, List.of(a));
        var registrada = service.step3_uploadRules(id, AlignmentRuleUploadTest.file(AlignmentRuleUploadTest.rule("R01", "https://e.org/C", "https://e.org/D")));
        assertEquals(true, registrada.getStep3_error());
        Model vacio = ModelFactory.createDefaultModel(); vacio.add(vacio.createResource("https://e.org/x"), RDF.type, vacio.createResource("https://e.org/Y"));
        assertEquals(true, service.step3_uploadRules(id, AlignmentRuleUploadTest.file(vacio)).getStep3_error());
        assertFalse(graphs.containsKey(rulesGraph(id)));
    }

    @Test void elPaso4RazonaConLaSeleccionMasLasReglasDeLaSesion() {
        UUID id = readyWithR27("Paso4");
        var verified = service.step4_verifyInferences(id);
        assertEquals(true, verified.getStep4_verified(), verified.getStep4_message());
        verify(reasoning).classify(argThat(m -> defines(m, "R27")), eq(List.of("R01", "R27")));
        assertEquals(List.of("R01", "R27"), verified.getExecutedRules().stream().map(r -> r.getName()).toList());
        assertNull(verified.getExecutedRules().get(1).getId());
    }

    @Test void cargarReglasDespuesDeVerificarObligaAVerificarDeNuevo() {
        UUID id = upload("Reverifica"); select(id, List.of(a));
        assertEquals(true, service.step4_verifyInferences(id).getStep4_verified());
        service.step3_uploadRules(id, AlignmentRuleUploadTest.file(AlignmentRuleUploadTest.rule("R27", "https://e.org/C", "https://e.org/D")));
        assertFalse(session(id).isInferenceVerified());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        assertTrue(registered.isEmpty()); assertNull(row("R27"));
    }

    @Test void completarRegistraSumaYActivaLasReglasVerificadas() {
        UUID id = readyWithR27("Completa");
        assertFalse(a.getActive());
        service.step4_verifyInferences(id);
        var done = service.completeIncorporation(id);
        assertEquals("COMPLETED", done.getStatus(), done.getMessage());
        assertTrue(defines(definitions(), "R27"));
        assertTrue(definitions().contains(definitions().createResource("https://example.org/definitions"), RDF.type, org.apache.jena.vocabulary.OWL.Ontology));
        assertNotNull(row("R27")); assertTrue(row("R27").getActive());
        assertTrue(ruleRecords.get(a.getId()).getActive());
        assertEquals(List.of("R01", "R27"), done.getActivatedRules());
        assertTrue(done.getExecutedRules().stream().allMatch(r -> r.getId() != null));
        assertFalse(graphs.containsKey(rulesGraph(id)));
    }

    @Test void reintentarElCompletadoNoDuplicaDefinicionesNiRegistros() throws Exception {
        UUID id = readyWithR27("Reintento"); service.step4_verifyInferences(id);
        doThrow(new RuntimeException("Database unavailable")).when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        long size = definitions().size();
        doAnswer(call -> saveModule(call.getArgument(0))).when(modules).saveAndFlush(any());
        var done = service.completeIncorporation(id);
        assertEquals("COMPLETED", done.getStatus(), done.getMessage());
        assertEquals(size, definitions().size());
        assertEquals(1, ruleRecords.values().stream().filter(r -> r.getRuleId().equals("R27")).count());
    }

    @Test void cancelarBorraLasReglasDeLaSesionYNoRegistraNada() {
        UUID id = readyWithR27("Cancela");
        service.cancelIncorporation(id);
        assertFalse(graphs.containsKey(rulesGraph(id)));
        assertNull(row("R27")); assertFalse(defines(definitions(), "R27"));
    }

    /** Estado de las reglas que ve la plataforma: registro, estado de cada una y grafo global de definiciones. */
    Map<String, Boolean> ruleStates() {
        Map<String, Boolean> states = new TreeMap<>();
        ruleRecords.values().forEach(r -> states.put(r.getRuleId(), r.getActive()));
        return states;
    }

    @Test void cancelarUnCompletadoCortadoDejaLasReglasComoEstaban() {
        UUID id = readyWithR27("Cortado");
        service.step4_verifyInferences(id);
        Map<String, Boolean> before = ruleStates();
        Model baseline = ModelFactory.createDefaultModel().add(definitions());
        // Se corta después de activar las reglas, al registrar el módulo del estándar.
        doThrow(new RuntimeException("Database unavailable")).when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        assertTrue(row("R27").getActive());
        assertTrue(ruleRecords.get(a.getId()).getActive());
        assertTrue(defines(definitions(), "R27"));

        service.cancelIncorporation(id);
        assertEquals(before, ruleStates());
        assertTrue(definitions().isIsomorphicWith(baseline), "el grafo de reglas no volvió a la línea de base");
        assertEquals("CANCELLED", session(id).getPhase());

        service.cancelIncorporation(id); // idempotente
        assertEquals(before, ruleStates());
        assertTrue(definitions().isIsomorphicWith(baseline));
    }

    @Test void cancelarNoTocaReglasQueYaEstabanActivasNiDefinicionesPrevias() {
        UUID id = upload("Previas"); select(id, List.of(b));
        service.step4_verifyInferences(id);
        Model baseline = ModelFactory.createDefaultModel().add(definitions());
        doThrow(new RuntimeException("Database unavailable")).when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        service.cancelIncorporation(id);
        assertTrue(ruleRecords.get(b.getId()).getActive());
        assertFalse(ruleRecords.get(a.getId()).getActive());
        assertTrue(definitions().isIsomorphicWith(baseline));
    }
}
