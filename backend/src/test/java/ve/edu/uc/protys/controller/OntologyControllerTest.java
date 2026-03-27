package ve.edu.uc.protys.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ve.edu.uc.protys.dto.OntologyClassDTO;
import ve.edu.uc.protys.dto.OntologyModuleDTO;
import ve.edu.uc.protys.service.OntologyService;

@WebMvcTest(OntologyController.class)
class OntologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OntologyService ontologyService;

    /**
     * Test GET /api/ontology/modules returns list of modules.
     */
    @Test
    void testGetModulesReturnsModuleList() throws Exception {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<OntologyModuleDTO> modules = Arrays.asList(
                OntologyModuleDTO.builder()
                        .id(id1)
                        .name("CoreConcepts")
                        .namedGraph("http://protys.ontology/CoreConcepts#")
                        .classCount(12)
                        .tripleCount(96L)
                        .build(),
                OntologyModuleDTO.builder()
                        .id(id2)
                        .name("ProductMod")
                        .namedGraph("http://protys.ontology/ProductMod#")
                        .classCount(8)
                        .tripleCount(103L)
                        .build()
        );
        when(ontologyService.getAllModules()).thenReturn(modules);

        // Act & Assert
        mockMvc.perform(get("/api/ontology/modules")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", equalTo("CoreConcepts")))
                .andExpect(jsonPath("$[0].classCount", equalTo(12)))
                .andExpect(jsonPath("$[1].name", equalTo("ProductMod")));

        verify(ontologyService, times(1)).getAllModules();
    }

    /**
     * Test POST /api/ontology/modules/upload with OWL file.
     */
    @Test
    void testUploadModuleWithFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "core-concepts.owl",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "<rdf:RDF>mock owl content</rdf:RDF>".getBytes()
        );

        UUID moduleId = UUID.randomUUID();
        OntologyModuleDTO uploadedModule = OntologyModuleDTO.builder()
                .id(moduleId)
                .name("CoreConcepts")
                .namedGraph("http://protys.ontology/CoreConcepts#")
                .classCount(12)
                .tripleCount(96L)
                .build();

        when(ontologyService.loadModule(any(), eq("CoreConcepts")))
                .thenReturn(uploadedModule);

        // Act & Assert
        mockMvc.perform(multipart("/api/ontology/modules/upload")
                        .file(file)
                        .param("name", "CoreConcepts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("CoreConcepts")))
                .andExpect(jsonPath("$.classCount", equalTo(12)));

        verify(ontologyService, times(1)).loadModule(any(), eq("CoreConcepts"));
    }

    /**
     * Test GET /api/ontology/modules/{id}/classes returns class hierarchy.
     */
    @Test
    void testGetClassHierarchyReturnsTree() throws Exception {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        OntologyClassDTO hierarchy = OntologyClassDTO.builder()
                .uri("http://protys.ontology/CoreConcepts#ManufacturingEntity")
                .localName("ManufacturingEntity")
                .label("Manufacturing Entity")
                .propertyCount(5)
                .subclasses(List.of(
                        OntologyClassDTO.builder()
                                .uri("http://protys.ontology/Product#Product")
                                .localName("Product")
                                .label("Product")
                                .propertyCount(3)
                                .build()
                ))
                .build();

        when(ontologyService.getClassHierarchy(moduleId)).thenReturn(hierarchy);

        // Act & Assert
        mockMvc.perform(get("/api/ontology/modules/{id}/classes", moduleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localName", equalTo("ManufacturingEntity")))
                .andExpect(jsonPath("$.propertyCount", equalTo(5)))
                .andExpect(jsonPath("$.subclasses", hasSize(1)));

        verify(ontologyService, times(1)).getClassHierarchy(moduleId);
    }

    /**
     * Test GET /api/ontology/modules/{id}/classes returns 404 when hierarchy is null.
     */
    @Test
    void testGetClassHierarchyReturns404WhenNull() throws Exception {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(ontologyService.getClassHierarchy(moduleId)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/ontology/modules/{id}/classes", moduleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test POST /api/ontology/modules/{id}/validate with consistent module.
     */
    @Test
    void testValidateModuleConsistencyPasses() throws Exception {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(ontologyService.validateConsistency(moduleId)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/ontology/modules/{id}/validate", moduleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleId", equalTo(moduleId.toString())))
                .andExpect(jsonPath("$.consistent", equalTo(true)));

        verify(ontologyService, times(1)).validateConsistency(moduleId);
    }

    /**
     * Test POST /api/ontology/modules/{id}/validate with inconsistent module.
     */
    @Test
    void testValidateModuleConsistencyFails() throws Exception {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(ontologyService.validateConsistency(moduleId)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/ontology/modules/{id}/validate", moduleId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent", equalTo(false)));

        verify(ontologyService, times(1)).validateConsistency(moduleId);
    }

    /**
     * Test upload without file returns 400.
     */
    @Test
    void testUploadModuleWithEmptyFileReturns400() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.owl",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[0]
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/ontology/modules/upload")
                        .file(emptyFile)
                        .param("name", "TestModule"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test upload with invalid file extension returns 400.
     */
    @Test
    void testUploadModuleWithInvalidExtensionReturns400() throws Exception {
        // Arrange
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "data.csv",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "col1,col2\nval1,val2".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/ontology/modules/upload")
                        .file(invalidFile)
                        .param("name", "TestModule"))
                .andExpect(status().isBadRequest());
    }
}
