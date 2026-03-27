package ve.edu.uc.protys.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ve.edu.uc.protys.model.SPARQLQuery;
import ve.edu.uc.protys.model.SPARQLQuery.QueryType;
import ve.edu.uc.protys.repository.SPARQLQueryRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data initializer component that runs on application startup.
 * Populates the database with predefined SPARQL query templates if they don't exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SPARQLQueryRepository sparqlQueryRepository;

    /**
     * Initializes SPARQL query templates on application startup.
     * Inserts competency question templates and utility queries if the table is empty.
     *
     * @param args command line arguments (unused)
     */
    @Override
    public void run(String... args) {
        log.info("Starting SPARQL query template initialization...");

        // Check if templates already exist
        long existingCount = sparqlQueryRepository.count();
        if (existingCount > 0) {
            log.info("SPARQL query templates already exist (count: {}). Skipping initialization.", existingCount);
            return;
        }

        int initializedCount = 0;

        // CQ1: Materiales y propiedades ambientales (Materials and environmental properties)
        SPARQLQuery cq1 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("CQ1 - Materiales y propiedades ambientales ISO 14040")
                .description("Finds materials used in a process with their ISO 14040 environmental properties")
                .category("Environmental")
                .competencyQuestion("Materiales y propiedades ambientales")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX protys: <http://protys.ingar.conicet.gob.ar/ontology#>
                        PREFIX iso14040: <http://protys.ingar.conicet.gob.ar/iso14040#>

                        SELECT ?material ?materialLabel ?carbonFootprint ?waterFootprint ?VOCContent
                        WHERE {
                            ?process protys:useMaterial ?material ;
                                    protys:label ?processLabel .
                            FILTER(CONTAINS(LCASE(?processLabel), LCASE(?param)))
                            ?material protys:label ?materialLabel ;
                                     iso14040:carbonFootprint ?carbonFootprint ;
                                     iso14040:waterFootprint ?waterFootprint ;
                                     iso14040:VOCContent ?VOCContent .
                        }
                        ORDER BY DESC(?carbonFootprint)
                        """)
                .build();
        sparqlQueryRepository.save(cq1);
        initializedCount++;
        log.debug("Initialized CQ1: Materials and environmental properties");

        // CQ2: Equipos y capacidades ISO 15531 (Equipment and capabilities)
        SPARQLQuery cq2 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("CQ2 - Equipos y capacidades ISO 15531")
                .description("Finds equipment for a process with ISO 15531 capabilities")
                .category("Resources")
                .competencyQuestion("Equipos y capacidades ISO 15531")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX protys: <http://protys.ingar.conicet.gob.ar/ontology#>
                        PREFIX iso15531: <http://protys.ingar.conicet.gob.ar/iso15531#>

                        SELECT ?equipment ?equipmentLabel ?energyConsumption ?processingCapacity ?availability ?oee
                        WHERE {
                            ?process protys:useEquipment ?equipment ;
                                    protys:label ?processLabel .
                            FILTER(CONTAINS(LCASE(?processLabel), LCASE(?param)))
                            ?equipment protys:label ?equipmentLabel ;
                                      iso15531:energyConsumption ?energyConsumption ;
                                      iso15531:processingCapacity ?processingCapacity ;
                                      iso15531:availability ?availability ;
                                      iso15531:OEE ?oee .
                        }
                        ORDER BY DESC(?oee)
                        """)
                .build();
        sparqlQueryRepository.save(cq2);
        initializedCount++;
        log.debug("Initialized CQ2: Equipment and capabilities");

        // CQ3: Trazabilidad producto-proceso-recurso (Product-Process-Resource traceability)
        SPARQLQuery cq3 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("CQ3 - Trazabilidad producto-proceso-recurso")
                .description("Traces products through processes to resources in the complete value chain")
                .category("Traceability")
                .competencyQuestion("Trazabilidad producto-proceso-recurso")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX protys: <http://protys.ingar.conicet.gob.ar/ontology#>

                        SELECT ?product ?productLabel ?process ?processLabel ?resource ?resourceLabel
                        WHERE {
                            ?product protys:isProducedBy ?process ;
                                    protys:label ?productLabel .
                            ?process protys:label ?processLabel ;
                                    protys:requiresResource ?resource .
                            ?resource protys:label ?resourceLabel .
                        }
                        ORDER BY ?productLabel ?processLabel
                        """)
                .build();
        sparqlQueryRepository.save(cq3);
        initializedCount++;
        log.debug("Initialized CQ3: Product-Process-Resource traceability");

        // CQ4: Impacto ambiental ciclo de vida ISO 14040 (Life cycle environmental impact)
        SPARQLQuery cq4 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("CQ4 - Impacto ambiental ciclo de vida ISO 14040")
                .description("Aggregates environmental impact categories across product lifecycle stages")
                .category("Environmental")
                .competencyQuestion("Impacto ambiental ciclo de vida ISO 14040")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX protys: <http://protys.ingar.conicet.gob.ar/ontology#>
                        PREFIX iso14040: <http://protys.ingar.conicet.gob.ar/iso14040#>

                        SELECT ?stage ?stageLabel (SUM(?impact) as ?totalImpact)
                        WHERE {
                            ?product protys:label ?productLabel ;
                                    protys:hasLifecycleStage ?stage .
                            FILTER(CONTAINS(LCASE(?productLabel), LCASE(?param)))
                            ?stage protys:label ?stageLabel ;
                                  iso14040:hasEnvironmentalImpact ?impact .
                        }
                        GROUP BY ?stage ?stageLabel
                        ORDER BY DESC(?totalImpact)
                        """)
                .build();
        sparqlQueryRepository.save(cq4);
        initializedCount++;
        log.debug("Initialized CQ4: Life cycle environmental impact");

        // CQ5: Recursos organizacionales disponibles ISO 15531 (Organizational resources)
        SPARQLQuery cq5 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("CQ5 - Recursos organizacionales disponibles ISO 15531")
                .description("Finds available resources for a process plan with skills and certifications")
                .category("Resources")
                .competencyQuestion("Recursos organizacionales disponibles ISO 15531")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX protys: <http://protys.ingar.conicet.gob.ar/ontology#>
                        PREFIX iso15531: <http://protys.ingar.conicet.gob.ar/iso15531#>

                        SELECT ?resource ?resourceLabel ?skill ?certification
                        WHERE {
                            ?plan protys:label ?planLabel ;
                                 protys:requiresResource ?resource .
                            FILTER(CONTAINS(LCASE(?planLabel), LCASE(?param)))
                            ?resource protys:label ?resourceLabel ;
                                     iso15531:hasSkill ?skill ;
                                     iso15531:hasCertification ?certification ;
                                     iso15531:isAvailable true .
                        }
                        ORDER BY ?resourceLabel
                        """)
                .build();
        sparqlQueryRepository.save(cq5);
        initializedCount++;
        log.debug("Initialized CQ5: Organizational resources");

        // Utility template 1: Count all triples
        SPARQLQuery utility1 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("Utility - Count all triples")
                .description("Counts the total number of triples in the triplestore")
                .category("Utility")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        SELECT (COUNT(*) as ?tripleCount)
                        WHERE {
                            ?s ?p ?o .
                        }
                        """)
                .build();
        sparqlQueryRepository.save(utility1);
        initializedCount++;
        log.debug("Initialized Utility 1: Count all triples");

        // Utility template 2: List all named graphs
        SPARQLQuery utility2 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("Utility - List all named graphs")
                .description("Lists all named graphs in the triplestore")
                .category("Utility")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        SELECT DISTINCT ?g
                        WHERE {
                            GRAPH ?g {
                                ?s ?p ?o .
                            }
                        }
                        ORDER BY ?g
                        """)
                .build();
        sparqlQueryRepository.save(utility2);
        initializedCount++;
        log.debug("Initialized Utility 2: List all named graphs");

        // Utility template 3: List all classes
        SPARQLQuery utility3 = SPARQLQuery.builder()
                .id(UUID.randomUUID())
                .name("Utility - List all classes")
                .description("Lists all RDF classes in the triplestore")
                .category("Utility")
                .isTemplate(true)
                .queryType(QueryType.SELECT)
                .executionCount(0)
                .queryText("""
                        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                        SELECT DISTINCT ?class ?label
                        WHERE {
                            ?class rdf:type rdfs:Class .
                            OPTIONAL {
                                ?class rdfs:label ?label .
                            }
                        }
                        ORDER BY ?class
                        """)
                .build();
        sparqlQueryRepository.save(utility3);
        initializedCount++;
        log.debug("Initialized Utility 3: List all classes");

        log.info("SPARQL query template initialization completed. {} templates initialized.", initializedCount);
    }
}
