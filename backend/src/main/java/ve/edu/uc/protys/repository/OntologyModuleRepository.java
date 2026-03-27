package ve.edu.uc.protys.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ve.edu.uc.protys.model.OntologyModule;
import ve.edu.uc.protys.model.OntologyModule.ModuleStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for OntologyModule entity.
 * Provides CRUD operations and custom query methods for ontology module management.
 */
@Repository
public interface OntologyModuleRepository extends JpaRepository<OntologyModule, UUID> {

    /**
     * Find an ontology module by its name.
     *
     * @param name the module name
     * @return Optional containing the module if found
     */
    Optional<OntologyModule> findByName(String name);

    /**
     * Find an ontology module by its named graph URI.
     *
     * @param namedGraph the named graph URI
     * @return Optional containing the module if found
     */
    Optional<OntologyModule> findByNamedGraph(String namedGraph);

    /**
     * Find all ontology modules with a specific status.
     *
     * @param status the module status (LOADED, VALIDATED, ERROR)
     * @return List of modules with the specified status
     */
    List<OntologyModule> findByStatus(ModuleStatus status);

    /**
     * Count ontology modules by status.
     *
     * @param status the module status
     * @return count of modules with the specified status
     */
    long countByStatus(ModuleStatus status);

    /**
     * Check if an ontology module with the given base URI already exists.
     *
     * @param baseUri the base URI to check
     * @return true if a module with this base URI exists, false otherwise
     */
    boolean existsByBaseUri(String baseUri);
}
