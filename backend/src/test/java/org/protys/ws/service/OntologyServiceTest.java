package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.protys.ws.dto.DashboardStats;
import org.protys.ws.dto.OntologyClassDTO;
import org.protys.ws.dto.OntologyModuleDTO;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.model.AlignmentRule;
import org.protys.ws.model.ERPConnector.ConnectorStatus;
import org.protys.ws.repository.OntologyModuleRepository;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.ERPConnectorRepository;

@ExtendWith(MockitoExtension.class)
class OntologyServiceTest {

    @Mock
    private FusekiService fusekiService;

    @Mock
    private OntologyModuleRepository ontologyModuleRepository;

    @Mock
    private ReasoningService reasoningService;

    @Mock
    private AlignmentRuleRepository alignmentRuleRepository;

    @Mock
    private ERPConnectorRepository erpConnectorRepository;

    @InjectMocks
    private OntologyService ontologyService;

    private Model mockModel;

    @BeforeEach
    void setUp() {
        mockModel = ModelFactory.createDefaultModel();
        mockModel.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        mockModel.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
    }

    /**
     * Test getAllModules returns mapped DTOs.
     */
    @Test
    void testGetAllModulesReturnsMappedDTOs() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        OntologyModule mod1 = new OntologyModule();
        mod1.setId(id1);
        mod1.setName("CoreConcepts");
        mod1.setNamedGraph("http://w3id.org/protys/ontology/CoreConcepts#");
        mod1.setTripleCount(96L);
        mod1.setLoadedAt(LocalDateTime.now());
        mod1.setUpdatedAt(LocalDateTime.now());

        OntologyModule mod2 = new OntologyModule();
        mod2.setId(id2);
        mod2.setName("ProductMod");
        mod2.setNamedGraph("http://w3id.org/protys/ontology/ProductMod#");
        mod2.setTripleCount(103L);
        mod2.setLoadedAt(LocalDateTime.now());
        mod2.setUpdatedAt(LocalDateTime.now());

        when(ontologyModuleRepository.findAll()).thenReturn(List.of(mod1, mod2));

        // Act
        List<OntologyModuleDTO> result = ontologyService.getAllModules();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("CoreConcepts", result.get(0).getName());
        assertEquals("ProductMod", result.get(1).getName());

