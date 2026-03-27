package ve.edu.uc.protys.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ve.edu.uc.protys.dto.DashboardStats;
import ve.edu.uc.protys.dto.OntologyClassDTO;
import ve.edu.uc.protys.dto.OntologyModuleDTO;
import ve.edu.uc.protys.model.OntologyModule;
import ve.edu.uc.protys.repository.OntologyModuleRepository;

@ExtendWith(MockitoExtension.class)
class OntologyServiceTest {

    @Mock
    private FusekiService fusekiService;

    @Mock
    private OntologyModuleRepository ontologyModuleRepository;

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
        mod1.setNamedGraph("http://protys.ontology/CoreConcepts#");
        mod1.setTripleCount(96);
        mod1.setCreatedAt(LocalDateTime.now());
        mod1.setUpdatedAt(LocalDateTime.now());

        OntologyModule mod2 = new OntologyModule();
        mod2.setId(id2);
        mod2.setName("ProductMod");
        mod2.setNamedGraph("http://protys.ontology/ProductMod#");
        mod2.setTripleCount(103);
        mod2.setCreatedAt(LocalDateTime.now());
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
        module.setNamedGraph("http://protys.ontology/CoreConcepts#");

        when(ontologyModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        // Create OWL model with class hierarchy
        Model owlModel = ModelFactory.createDefaultModel();
        Resource parentClass = owlModel.createResource("http://protys.ontology/ManufacturingEntity");
        Resource childClass = owlModel.createResource("http://protys.ontology/Product");
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
        module.setNamedGraph("http://protys.ontology/CoreConcepts#");

        when(ontologyModuleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        // Create valid OWL model
        Model owlModel = ModelFactory.createDefaultModel();
        Resource classA = owlModel.createResource("http://example.org/ClassA");
        Resource classB = owlModel.createResource("http://example.org/ClassB");
        owlModel.add(classA, RDF.type, OWL.Class);
        owlModel.add(classB, RDF.type, OWL.Class);

        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(owlModel);

        // Act
        boolean result = ontologyService.validateConsistency(moduleId);

        // Assert
        assertTrue(result);
    }

    /**
     * Test getDashboardStats computes aggregated statistics.
     */
    @Test
    void testGetDashboardStatsComputesAggregates() {
        // Arrange
        OntologyModule mod1 = new OntologyModule();
        mod1.setId(UUID.randomUUID());
        mod1.setName("CoreConcepts");
        mod1.setNamedGraph("http://protys.ontology/CoreConcepts#");
        mod1.setTripleCount(96);
        mod1.setCreatedAt(LocalDateTime.now());

        Model owlModel = ModelFactory.createDefaultModel();
        Resource classA = owlModel.createResource("http://example.org/ClassA");
        owlModel.add(classA, RDF.type, OWL.Class);

        when(ontologyModuleRepository.findAll()).thenReturn(List.of(mod1));
        when(fusekiService.getModel(mod1.getNamedGraph())).thenReturn(owlModel);

        // Act
        DashboardStats stats = ontologyService.getDashboardStats();

        // Assert
        assertNotNull(stats);
        assertEquals(1, stats.getTotalModules());
        assertEquals(96L, stats.getTotalTriples());
        assertTrue(stats.getTotalClasses() >= 1);
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
