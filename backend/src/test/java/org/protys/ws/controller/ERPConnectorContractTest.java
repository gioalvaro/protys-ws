package org.protys.ws.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.protys.ws.config.SecurityConfig;
import org.protys.ws.dto.MaterializationResult;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.exception.ERPMaterializationException;
import org.protys.ws.model.ERPConnector;
import org.protys.ws.service.ERPConnectorService;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(SecurityConfig.class)
@WebMvcTest(ERPController.class)
@ExtendWith(OutputCaptureExtension.class)
class ERPConnectorContractTest {
    @Autowired MockMvc mvc;
    @MockBean ERPConnectorService service;
    private static final String SECRET = "synthetic-secret-http-test";
    private final UUID id = UUID.randomUUID();

    private ERPConnector connector() {
        return ERPConnector.builder().id(id).name("Synthetic").password(SECRET)
                .jdbcUrl("jdbc:postgresql://localhost/test?password=" + SECRET)
                .type(ERPConnector.ERPType.CUSTOM).build();
    }

    @Test void listAndDetailNeverSerializeStoredSecret() throws Exception {
        when(service.getConnectors()).thenReturn(List.of(connector()));
        when(service.getConnector(id)).thenReturn(connector());
        mvc.perform(get("/api/erp/connectors"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].hasPassword").value(true))
                .andExpect(content().string(not(containsString(SECRET))));
        mvc.perform(get("/api/erp/connectors/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test void creationAndUpdateAcceptSecretWithoutEchoingIt() throws Exception {
        when(service.registerConnector(any())).thenReturn(connector());
        when(service.updateConnector(eq(id), any())).thenReturn(connector());
        String body = "{\"name\":\"Synthetic\",\"password\":\"" + SECRET + "\"}";
        mvc.perform(post("/api/erp/connectors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(content().string(not(containsString(SECRET))));
        verify(service).registerConnector(argThat(value -> SECRET.equals(value.getPassword())));
        mvc.perform(put("/api/erp/connectors/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString(SECRET))));
        verify(service).updateConnector(eq(id), argThat(value -> SECRET.equals(value.getPassword())));
    }

    @Test void emptyIsNotPresentedAsSuccessfulPublication() throws Exception {
        when(service.materialize(id)).thenReturn(MaterializationResult.builder()
                .connectorId(id).status("EMPTY").tripleCount(0L).build());
        mvc.perform(post("/api/erp/connectors/{id}/materialize", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.statusMessage", containsString("preserved")));
    }

    @Test void unsupportedMappingHasStructuredErrorAndOtherFailuresDoNotEchoSecrets(CapturedOutput output) throws Exception {
        when(service.materialize(id)).thenThrow(new ProtysMappingException("UNSUPPORTED_MAPPING"));
        mvc.perform(post("/api/erp/connectors/{id}/materialize", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MAPPING"));
        doThrow(new IllegalStateException(SECRET)).when(service).materialize(id);
        mvc.perform(post("/api/erp/connectors/{id}/materialize", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MATERIALIZATION_FAILED"))
                .andExpect(content().string(not(containsString(SECRET))));
        when(service.registerConnector(any())).thenThrow(new IllegalStateException(SECRET));
        mvc.perform(post("/api/erp/connectors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic\",\"password\":\"" + SECRET + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString(SECRET))));
        assertFalse(output.getAll().contains(SECRET));
    }

    @Test void malformedPasswordDoesNotEchoDeserializationDetails(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/erp/connectors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic\",\"password\":{\"value\":\"" + SECRET + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ERP_REQUEST"))
                .andExpect(content().string(not(containsString(SECRET))));
        assertFalse(output.getAll().contains(SECRET));
    }

    @Test void failedRecoveryReturnsAnExplicitOperationReceipt() throws Exception {
        UUID operation = UUID.randomUUID();
        when(service.materialize(id)).thenThrow(new ERPMaterializationException(operation, "COMPENSATION_FAILED"));
        mvc.perform(post("/api/erp/connectors/{id}/materialize", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.operationId").value(operation.toString()))
                .andExpect(jsonPath("$.compensationStatus").value("COMPENSATION_FAILED"));
    }
}
