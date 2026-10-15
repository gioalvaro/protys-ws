package org.protys.ws.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.protys.ws.service.StandardIncorporationService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StandardWizardControllerTest {
    @Test void operationalIllegalStateCannotBeReportedAsCompleted() throws Exception {
        var service = mock(StandardIncorporationService.class);
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("Database unavailable")).when(service).cancelIncorporation(id);
        var mvc = MockMvcBuilders.standaloneSetup(new StandardWizardController(service)).build();
        mvc.perform(delete("/api/wizard/" + id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("CLEANUP_PENDING"))
                .andExpect(jsonPath("$.tempModuleId").value(id.toString()));
    }

    @Test void onlyAnExplicitCompletedSessionProducesTheRecoveryConflict() throws Exception {
        var service = mock(StandardIncorporationService.class);
        UUID id = UUID.randomUUID();
        doThrow(new StandardIncorporationService.IncorporationAlreadyCompletedException()).when(service).cancelIncorporation(id);
        var mvc = MockMvcBuilders.standaloneSetup(new StandardWizardController(service)).build();
        mvc.perform(delete("/api/wizard/" + id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.tempModuleId").value(id.toString()));
    }
}
