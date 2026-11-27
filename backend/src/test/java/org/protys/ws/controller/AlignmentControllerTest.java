package org.protys.ws.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.protys.ws.dto.InferenceStats;
import org.protys.ws.service.AlignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Cuerpo del razonamiento: dataGraphs nulo es un error del pedido (400), no del servidor. */
@org.springframework.context.annotation.Import(org.protys.ws.config.SecurityConfig.class)
@WebMvcTest(AlignmentController.class)
class AlignmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AlignmentService alignmentService;

    @Test
    void dataGraphsNuloDa400ConMensaje() throws Exception {
        for (String body : List.of("{\"dataGraphs\": null}", "{\"dataGraphs\": [null]}")) {
            mockMvc.perform(post("/api/alignment/reasoning/execute").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("dataGraphs must be a list of graph IRIs (null is not allowed)"));
        }
        verifyNoInteractions(alignmentService);
    }

    @Test
    void sinCuerpoRazonaComoAntes() throws Exception {
        InferenceStats stats = new InferenceStats();
        stats.setStatus("SUCCESS");
        when(alignmentService.executeReasoning()).thenReturn(stats);
        mockMvc.perform(post("/api/alignment/reasoning/execute")).andExpect(status().isCreated());
    }
}
