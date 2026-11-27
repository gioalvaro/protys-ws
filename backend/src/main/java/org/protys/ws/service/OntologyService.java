package org.protys.ws.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntDocumentManager;
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
import org.protys.ws.model.OntologyModule;
import org.protys.ws.model.AlignmentRule;
import org.protys.ws.model.ERPConnector.ConnectorStatus;
import org.protys.ws.dto.DashboardStats;
import org.protys.ws.dto.OntologyClassDTO;
import org.protys.ws.dto.OntologyIndividualDTO;
import org.protys.ws.dto.OntologyModuleDTO;
import org.protys.ws.exception.ProtysFusekiException;
import org.protys.ws.exception.ProtysMappingException;
import org.protys.ws.repository.OntologyModuleRepository;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.ERPConnectorRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
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
    private final ReasoningService reasoningService;
    private final AlignmentRuleRepository alignmentRuleRepository;
    private final ERPConnectorRepository erpConnectorRepository;

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
            // El lector se elige por la extensión (.ttl/.n3/.nt/.rdf/.owl/.xml); .owl y desconocidas, RDF/XML.
            Lang lang = moduleLanguage(file.getOriginalFilename());
            Model model = ModelFactory.createDefaultModel();
            try (java.io.InputStream input = file.getInputStream()) {
                RDFDataMgr.read(model, input, lang);
            } catch (org.apache.jena.riot.RiotException syntax) {
                throw new org.protys.ws.exception.ProtysValidationException(
                        "The file is not valid " + lang.getName() + ": " + syntax.getMessage(), syntax.getMessage());
            }

            log.debug("Parsed OWL file with {} triples", model.size());

            // Create module entity
            OntologyModule module = new OntologyModule();
            module.setId(UUID.randomUUID());
            module.setName(moduleName);
            module.setBaseUri("http://w3id.org/protys/ontology/" + moduleName);
            module.setNamedGraph("http://w3id.org/protys/ontology/" + moduleName + "#");
            module.setVersion("1.0");
            module.setTripleCount(model.size());
            module.setStatus(OntologyModule.ModuleStatus.LOADED);
            module.setLoadedAt(LocalDateTime.now());
            module.setUpdatedAt(LocalDateTime.now());

            // Load into Fuseki
            fusekiService.putModel(module.getNamedGraph(), model);

            // Persist module metadata
            ontologyModuleRepository.save(module);

            log.info("Successfully loaded module {} (ID: {}) with {} triples",
                    moduleName, module.getId(), model.size());

            return mapToDTO(module);
        } catch (org.protys.ws.exception.ProtysValidationException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read OWL file: {}", moduleName, e);
            throw new ProtysMappingException("Failed to load OWL file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to load ontology module: {}", moduleName, e);
            throw new ProtysMappingException("Failed to load module: " + e.getMessage(), e);
        }
    }

    static Lang moduleLanguage(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".ttl")) return Lang.TURTLE;
        if (name.endsWith(".n3")) return Lang.N3;
        if (name.endsWith(".nt")) return Lang.NTRIPLES;
        return Lang.RDFXML;
    }

    /**
     * Devuelve la jerarquía de clases de un módulo como lista de raíces.
     *
     * Raíz es toda clase nombrada del módulo sin superclase nombrada dentro del módulo: las
     * superclases anónimas (restricciones) y las de otros módulos no cuentan. Así el árbol no
     * depende de una única raíz ni pierde las clases definidas sólo por restricciones. Una clase con
     * varias superclases aparece bajo cada una; los ciclos de subClassOf se cortan, y las clases que
     * sólo forman parte de un ciclo se agregan como raíces para que ninguna quede afuera.
     *
     * @param moduleId the OntologyModule ID
     * @return raíces ordenadas por etiqueta (lista vacía si el módulo no tiene clases)
     */
    @Transactional(readOnly = true)
    public List<OntologyClassDTO> getClassHierarchy(UUID moduleId) {
        log.debug("Fetching class hierarchy for module: {}", moduleId);

        OntologyModule module = ontologyModuleRepository.findById(moduleId)
                .orElseThrow(() -> new org.protys.ws.exception.ModuleNotFoundException(moduleId));
        Model model = fusekiService.getModel(module.getNamedGraph());

        Set<Resource> classes = new HashSet<>();
        for (Resource type : List.of(org.apache.jena.vocabulary.OWL.Class, RDFS.Class)) {
            model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type, type)
                    .filterKeep(Resource::isURIResource).forEachRemaining(classes::add);
        }
        classes.remove(org.apache.jena.vocabulary.OWL.Thing);
        classes.remove(org.apache.jena.vocabulary.OWL.Nothing);

        Map<Resource, List<Resource>> children = new HashMap<>();
        List<Resource> roots = new ArrayList<>();
        for (Resource cls : classes) {
            boolean hasNamedParent = false;
            for (Resource parent : namedSuperClasses(model, cls)) {
                if (classes.contains(parent) && !parent.equals(cls)) {
                    children.computeIfAbsent(parent, k -> new ArrayList<>()).add(cls);
                    hasNamedParent = true;
                }
            }
            if (!hasNamedParent) roots.add(cls);
        }

        Comparator<Resource> byLabel = Comparator.comparing((Resource r) -> classLabel(model, r).toLowerCase())
                .thenComparing(Resource::getURI);
        roots.sort(byLabel);
        children.values().forEach(list -> list.sort(byLabel));

        Set<Resource> reached = new HashSet<>();
        List<OntologyClassDTO> result = new ArrayList<>();
        for (Resource root : roots) result.add(buildClassNode(model, root, children, new HashSet<>(), reached));
        // Clases que sólo forman parte de un ciclo: no tienen raíz alcanzable.
        List<Resource> unreached = classes.stream().filter(c -> !reached.contains(c)).sorted(byLabel).toList();
        for (Resource cls : unreached) {
            if (!reached.contains(cls)) result.add(buildClassNode(model, cls, children, new HashSet<>(), reached));
        }

        log.info("Class hierarchy built for module {}: {} classes, {} roots", moduleId, classes.size(), result.size());
        return result;
    }

    private List<Resource> namedSuperClasses(Model model, Resource cls) {
        List<Resource> parents = new ArrayList<>();
        model.listObjectsOfProperty(cls, RDFS.subClassOf).forEachRemaining(o -> {
            if (o.isURIResource()) parents.add(o.asResource());
        });
        return parents;
    }

    private String classLabel(Model model, Resource cls) {
        String fallback = null;
        for (String lang : List.of("", "es", "en")) {
            StmtIterator it = model.listStatements(cls, RDFS.label, (RDFNode) null);
            while (it.hasNext()) {
                RDFNode o = it.next().getObject();
                if (o.isLiteral()) {
                    if (o.asLiteral().getLanguage().equals(lang)) return o.asLiteral().getString();
                    if (fallback == null) fallback = o.asLiteral().getString();
                }
            }
        }
        if (fallback != null) return fallback;
        String local = cls.getLocalName();
        return local != null && !local.isBlank() ? local : cls.getURI();
    }

    /** Arma el nodo y sus subclases; {@code path} corta ciclos, {@code reached} registra lo visitado. */
    private OntologyClassDTO buildClassNode(Model model, Resource cls, Map<Resource, List<Resource>> children,
                                            Set<Resource> path, Set<Resource> reached) {
        reached.add(cls);
        path.add(cls);
        OntologyClassDTO dto = new OntologyClassDTO();
        dto.setUri(cls.getURI());
        dto.setLocalName(cls.getLocalName());
        dto.setLabel(classLabel(model, cls));
        Statement comment = cls.getProperty(RDFS.comment);
        if (comment != null && comment.getObject().isLiteral()) dto.setDescription(comment.getString());
        dto.setSuperClassUris(namedSuperClasses(model, cls).stream().map(Resource::getURI).sorted().toList());
        dto.setPropertyCount(model.listSubjectsWithProperty(RDFS.domain, cls).toList().size());
        List<OntologyClassDTO> subclasses = new ArrayList<>();
        for (Resource child : children.getOrDefault(cls, List.of())) {
            if (!path.contains(child)) subclasses.add(buildClassNode(model, child, children, path, reached));
        }
        dto.setSubclasses(subclasses);
        path.remove(cls);
        return dto;
    }

    /**
     * Validates ontology consistency using reasoning.
     *
     * @param moduleId the OntologyModule ID
     * @return true if consistent, false if inconsistencies found
     */
    @Transactional(readOnly = true)
    public ReasoningService.ClassificationResult validateDetailed(UUID moduleId) {
        OntologyModule module=ontologyModuleRepository.findById(moduleId).orElseThrow(()->new org.protys.ws.exception.ModuleNotFoundException(moduleId));
        Model model=fusekiService.getModel(module.getNamedGraph());
        if (model.isEmpty() && module.getTripleCount()!=null && module.getTripleCount()>0) return ReasoningService.ClassificationResult.builder().consistent(false).validationStatus("NOT_EVALUATED").errorMessage("Registered module graph is missing or empty").deductions(ModelFactory.createDefaultModel()).build();
        return reasoningService.classify(model);
    }
    public boolean validateConsistency(UUID moduleId) {
        ReasoningService.ClassificationResult result=validateDetailed(moduleId);
        if(result.getValidationStatus().equals("NOT_EVALUATED"))throw new ProtysMappingException(result.getErrorMessage());
        return result.isConsistent();
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

        Model registeredUnion = ModelFactory.createDefaultModel();
        LocalDateTime lastActivity = null;
        List<DashboardStats.ModuleStatEntry> moduleStatEntries = new ArrayList<>();

        for (OntologyModule module : modules) {
            Model model = fusekiService.getModel(module.getNamedGraph());
            registeredUnion.add(model);
            OntModel ontModel = registeredOntologyView(model);

            long classCount = ontModel.listNamedClasses().toList().size();
            long individualCount = namedIndividualCount(ontModel);
            lastActivity = latest(lastActivity, module.getLoadedAt(), module.getUpdatedAt());

            // Build module stat entry
            DashboardStats.ModuleStatEntry entry = DashboardStats.ModuleStatEntry.builder()
                    .moduleName(module.getName())
                    .baseUri(module.getNamedGraph())
                    .status(module.getStatus() != null ? module.getStatus().name() : null)
                    .classCount(Math.toIntExact(classCount))
                    .individualCount(Math.toIntExact(individualCount))
                    .tripleCount(model.size())
                    .loadedAt(module.getLoadedAt())
                    .build();
            moduleStatEntries.add(entry);
        }

        for (AlignmentRule rule : alignmentRuleRepository.findAll()) {
            lastActivity = latest(lastActivity, rule.getCreatedAt(), rule.getUpdatedAt(),
                    rule.getLastExecutedAt());
        }
        // Shared IRIs/triples in overlapping modules are counted once in totals.
        OntModel unionOntology = registeredOntologyView(registeredUnion);
        long totalTriples = registeredUnion.size();
        long totalClasses = unionOntology.listNamedClasses().toList().size();
        long totalIndividuals = namedIndividualCount(unionOntology);
        DashboardStats stats = DashboardStats.builder()
                .totalModules(modules.size())
                .totalTriples(totalTriples)
                .totalClasses(totalClasses)
                .totalIndividuals(totalIndividuals)
                .activeAlignmentRules(Math.toIntExact(alignmentRuleRepository.countByActiveTrue()))
                // No persisted inference-run ledger exists for this dashboard.
                .totalInferences(null)
                .connectedERPs(Math.toIntExact(erpConnectorRepository.countByStatusIn(
                        List.of(ConnectorStatus.CONNECTED, ConnectorStatus.MATERIALIZED))))
                .lastActivity(lastActivity)
                .moduleStats(moduleStatEntries)
                .build();

        log.debug("Dashboard stats: modules={}, triples={}, classes={}, individuals={}",
                stats.getTotalModules(), totalTriples, totalClasses, totalIndividuals);

        return stats;
    }

    /**
     * Count named ontology instances rather than every rdf:type subject. Schema
     * resources (including classes/properties with additional types) are excluded.
     * This inventory concerns asserted registered modules, not the inference graph.
     */
    private long namedIndividualCount(OntModel model) {
        Set<String> schemaResources = new HashSet<>();
        model.listNamedClasses().forEachRemaining(resource -> schemaResources.add(resource.getURI()));
        model.listAllOntProperties().filterDrop(Resource::isAnon)
                .forEachRemaining(resource -> schemaResources.add(resource.getURI()));
        model.listOntologies().filterDrop(Resource::isAnon)
                .forEachRemaining(resource -> schemaResources.add(resource.getURI()));
        Set<String> individuals = new HashSet<>();
        model.listIndividuals().filterDrop(Resource::isAnon)
                .filterDrop(resource -> schemaResources.contains(resource.getURI()))
                .forEachRemaining(resource -> individuals.add(resource.getURI()));
        // OWL_MEM has an OWL1 profile; retain explicit OWL2 NamedIndividual declarations.
        model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"))
                .filterDrop(Resource::isAnon)
                .filterDrop(resource -> schemaResources.contains(resource.getURI()))
                .forEachRemaining(resource -> individuals.add(resource.getURI()));
        return individuals.size();
    }

    /** Inventory only the graphs retrieved above; imports are not fetched here. */
    private OntModel registeredOntologyView(Model assertedModel) {
        OntDocumentManager documents = new OntDocumentManager();
        documents.setProcessImports(false);
        OntModelSpec specification = new OntModelSpec(OntModelSpec.OWL_MEM);
        specification.setDocumentManager(documents);
        return ModelFactory.createOntologyModel(specification, assertedModel);
    }

    private LocalDateTime latest(LocalDateTime current, LocalDateTime... dates) {
        for (LocalDateTime date : dates) {
            if (date != null && (current == null || date.isAfter(current))) {
                current = date;
            }
        }
        return current;
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
