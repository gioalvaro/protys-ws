package ve.edu.uc.protys.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ve.edu.uc.protys.model.AlignmentRule;
import ve.edu.uc.protys.model.OntologyModule;
import ve.edu.uc.protys.model.AlignmentRule.RuleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for AlignmentRule entity.
 * Provides CRUD operations and custom query methods for alignment rule management.
 */
@Repository
public interface AlignmentRuleRepository extends JpaRepository<AlignmentRule, UUID> {

    /**
     * Find all active alignment rules.
     *
     * @return List of all active rules
     */
    List<AlignmentRule> findByActiveTrue();

    /**
     * Find an alignment rule by its rule ID.
     *
     * @param ruleId the unique rule identifier
     * @return Optional containing the rule if found
     */
    Optional<AlignmentRule> findByRuleId(String ruleId);

    /**
     * Find all alignment rules with a specific source module.
     *
     * @param sourceModule the source OntologyModule
     * @return List of rules with the specified source module
     */
    List<AlignmentRule> findBySourceModule(OntologyModule sourceModule);

    /**
     * Find all alignment rules with a specific target module.
     *
     * @param targetModule the target OntologyModule
     * @return List of rules with the specified target module
     */
    List<AlignmentRule> findByTargetModule(OntologyModule targetModule);

    /**
     * Find all alignment rules of a specific type.
     *
     * @param ruleType the rule type (BRIDGE, CORRESPONDENCE, INFERENCE)
     * @return List of rules of the specified type
     */
    List<AlignmentRule> findByRuleType(RuleType ruleType);

    /**
     * Count all active alignment rules.
     *
     * @return count of active rules
     */
    long countByActiveTrue();
}
