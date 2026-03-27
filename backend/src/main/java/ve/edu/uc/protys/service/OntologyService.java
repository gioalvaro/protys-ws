package ve.edu.uc.protys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ve.edu.uc.protys.model.OntologyModule;
import ve.edu.uc.protys.dto.DashboardStats;
import ve.edu.uc.protys.dto.OntologyClassDTO;
import ve.edu.uc.protys.dto.OntologyIndividualDTO;
import ve.edu.uc.protys.dto.OntologyModuleDTO;
import ve.edu.uc.protys.exception.ProtysFusekiException;
import ve.edu.uc.protys.exception.ProtysMappingException;
import ve.edu.uc.protys.repository.OntologyModuleRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main ontology management service.
 * Handles OWL module loading, class/individual management, and consistency validation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OntologyService {

    private final FusekiService fusekiService;
    private final OntologyModuleRepository ontologyModuleRepository;

    /**
     * Loads an OWL ontology module from an uploaded file.
     *
     * @param file       the MultipartFile containing OWL content
     * @param moduleName the name for this module
     * @return OntologyModuleDTO with module metadata
     * @throws ProtysMappingException if parsing or loading fails
     */
    @Transactional
    public OntologyModuleDTO loadModule(MultipartFile file, String moduleName) {
        log.info("Loading ontology module: {}", moduleName);

        try {
            // Parse OWL file
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, file.getInputStream(), Lang.RDFXML);

            log.debug("Parsed OWL file with {} triples", model.size());

            // Create module entity
            OntologyModule module = new OntologyModule();
            module.setId(UUID.randomUUID());
            module.setName(moduleName);
            module.setBaseUri("http://protys.ontology/" + moduleName);
            module.setNamedGraph("http://protys.ontology/" + moduleName + "#");
            module.setVersion("1.0");
            module.setTripleCount(model.size());
            module.setStatus(OntologyModule.ModuleStatus.LOADED);
            module.setLoadedAt(LocalDateTime.now());
            module.setUpdatedAt(LocalDateTime.now());

            // Load into Fuseki
            fusekiService.loadModel(module.getNamedGraph(), file.getInputStream());

            // Persist module metadata
            ontologyModuleRepository.save(module);

            log.info("Successfully loaded module {} (ID: {}) with {} triples",
                    moduleName, module.getId(), model.size());

            return mapToDTO(module);
        } catch (IOException e) {
            log.error("Failed to read OWL file: {}", moduleName, e);
            throw new ProtysMappingException("Failed to load OWL file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to load ontology module: {}", moduleName, e);
            throw new ProtysMappingException("Failed to load module: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the class hierarchy for a module.
     *
     * @param moduleId the OntologyModule ID
     * @return OntologyClassDTO tree root
     * @throws ProtysFusekiException if retrieval fails
     */
    @Transactional(readOnly = true)
    public OntologyClassDTO getClassHierarchy(UUID moduleId) {
        log.debug("Fetching class hierarchy for module: {}", moduleId);

        OntologyModule module = ontologyModuleRepository.findById(moduleId)
                .orElseThrow(() -> new ProtysMappingException("Module not found: " + moduleId));

        Model model = fusekiService.getModel(module.getNamedGraph());
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);

        // Find root classes (no superclass)
        List<OntClass> rootClasses = ontModel.listNamedClasses()
                .filterDrop(OntClass::isAnon)
                .filter(cls -> !cls.listSuperClasses().hasNext())
                .toList();

        log.debug("Found {} root classes", rootClasses.size());

        if (rootClasses.isEmpty()) {
            return null;
        }

        // Build hierarchy from first root
        OntologyClassDTO root = buildClassHierarchy(rootClasses.get(0), ontModel);
        log.info("Class hierarchy built for module: {}", moduleId);

        return root;
    }

    /**
     * Recursively builds class hierarchy DTO from OntClass.
     */
    private OntologyClassDTO buildClassHierarchy(OntClass ontClass, OntModel ontModel) {
        OntologyClassDTO dto = new OntologyClassDTO();
        dto.setUri(ontClass.getURI());
        dto.setLocalName(ontClass.getLocalName());
        dto.setLabel(ontClass.getLabel() != null ? ontClass.getLabel("en") : ontClass.getLocalName());

        List<OntologyClassDTO> subclasses = new ArrayList<>();
        ontClass.listSubClasses()
                .filterDrop(OntClass::isAnon)
                .forEach(subclass -> {
                    OntologyClassDTO subDto = buildClassHierarchy(subclass, ontModel);
                    subclasses.add(subDto);
                });

        dto.setSubclasses(subclasses);
        dto.setPropertyCount((int) ontClass.listDeclaredProperties().toList().size());

        return dto;
    }

    /**
     * Retrieves a specific individual with all its properties.
     *
     * @param uri the individual URI
     * @return OntologyIndividualDTO
     * @throws ProtysFusekiException if retrieval fails
     */
    @Transactional(readOnly = true)
    public OntologyIndividualDTO getIndividual(String uri) {
        log.debug("Fetching individual: {}", uri);

        // Query all modules to find the individual
        List<OntologyModule> modules = ontologyModuleRepository.findAll();
        for (OntologyModule module : modules) {
            Model model = fusekiService.getModel(module.getNamedGraph());
            Resource resource = model.getResource(uri);

            if (resource.hasProperty(org.apache.jena.vocabulary.RDF.type)) {
                OntologyIndividualDTO dto = new OntologyIndividualDTO();
                dto.setUri(uri);
                dto.setLocalName(resource.getLocalName());

                // Get rdf:type
                StmtIterator typeIter = resource.listProperties(org.apache.jena.vocabulary.RDF.type);
                if (typeIter.hasNext()) {
                    RDFNode typeNode = typeIter.nextStatement().getObject();
                    dto.setType(typeNode.asResource().getURI());
                }

                // Get all other properties
                Map<String, List<String>> properties = new HashMap<>();
                StmtIterator iter = resource.listProperties();
                while (iter.hasNext()) {
                    Statement stmt = iter.nextStatement();
                    String predicate = stmt.getPredicate().getURI();
                    String value = stmt.getObject().isResource() ?
                            stmt.getObject().asResource().getURI() :
                            stmt.getObject().asLiteral().getString();

                    properties.computeIfAbsent(predicate, k -> new ArrayList<>()).add(value);
                }
                dto.setProperties(properties);

                log.debug("Retrieved individual: {}", uri);
                return dto;
            }
        }

        log.warn("Individual not found: {}", uri);
        throw new ProtysFusekiException("Individual not found: " + uri);
    }

    /**
     * Creates a new individual instance.
     *
     * @param classUri   the class URI for the new individual
     * @param properties Map of property URIs to values
     * @return the URI of the created individual
     * @throws ProtysFusekiException if creation fails
     */
    @Transactional
    public String createIndividual(String classUri, Map<String, String> properties) {
        log.info("Creating new individual of class: {}", classUri);

        String individualUri = "http://protys.instances/" + UUID.randomUUID();

        try {
            // Find module containing the class
            List<OntologyModule> modules = ontologyModuleRepository.findAll();
            OntologyModule targetModule = null;

            for (OntologyModule module : modules) {
                Model model = fusekiService.getModel(module.getNamedGraph());
                if (model.getResource(classUri) != null) {
                    targetModule = module;
                    break;
                }
            }

            if (targetModule == null) {
                throw new ProtysFusekiException("Class not found in any module: " + classUri);
            }

            Model model = fusekiService.getModel(targetModule.getNamedGraph());
            Resource individual = model.createResource(individualUri);
            Resource classResource = model.getResource(classUri);

            // Set rdf:type
            individual.addProperty(org.apache.jena.vocabulary.RDF.type, classResource);

            // Add properties
            for (Map.Entry<String, String> prop : properties.entrySet()) {
                Property property = model.getProperty(prop.getKey());
                individual.addProperty(property, prop.getValue());
            }

            // Store updated model
            fusekiService.putModel(targetModule.getNamedGraph(), model);

            log.info("Created individual: {}", individualUri);
            return individualUri;
        } catch (Exception e) {
            log.error("Failed to create individual", e);
            throw new ProtysFusekiException("Failed to create individual: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing individual's properties.
     *
     * @param uri        the individual URI
     * @param properties Map of property URIs to new values
     * @throws ProtysFusekiException if update fails
     */
    @Transactional
    public void updateIndividual(String uri, Map<String, String> properties) {
        log.info("Updating individual: {}", uri);

        try {
            List<OntologyModule> modules = ontologyModuleRepository.findAll();

            for (OntologyModule module : modules) {
                Model model = fusekiService.getModel(module.getNamedGraph());
                Resource resource = model.getResource(uri);

                if (resource.hasProperty(org.apache.jena.vocabulary.RDF.type)) {
                    // Remove old properties (except rdf:type)
                    StmtIterator iter = resource.listProperties();
                    List<Statement> toRemove = new ArrayList<>();
                    while (iter.hasNext()) {
                        Statement stmt = iter.nextStatement();
                        if (!stmt.getPredicate().equals(org.apache.jena.vocabulary.RDF.type)) {
                            toRemove.add(stmt);
                        }
                    }
                    toRemove.forEach(model::remove);

                    // Add new properties
                    for (Map.Entry<String, String> prop : properties.entrySet()) {
                        Property property = model.getProperty(prop.getKey());
                        resource.addProperty(property, prop.getValue());
                    }

                    fusekiService.putModel(module.getNamedGraph(), model);
                    log.info("Successfully updated individual: {}", uri);
                    return;
                }
            }

            throw new ProtysFusekiException("Individual not found: " + uri);
        } catch (Exception e) {
            log.error("Failed to update individual: {}", uri, e);
            throw new ProtysFusekiException("Failed to update individual: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes an individual from the knowledge base.
     *
     * @param uri the individual URI
     * @throws ProtysFusekiException if deletion fails
     */
    @Transactional
    public void deleteIndividual(String uri) {
        log.info("Deleting individual: {}", uri);

        try {
            List<OntologyModule> modules = ontologyModuleRepository.findAll();

            for (OntologyModule module : modules) {
                Model model = fusekiService.getModel(module.getNamedGraph());
                Resource resource = model.getResource(uri);

                if (resource.hasProperty(org.apache.jena.vocabulary.RDF.type)) {
                    model.removeAll(resource, null, null);
                    model.removeAll(null, null, resource);
                    fusekiService.putModel(module.getNamedGraph(), model);
                    log.info("Successfully deleted individual: {}", uri);
                    return;
                }
            }

            throw new ProtysFusekiException("Individual not found: " + uri);
        } catch (Exception e) {
            log.error("Failed to delete individual: {}", uri, e);
            throw new ProtysFusekiException("Failed to delete individual: " + e.getMessage(), e);
        }
    }

    /**
     * Validates ontology consistency using reasoning.
     *
     * @param moduleId the OntologyModule ID
     * @return true if consistent, false if inconsistencies found
     */
    @Transactional(readOnly = true)
    public boolean validateConsistency(UUID moduleId) {
        log.info("Validating consistency for module: {}", moduleId);

        OntologyModule module = ontologyModuleRepository.findById(moduleId)
                .orElseThrow(() -> new ProtysMappingException("Module not found: " + moduleId));

        Model model = fusekiService.getModel(module.getNamedGraph());
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);

        try {
            // Basic consistency check: no contradictory declarations
            List<OntClass> classes = ontModel.listNamedClasses().toList();
            for (OntClass cls : classes) {
                List<OntClass> superClasses = cls.listSuperClasses().toList();
                for (OntClass superClass : superClasses) {
                    if (superClasses.contains(cls)) {
                        log.warn("Circular subclass relationship detected: {}", cls.getURI());
                        return false;
                    }
                }
            }

            log.info("Consistency validation passed for module: {}", moduleId);
            return true;
        } catch (Exception e) {
            log.error("Consistency validation failed for module: {}", moduleId, e);
            return false;
        }
    }

    /**
     * Retrieves all loaded modules.
     *
     * @return List of OntologyModuleDTO
     */
    @Transactional(readOnly = true)
    public List<OntologyModuleDTO> getAllModules() {
        log.debug("Fetching all ontology modules");

        List<OntologyModule> modules = ontologyModuleRepository.findAll();
        return modules.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves aggregated dashboard statistics.
     *
     * @return DashboardStats DTO
     */
    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats() {
        log.debug("Computing dashboard statistics");

        List<OntologyModule> modules = ontologyModuleRepository.findAll();

        long totalTriples = 0;
        long totalClasses = 0;
        long totalIndividuals = 0;
        List<DashboardStats.ModuleStatEntry> moduleStatEntries = new ArrayList<>();

        for (OntologyModule module : modules) {
            totalTriples += module.getTripleCount();

            Model model = fusekiService.getModel(module.getNamedGraph());
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);

            long classCount = ontModel.listNamedClasses().toList().size();
            totalClasses += classCount;

            // Count individuals (resources with rdf:type)
            long individualCount = model.listResourcesWithProperty(
                    org.apache.jena.vocabulary.RDF.type).toList().stream()
                    .filter(r -> !r.isAnon())
                    .count();
            totalIndividuals += individualCount;

            // Build module stat entry
            DashboardStats.ModuleStatEntry entry = DashboardStats.ModuleStatEntry.builder()
                    .moduleName(module.getName())
                    .baseUri(module.getNamedGraph())
                    .status("LOADED")
                    .classCount((int) classCount)
                    .individualCount((int) individualCount)
                    .tripleCount((long) module.getTripleCount())
                    .loadedAt(module.getLoadedAt())
                    .build();
            moduleStatEntries.add(entry);
        }

        DashboardStats stats = DashboardStats.builder()
                .totalModules(modules.size())
                .totalTriples(totalTriples)
                .totalClasses(totalClasses)
                .totalIndividuals(totalIndividuals)
                .activeAlignmentRules(0)
                .totalInferences(0L)
                .connectedERPs(0)
                .lastActivity(LocalDateTime.now())
                .moduleStats(moduleStatEntries)
                .build();

        log.debug("Dashboard stats: modules={}, triples={}, classes={}, individuals={}",
                stats.getTotalModules(), totalTriples, totalClasses, totalIndividuals);

        return stats;
    }

    /**
     * Maps OntologyModule entity to DTO.
     */
    private OntologyModuleDTO mapToDTO(OntologyModule module) {
        OntologyModuleDTO dto = new OntologyModuleDTO();
        dto.setId(module.getId());
        dto.setName(module.getName());
        dto.setDescription(module.getDescription());
        dto.setBaseUri(module.getBaseUri());
        dto.setNamedGraph(module.getNamedGraph());
        dto.setClassCount(module.getClassCount());
        dto.setIndividualCount(module.getIndividualCount());
        dto.setTripleCount(module.getTripleCount());
        dto.setStatus(module.getStatus() != null ? module.getStatus().name() : null);
        dto.setCreatedAt(module.getLoadedAt());
        dto.setUpdatedAt(module.getUpdatedAt());
        return dto;
    }
}
