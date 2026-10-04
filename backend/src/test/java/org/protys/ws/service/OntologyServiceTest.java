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
        OntologyClassDTO hierarchy = ontologyService.getClassHierarchy(moduleId);

        // Assert
        assertNotNull(hierarchy);
        assertNotNull(hierarchy.getUri());

        verify(fusekiService, times(1)).getModel(module.getNamedGraph());
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
}
