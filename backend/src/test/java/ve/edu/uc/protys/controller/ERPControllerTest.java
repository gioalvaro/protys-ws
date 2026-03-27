package ve.edu.uc.protys.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ve.edu.uc.protys.dto.MaterializationResult;
import ve.edu.uc.protys.dto.SchemaMetadata;
import ve.edu.uc.protys.model.ERPConnector;
import ve.edu.uc.protys.exception.ProtysFusekiException;
import ve.edu.uc.protys.service.ERPConnectorService;

@WebMvcTest(ERPController.class)
class ERPControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ERPConnectorService erpConnectorService;

    /**
     * Test GET /api/erp/connectors returns connector list.
     */
    @Test
    void testGetConnectorsReturnsConnectorList() throws Exception {
        // Arrange
        ERPConnector conn1 = ERPConnector.builder()
                .id(UUID.randomUUID())
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .status(ERPConnector.ConnectorStatus.CONNECTED)
                .active(true)
                .build();

        ERPConnector conn2 = ERPConnector.builder()
                .id(UUID.randomUUID())
                .name("Odoo Paint")
                .type(ERPConnector.ERPType.ODOO)
                .jdbcUrl("jdbc:postgresql://localhost:5432/odoo")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .active(true)
                .build();

        when(erpConnectorService.getConnectors()).thenReturn(Arrays.asList(conn1, conn2));

        // Act & Assert
        mockMvc.perform(get("/api/erp/connectors")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", equalTo("ADempiere Paint")))
                .andExpect(jsonPath("$[1].name", equalTo("Odoo Paint")));

        verify(erpConnectorService, times(1)).getConnectors();
    }

    /**
     * Test POST /api/erp/connectors registers new connector.
     */
    @Test
    void testRegisterConnector() throws Exception {
        // Arrange
        ERPConnector connector = ERPConnector.builder()
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .namedGraph("urn:protys:erp:adempiere")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .build();

        ERPConnector registered = ERPConnector.builder()
                .id(UUID.randomUUID())
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .namedGraph("urn:protys:erp:adempiere")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(erpConnectorService.registerConnector(any(ERPConnector.class))).thenReturn(registered);

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connector)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("ADempiere Paint")))
                .andExpect(jsonPath("$.type", equalTo("ADEMPIERE")));

        verify(erpConnectorService, times(1)).registerConnector(any(ERPConnector.class));
    }

    /**
     * Test POST /api/erp/connectors with missing name returns 400.
     */
    @Test
    void testRegisterConnectorWithMissingNameReturns400() throws Exception {
        // Arrange
        ERPConnector connector = ERPConnector.builder()
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .build();
        // name is null

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connector)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test POST /api/erp/connectors/{id}/test with successful connection.
     */
    @Test
    void testConnectionTestSuccessful() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        when(erpConnectorService.testConnection(connectorId)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors/{id}/test", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected", equalTo(true)))
                .andExpect(jsonPath("$.connectorId", equalTo(connectorId.toString())));

        verify(erpConnectorService, times(1)).testConnection(connectorId);
    }

    /**
     * Test POST /api/erp/connectors/{id}/test with failed connection.
     */
    @Test
    void testConnectionTestFailed() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        when(erpConnectorService.testConnection(connectorId)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors/{id}/test", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected", equalTo(false)));
    }

    /**
     * Test POST /api/erp/connectors/{id}/materialize produces RDF triples.
     */
    @Test
    void testMaterializeConnectorData() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        MaterializationResult result = MaterializationResult.builder()
                .connectorId(connectorId)
                .status("SUCCESS")
                .tripleCount(1250L)
                .executionTimeMs(3400L)
                .namedGraph("urn:protys:erp:adempiere")
                .startedAt(LocalDateTime.now().minusSeconds(4))
                .completedAt(LocalDateTime.now())
                .build();

        when(erpConnectorService.materialize(connectorId)).thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors/{id}/materialize", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.tripleCount", equalTo(1250)))
                .andExpect(jsonPath("$.namedGraph", equalTo("urn:protys:erp:adempiere")));

        verify(erpConnectorService, times(1)).materialize(connectorId);
    }

    /**
     * Test POST /api/erp/connectors/{id}/introspect returns schema metadata.
     */
    @Test
    void testIntrospectSchema() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();

        SchemaMetadata col1 = new SchemaMetadata();
        col1.setName("m_product_id");
        col1.setType("INTEGER");
        col1.setNullable(false);

        SchemaMetadata col2 = new SchemaMetadata();
        col2.setName("name");
        col2.setType("VARCHAR");
        col2.setNullable(false);

        Map<String, List<SchemaMetadata>> schema = Map.of(
                "M_Product", List.of(col1, col2)
        );

        when(erpConnectorService.introspectSchema(connectorId)).thenReturn(schema);

        // Act & Assert
        mockMvc.perform(post("/api/erp/connectors/{id}/introspect", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.M_Product", hasSize(2)))
                .andExpect(jsonPath("$.M_Product[0].name", equalTo("m_product_id")));

        verify(erpConnectorService, times(1)).introspectSchema(connectorId);
    }

    /**
     * Test GET /api/erp/connectors/{id} returns connector details.
     */
    @Test
    void testGetConnectorById() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        ERPConnector connector = ERPConnector.builder()
                .id(connectorId)
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .status(ERPConnector.ConnectorStatus.CONNECTED)
                .active(true)
                .build();

        when(erpConnectorService.getConnector(connectorId)).thenReturn(connector);

        // Act & Assert
        mockMvc.perform(get("/api/erp/connectors/{id}", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("ADempiere Paint")))
                .andExpect(jsonPath("$.type", equalTo("ADEMPIERE")));

        verify(erpConnectorService, times(1)).getConnector(connectorId);
    }

    /**
     * Test DELETE /api/erp/connectors/{id} returns 204.
     */
    @Test
    void testDeleteConnectorReturns204() throws Exception {
        // Arrange
        UUID connectorId = UUID.randomUUID();
        doNothing().when(erpConnectorService).deleteConnector(connectorId);

        // Act & Assert
        mockMvc.perform(delete("/api/erp/connectors/{id}", connectorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(erpConnectorService, times(1)).deleteConnector(connectorId);
    }
}
