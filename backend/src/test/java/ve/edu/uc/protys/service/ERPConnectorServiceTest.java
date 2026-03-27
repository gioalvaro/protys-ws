package ve.edu.uc.protys.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ve.edu.uc.protys.dto.MaterializationResult;
import ve.edu.uc.protys.model.ERPConnector;
import ve.edu.uc.protys.exception.ProtysFusekiException;
import ve.edu.uc.protys.repository.ERPConnectorRepository;

@ExtendWith(MockitoExtension.class)
class ERPConnectorServiceTest {

    @Mock
    private ERPConnectorRepository erpConnectorRepository;

    @Mock
    private FusekiService fusekiService;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private ERPConnectorService erpConnectorService;

    private ERPConnector testConnector;

    @BeforeEach
    void setUp() {
        testConnector = ERPConnector.builder()
                .id(UUID.randomUUID())
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .username("admin")
                .password("secret")
                .databaseName("adempiere")
                .namedGraph("urn:protys:erp:adempiere")
                .r2rmlMappingPath("r2rml/adempiere-mappings.ttl")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Test registerConnector saves and returns connector.
     */
    @Test
    void testRegisterConnectorSuccessfully() {
        // Arrange
        ERPConnector input = ERPConnector.builder()
                .name("ADempiere Paint")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://localhost:5432/adempiere")
                .namedGraph("urn:protys:erp:adempiere")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .build();

        when(erpConnectorRepository.save(any(ERPConnector.class))).thenReturn(testConnector);

        // Act
        ERPConnector result = erpConnectorService.registerConnector(input);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("ADempiere Paint", result.getName());

        verify(erpConnectorRepository, times(1)).save(any(ERPConnector.class));
    }

    /**
     * Test getConnectors returns all connectors.
     */
    @Test
    void testGetConnectorsReturnsList() {
        // Arrange
        ERPConnector conn2 = ERPConnector.builder()
                .id(UUID.randomUUID())
                .name("Odoo Paint")
                .type(ERPConnector.ERPType.ODOO)
                .jdbcUrl("jdbc:postgresql://localhost:5432/odoo")
                .status(ERPConnector.ConnectorStatus.REGISTERED)
                .build();

        when(erpConnectorRepository.findAll()).thenReturn(List.of(testConnector, conn2));

        // Act
        List<ERPConnector> result = erpConnectorService.getConnectors();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("ADempiere Paint", result.get(0).getName());
        assertEquals("Odoo Paint", result.get(1).getName());

        verify(erpConnectorRepository, times(1)).findAll();
    }

    /**
     * Test getConnector returns single connector by ID.
     */
    @Test
    void testGetConnectorById() {
        // Arrange
        when(erpConnectorRepository.findById(testConnector.getId()))
                .thenReturn(Optional.of(testConnector));

        // Act
        ERPConnector result = erpConnectorService.getConnector(testConnector.getId());

        // Assert
        assertNotNull(result);
        assertEquals("ADempiere Paint", result.getName());
    }

    /**
     * Test getConnector throws exception when not found.
     */
    @Test
    void testGetConnectorNotFoundThrowsException() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();
        when(erpConnectorRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProtysFusekiException.class, () -> {
            erpConnectorService.getConnector(nonExistentId);
        });
    }

    /**
     * Test updateConnector updates and saves.
     */
    @Test
    void testUpdateConnector() {
        // Arrange
        UUID connectorId = testConnector.getId();
        ERPConnector updated = ERPConnector.builder()
                .name("ADempiere Paint v2")
                .type(ERPConnector.ERPType.ADEMPIERE)
                .jdbcUrl("jdbc:postgresql://newhost:5432/adempiere")
                .build();

        when(erpConnectorRepository.findById(connectorId)).thenReturn(Optional.of(testConnector));
        when(erpConnectorRepository.save(any(ERPConnector.class))).thenReturn(testConnector);

        // Act
        ERPConnector result = erpConnectorService.updateConnector(connectorId, updated);

        // Assert
        assertNotNull(result);
        verify(erpConnectorRepository, times(1)).save(any(ERPConnector.class));
    }

    /**
     * Test deleteConnector removes from repository.
     */
    @Test
    void testDeleteConnector() {
        // Arrange
        UUID connectorId = testConnector.getId();
        doNothing().when(erpConnectorRepository).deleteById(connectorId);

        // Act
        erpConnectorService.deleteConnector(connectorId);

        // Assert
        verify(erpConnectorRepository, times(1)).deleteById(connectorId);
    }

    /**
     * Test getMaterializationHistory returns empty list.
     */
    @Test
    void testGetMaterializationHistoryReturnsEmptyList() {
        // Act
        List<MaterializationResult> history = erpConnectorService.getMaterializationHistory(testConnector.getId());

        // Assert
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }
}
