package ve.edu.uc.protys.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ve.edu.uc.protys.model.ERPConnector;
import ve.edu.uc.protys.model.ERPConnector.ConnectorStatus;
import ve.edu.uc.protys.model.ERPConnector.ERPType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for ERPConnector entity.
 * Provides CRUD operations and custom query methods for ERP connector management.
 */
@Repository
public interface ERPConnectorRepository extends JpaRepository<ERPConnector, UUID> {

    /**
     * Find all active ERP connectors.
     *
     * @return List of all active connectors
     */
    List<ERPConnector> findByActiveTrue();

    /**
     * Find all ERP connectors of a specific type.
     *
     * @param erpType the ERP type (ADEMPIERE, ODOO, CUSTOM)
     * @return List of connectors of the specified type
     */
    List<ERPConnector> findByType(ERPType erpType);

    /**
     * Find all ERP connectors with a specific status.
     *
     * @param status the connector status (REGISTERED, CONNECTED, MATERIALIZED, ERROR)
     * @return List of connectors with the specified status
     */
    List<ERPConnector> findByStatus(ConnectorStatus status);

    /**
     * Find an ERP connector by its named graph URI.
     *
     * @param namedGraph the named graph URI
     * @return Optional containing the connector if found
     */
    Optional<ERPConnector> findByNamedGraph(String namedGraph);

    /**
     * Count all active ERP connectors.
     *
     * @return count of active connectors
     */
    long countByActiveTrue();
}