        verify(ontologyModuleRepository, times(1)).findAll();
    }

    /**
     * Test getClassHierarchy returns tree structure from Fuseki model.
     */
    @Test
    void testGetClassHierarchyReturnsTree() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        OntologyModule module = new OntologyModule();
        module.setId(moduleId);
        module.setName("CoreConcepts");
        module.setNamedGraph("http://w3id.org/protys/ontology/CoreConcepts#");

        when(ontologyModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        // Create OWL model with class hierarchy
        Model owlModel = ModelFactory.createDefaultModel();
        Resource parentClass = owlModel.createResource("http://w3id.org/protys/ontology/ManufacturingEntity");
        Resource childClass = owlModel.createResource("http://w3id.org/protys/ontology/Product");
        owlModel.add(parentClass, RDF.type, OWL.Class);
        owlModel.add(childClass, RDF.type, OWL.Class);
        owlModel.add(childClass, RDFS.subClassOf, parentClass);

        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(owlModel);

        // Act
        List<OntologyClassDTO> hierarchy = ontologyService.getClassHierarchy(moduleId);

        // Assert
        assertEquals(1, hierarchy.size());
        assertEquals("http://w3id.org/protys/ontology/ManufacturingEntity", hierarchy.get(0).getUri());
        assertEquals(List.of("http://w3id.org/protys/ontology/Product"),
                hierarchy.get(0).getSubclasses().stream().map(OntologyClassDTO::getUri).toList());

        verify(fusekiService, times(1)).getModel(module.getNamedGraph());
    }

    private List<OntologyClassDTO> hierarchyOf(Model owlModel) {
        UUID moduleId = UUID.randomUUID();
        OntologyModule module = new OntologyModule();
        module.setId(moduleId);
        module.setNamedGraph("http://example.org/module#");
        when(ontologyModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(owlModel);
        return ontologyService.getClassHierarchy(moduleId);
    }

    private static int countNodes(List<OntologyClassDTO> nodes) {
        int n = 0;
        for (OntologyClassDTO node : nodes) n += 1 + countNodes(node.getSubclasses());
        return n;
    }

    /** Varias raíces: el árbol no depende de una única raíz (antes sólo se devolvía la primera). */
    @Test
    void classHierarchyReturnsEveryRoot() {
        Model m = ModelFactory.createDefaultModel();
        for (String c : List.of("A", "B", "C")) m.add(m.createResource("http://example.org/" + c), RDF.type, OWL.Class);
        m.add(m.createResource("http://example.org/C"), RDFS.subClassOf, m.createResource("http://example.org/A"));
        List<OntologyClassDTO> roots = hierarchyOf(m);
        assertEquals(List.of("http://example.org/A", "http://example.org/B"), roots.stream().map(OntologyClassDTO::getUri).toList());
        assertEquals(3, countNodes(roots));
    }

    /** Una clase cuya única superclase es una restricción, o una clase de otro módulo, es raíz. */
    @Test
    void classesWithOnlyAnonymousOrExternalSuperclassesAreRoots() {
        Model m = ModelFactory.createDefaultModel();
        Resource station = m.createResource("http://example.org/Station");
        Resource product = m.createResource("http://example.org/Product");
        Resource restriction = m.createResource();
        m.add(station, RDF.type, OWL.Class).add(product, RDF.type, OWL.Class)
                .add(restriction, RDF.type, OWL.Restriction).add(station, RDFS.subClassOf, restriction)
                .add(product, RDFS.subClassOf, m.createResource("http://other.org/External"))
                .add(product, RDFS.label, "Producto", "es").add(product, RDFS.comment, "Un producto");
        List<OntologyClassDTO> roots = hierarchyOf(m);
        assertEquals(2, roots.size());
        OntologyClassDTO p = roots.stream().filter(r -> r.getUri().endsWith("Product")).findFirst().orElseThrow();
        assertEquals("Producto", p.getLabel());
        assertEquals("Un producto", p.getDescription());
        assertEquals(List.of("http://other.org/External"), p.getSuperClassUris());
    }

    /** Un ciclo de subClassOf no hace recursión infinita y ninguna clase queda afuera. */
    @Test
    void subclassCyclesAreCutAndNoClassIsLost() {
        Model m = ModelFactory.createDefaultModel();
        Resource a = m.createResource("http://example.org/A"), b = m.createResource("http://example.org/B");
        Resource root = m.createResource("http://example.org/Root"), x = m.createResource("http://example.org/X");
        m.add(a, RDF.type, OWL.Class).add(b, RDF.type, OWL.Class).add(root, RDF.type, OWL.Class).add(x, RDF.type, OWL.Class)
                .add(a, RDFS.subClassOf, b).add(b, RDFS.subClassOf, a)
                .add(x, RDFS.subClassOf, root).add(root, RDFS.subClassOf, x).add(x, RDFS.subClassOf, m.createResource("http://example.org/Root"));
        List<OntologyClassDTO> roots = hierarchyOf(m);
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<OntologyClassDTO> todo = new java.util.ArrayDeque<>(roots);
        while (!todo.isEmpty()) { OntologyClassDTO n = todo.pop(); seen.add(n.getUri()); todo.addAll(n.getSubclasses()); }
        assertEquals(java.util.Set.of(a.getURI(), b.getURI(), root.getURI(), x.getURI()), seen);
    }

    /** Un módulo sin clases devuelve una lista vacía, no null. */
    @Test
    void moduleWithoutClassesReturnsEmptyList() {
        assertEquals(List.of(), hierarchyOf(ModelFactory.createDefaultModel()));
    }

    /**
     * Test validateConsistency returns true for consistent module.
     */
    @Test
    void testValidateConsistencyReturnsTrue() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        OntologyModule module = new OntologyModule();
        module.setId(moduleId);
        module.setName("CoreConcepts");
        module.setNamedGraph("http://w3id.org/protys/ontology/CoreConcepts#");

        when(ontologyModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        // Create valid OWL model
        Model owlModel = ModelFactory.createDefaultModel();
        Resource classA = owlModel.createResource("http://example.org/ClassA");
        Resource classB = owlModel.createResource("http://example.org/ClassB");
        owlModel.add(classA, RDF.type, OWL.Class);
        owlModel.add(classB, RDF.type, OWL.Class);

        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(owlModel);

        when(reasoningService.classify(owlModel)).thenReturn(ReasoningService.ClassificationResult.builder().consistent(true).validationStatus("CONSISTENT").build());
        // Act
        boolean result = ontologyService.validateConsistency(moduleId);

        // Assert
        assertTrue(result);
    }

    /**
     * Test getDashboardStats computes aggregated statistics.
     */
    @Test
    void dashboardDeduplicatesSharedModulesAndExcludesSchemaFromIndividuals() {
        OntologyModule mod1 = new OntologyModule();
        mod1.setId(UUID.randomUUID());
        mod1.setName("CoreConcepts");
        mod1.setNamedGraph("http://w3id.org/protys/ontology/CoreConcepts#");
        mod1.setTripleCount(96L);
        LocalDateTime loaded = LocalDateTime.of(2026, 9, 1, 12, 0);
        LocalDateTime edited = loaded.plusDays(2);
        mod1.setLoadedAt(loaded);
        mod1.setStatus(OntologyModule.ModuleStatus.VALIDATED);
        OntologyModule mod2 = new OntologyModule();
        mod2.setId(UUID.randomUUID());
        mod2.setName("OverlappingModule");
        mod2.setNamedGraph("http://example.org/overlap");
        mod2.setUpdatedAt(edited);
        mod2.setStatus(OntologyModule.ModuleStatus.ERROR);

        Model owlModel = ModelFactory.createDefaultModel();
        Resource classA = owlModel.createResource("http://example.org/ClassA");
        Resource classB = owlModel.createResource("http://example.org/ClassB");
        Resource individualA = owlModel.createResource("http://example.org/a");
        Resource individualB = owlModel.createResource("http://example.org/b");
        Resource property = owlModel.createResource("http://example.org/hasPart");
        owlModel.add(classA, RDF.type, OWL.Class);
        owlModel.add(classB, RDF.type, OWL.Class);
        owlModel.add(individualA, RDF.type, classA);
        owlModel.add(individualB, RDF.type, classB);
        owlModel.add(individualB, RDF.type,
                owlModel.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));
        owlModel.add(property, RDF.type, OWL.ObjectProperty);
        owlModel.add(property, RDFS.domain, classA);
        owlModel.add(property, RDFS.range, classB);
        owlModel.add(individualA, owlModel.createProperty(property.getURI()), individualB);
        AlignmentRule rule = AlignmentRule.builder().createdAt(loaded).updatedAt(edited.plusDays(1)).build();

        when(ontologyModuleRepository.findAll()).thenReturn(List.of(mod1, mod2));
        when(fusekiService.getModel(mod1.getNamedGraph())).thenReturn(owlModel);
        when(fusekiService.getModel(mod2.getNamedGraph())).thenReturn(owlModel);
        when(alignmentRuleRepository.findAll()).thenReturn(List.of(rule));
        when(alignmentRuleRepository.countByActiveTrue()).thenReturn(3L);
        when(erpConnectorRepository.countByStatusIn(List.of(ConnectorStatus.CONNECTED,
                ConnectorStatus.MATERIALIZED))).thenReturn(2L);

        DashboardStats stats = ontologyService.getDashboardStats();

        assertEquals(2, stats.getTotalModules());
        assertEquals(9L, stats.getTotalTriples());
        assertEquals(2L, stats.getTotalClasses());
        assertEquals(2L, stats.getTotalIndividuals());
        assertEquals(3, stats.getActiveAlignmentRules());
        assertEquals(2, stats.getConnectedERPs());
        assertNull(stats.getTotalInferences());
        assertEquals(edited.plusDays(1), stats.getLastActivity());
        assertEquals("VALIDATED", stats.getModuleStats().get(0).getStatus());
        assertEquals("ERROR", stats.getModuleStats().get(1).getStatus());
        for (DashboardStats.ModuleStatEntry entry : stats.getModuleStats()) {
            assertEquals(2, entry.getClassCount());
            assertEquals(2, entry.getIndividualCount());
            assertEquals(9L, entry.getTripleCount());
        }
        verify(erpConnectorRepository, never()).countByActiveTrue();
    }

    @Test
    void dashboardDoesNotInventActivityOrInferenceCountForAnEmptySystem() {
        when(ontologyModuleRepository.findAll()).thenReturn(List.of());
        when(alignmentRuleRepository.findAll()).thenReturn(List.of());

        DashboardStats stats = ontologyService.getDashboardStats();

        assertEquals(0, stats.getTotalModules());
        assertEquals(0L, stats.getTotalTriples());
        assertEquals(0L, stats.getTotalClasses());
        assertEquals(0L, stats.getTotalIndividuals());
        assertEquals(0, stats.getActiveAlignmentRules());
        assertEquals(0, stats.getConnectedERPs());
        assertNull(stats.getLastActivity());
        assertNull(stats.getTotalInferences());
    }

    @Test
    void dashboardDoesNotCountClassOrPropertyPunningOrRuleMetadataAsInstances() {
        OntologyModule module = new OntologyModule();
        module.setNamedGraph("http://example.org/metadata");
        Model model = ModelFactory.createDefaultModel();
        Resource classA = model.createResource("http://example.org/ClassA");
        Resource classB = model.createResource("http://example.org/ClassB");
        Resource property = model.createResource("http://example.org/property");
        model.add(classA, RDF.type, OWL.Class);
        model.add(classB, RDF.type, OWL.Class);
        model.add(classA, RDF.type, classB);
        model.add(property, RDF.type, OWL.DatatypeProperty);
        model.add(property, RDF.type, classB);
        model.add(model.createResource("http://example.org/a"), RDF.type, classA);
        model.add(model.createResource(), RDF.type, classA);
        model.add(model.createResource("http://example.org/rule"), RDF.type,
                model.createResource("http://www.w3.org/2003/11/swrl#Imp"));
        model.add(model.createResource("http://example.org/variable"), RDF.type,
                model.createResource("http://www.w3.org/2003/11/swrl#Variable"));
        when(ontologyModuleRepository.findAll()).thenReturn(List.of(module));
        when(alignmentRuleRepository.findAll()).thenReturn(List.of());
        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(model);

        DashboardStats stats = ontologyService.getDashboardStats();

        assertEquals(1L, stats.getTotalIndividuals());
        assertEquals(2L, stats.getTotalClasses());
        assertEquals(model.size(), stats.getTotalTriples());
        assertNull(stats.getModuleStats().get(0).getStatus());
    }

    @Test
    void dashboardDoesNotFetchImportsOutsideRegisteredGraphs(@TempDir Path temporaryDirectory)
            throws Exception {
        Path external = temporaryDirectory.resolve("unregistered.ttl");
        Files.writeString(external, """
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                <http://example.org/ExternalClass> a owl:Class .
                <http://example.org/external> a <http://example.org/ExternalClass> .
                """);
        OntologyModule module = new OntologyModule();
        module.setNamedGraph("http://example.org/registered");
        Model model = ModelFactory.createDefaultModel();
        Resource ontology = model.createResource("http://example.org/ontology");
        Resource type = model.createResource("http://example.org/RegisteredClass");
        model.add(ontology, RDF.type, OWL.Ontology);
        model.add(ontology, OWL.imports, model.createResource(external.toUri().toString()));
        model.add(type, RDF.type, OWL.Class);
        model.add(model.createResource("http://example.org/registeredInstance"), RDF.type, type);
        when(ontologyModuleRepository.findAll()).thenReturn(List.of(module));
        when(alignmentRuleRepository.findAll()).thenReturn(List.of());
        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(model);

        DashboardStats stats = ontologyService.getDashboardStats();

        assertEquals(1L, stats.getTotalClasses());
        assertEquals(1L, stats.getTotalIndividuals());
        assertEquals(4L, stats.getTotalTriples());
        assertEquals(4L, model.size());
    }

    /**
     * Test getAllModules returns empty list when no modules exist.
     */
    @Test
    void testGetAllModulesReturnsEmptyListWhenNoModules() {
        // Arrange
        when(ontologyModuleRepository.findAll()).thenReturn(List.of());

        // Act
        List<OntologyModuleDTO> result = ontologyService.getAllModules();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void unModuloQueNoExisteLanzaModuleNotFound() {
        java.util.UUID id = java.util.UUID.randomUUID();
        when(ontologyModuleRepository.findById(id)).thenReturn(java.util.Optional.empty());
        var jerarquia = assertThrows(org.protys.ws.exception.ModuleNotFoundException.class, () -> ontologyService.getClassHierarchy(id));
        assertEquals("Module not found: " + id, jerarquia.getMessage());
        var validacion = assertThrows(org.protys.ws.exception.ModuleNotFoundException.class, () -> ontologyService.validateDetailed(id));
        assertEquals("Module not found: " + id, validacion.getMessage());
        verifyNoInteractions(fusekiService);
    }
}
