package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.*;
import org.springframework.mock.web.MockMultipartFile;

/** Paso 1 por extensión y datos de instancia opcionales de la sesión. */
class WizardInstanceDataTest extends WizardSelectionRegressionTest {
    static final String TTL = "@prefix owl: <http://www.w3.org/2002/07/owl#> . <https://example.org/Pieza> a owl:Class .";
    static final String DATOS = "@prefix owl: <http://www.w3.org/2002/07/owl#> . <https://example.org/pieza1> a owl:NamedIndividual, <https://example.org/Pieza> .";

    StandardIncorporationSession session(UUID id) { return (StandardIncorporationSession) sessions.get(id); }
    static MockMultipartFile ttl(String name, String text) { return new MockMultipartFile("file", name, "text/turtle", text.getBytes(StandardCharsets.UTF_8)); }
    UUID withData(String name) {
        UUID id = upload(name);
        var result = service.step1_uploadData(id, ttl("datos.ttl", DATOS));
        assertEquals(true, result.getStep1_parsed(), result.getStep1_message());
        assertEquals(2, result.getDataTripleCount());
        return id;
    }
    String tempData(UUID id) { return StandardIncorporationService.dataGraph(session(id).getTemporaryGraph()); }
    String permanentData(UUID id) { return StandardIncorporationService.dataGraph(session(id).getPermanentGraph()); }

    @Test void elPaso1LeeTurtlePorLaExtension() {
        var result = service.step1_upload(ttl("estandar.ttl", TTL), "Turtle");
        assertEquals(true, result.getStep1_parsed(), result.getStep1_message());
        assertEquals(1, result.getTripleCount());
    }

    @Test void losDatosVanASuGrafoTemporalYEntranEnLaValidacion() {
        UUID id = withData("ConDatos");
        assertTrue(graphs.containsKey(tempData(id)));
        assertTrue(tempData(id).startsWith(StandardIncorporationService.TEMP_PREFIX));
        assertEquals(true, service.step2_validateConsistency(id).getStep2_consistent());
        verify(reasoning).classify(argThat(m -> m.contains(m.createResource("https://example.org/pieza1"), RDF.type, OWL2.NamedIndividual)));
        assertTrue(registered.isEmpty());
    }

    @Test void subirDatosInvalidaLaValidacionAnterior() {
        UUID id = upload("Invalida"); select(id, List.of(a));
        assertEquals(true, service.step1_uploadData(id, ttl("datos.ttl", DATOS)).getStep1_parsed());
        assertEquals(true, service.step3_defineAlignments(id, List.of(a)).getStep3_error());
    }

    @Test void completarRegistraLosDatosComoModuloPropioYLimpiaLosTemporales() {
        UUID id = withData("Completa"); select(id, List.of(a));
        assertEquals(true, service.step4_verifyInferences(id).getStep4_verified());
        var done = service.completeIncorporation(id);
        assertEquals("COMPLETED", done.getStatus(), done.getMessage());
        assertTrue(graphs.containsKey(permanentData(id)));
        assertFalse(graphs.containsKey(tempData(id)));
        assertEquals(2, registered.size());
        OntologyModule data = registered.values().stream().filter(m -> m.getNamedGraph().equals(permanentData(id))).findFirst().orElseThrow();
        assertNull(data.getIncorporationSessionId());
        assertEquals(2L, data.getTripleCount());
        assertTrue(AlignmentService.isPermanentModule(data));
    }

    @Test void cancelarBorraLosDatosTemporales() {
        UUID id = withData("Cancela");
        service.cancelIncorporation(id);
        assertFalse(graphs.containsKey(tempData(id)));
        assertEquals("CANCELLED", session(id).getPhase());
    }

    @Test void cancelarUnCompletadoInterrumpidoQuitaElModuloDeDatos() {
        UUID id = withData("Interrumpida"); select(id, List.of(a)); service.step4_verifyInferences(id);
        int[] calls = {0};
        doAnswer(call -> { if (++calls[0] == 2) throw new RuntimeException("Database unavailable"); return saveModule(call.getArgument(0)); })
                .when(modules).saveAndFlush(any());
        assertEquals("NOT_EVALUATED", service.completeIncorporation(id).getStatus());
        assertEquals(1, registered.size());
        service.cancelIncorporation(id);
        assertTrue(registered.isEmpty());
        assertFalse(graphs.containsKey(permanentData(id)));
        assertFalse(graphs.containsKey(session(id).getPermanentGraph()));
    }
}
