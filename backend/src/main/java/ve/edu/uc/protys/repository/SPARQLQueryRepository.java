package ve.edu.uc.protys.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ve.edu.uc.protys.model.SPARQLQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for SPARQLQuery entity.
 * Provides CRUD operations and custom query methods for SPARQL query template management.
 */
@Repository
public interface SPARQLQueryRepository extends JpaRepository<SPARQLQuery, UUID> {

    /**
     * Find all SPARQL query templates.
     *
     * @return List of all query templates
     */
    List<SPARQLQuery> findByIsTemplateTrue();

    /**
     * Find all SPARQL queries that are associated with competency questions.
     *
     * @return List of queries with non-null competency questions
     */
    List<SPARQLQuery> findByCompetencyQuestionIsNotNull();

    /**
     * Find all SPARQL queries in a specific category.
     *
     * @param category the query category
     * @return List of queries in the specified category
     */
    List<SPARQLQuery> findByCategory(String category);

    /**
     * Find a SPARQL query by its associated competency question.
     *
     * @param competencyQuestion the competency question text
     * @return Optional containing the query if found
     */
    Optional<SPARQLQuery> findByCompetencyQuestion(String competencyQuestion);

    /**
     * Search for SPARQL queries by name (case-insensitive).
     *
     * @param search the search text to match against query names
     * @return List of queries whose names contain the search text
     */
    List<SPARQLQuery> findByNameContainingIgnoreCase(String search);
}
