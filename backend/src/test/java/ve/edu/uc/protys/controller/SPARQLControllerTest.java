package ve.edu.uc.protys.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ve.edu.uc.protys.dto.SPARQLRequest;
import ve.edu.uc.protys.dto.SPARQLResponse;
import ve.edu.uc.protys.model.SPARQLQuery;
import ve.edu.uc.protys.service.SPARQLService;

@WebMvcTest(SPARQLController.class)
class SPARQLControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SPARQLService sparqlService;

    /**
     * Test POST /api/sparql/execute with valid SPARQL query.
     */
    @Test
    void testExecuteSparqlQuery() throws Exception {
        // Arrange
        SPARQLRequest request = SPARQLRequest.builder()
                .query("SELECT ?class WHERE { ?class a owl:Class }")
                .build();

        SPARQLResponse response = SPARQLResponse.builder()
                .columns(List.of("class"))
                .rows(List.of(
                        Map.of("class", "http://protys.ontology/Product"),
                        Map.of("class", "http://protys.ontology/Process")
                ))
                .resultCount(2)
                .executionTimeMs(245L)
                .build();

        when(sparqlService.executeQuery(any(SPARQLRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/sparql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount", equalTo(2)))
                .andExpect(jsonPath("$.executionTimeMs", equalTo(245)))
                .andExpect(jsonPath("$.rows", hasSize(2)));

        verify(sparqlService, times(1)).executeQuery(any(SPARQLRequest.class));
    }

    /**
     * Test POST /api/sparql/execute with empty query returns 400.
     */
    @Test
    void testExecuteEmptyQueryReturns400() throws Exception {
        // Arrange
        SPARQLRequest request = SPARQLRequest.builder()
                .query("")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/sparql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test POST /api/sparql/execute with null query returns 400.
     */
    @Test
    void testExecuteNullQueryReturns400() throws Exception {
        // Arrange
        SPARQLRequest request = SPARQLRequest.builder().build();

        // Act & Assert
        mockMvc.perform(post("/api/sparql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test POST /api/sparql/validate with valid query.
     */
    @Test
    void testValidateValidQuery() throws Exception {
        // Arrange
        SPARQLRequest request = SPARQLRequest.builder()
                .query("SELECT ?s ?p ?o WHERE { ?s ?p ?o }")
                .build();

        doNothing().when(sparqlService).validateQuery(anyString());

        // Act & Assert
        mockMvc.perform(post("/api/sparql/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", equalTo(true)))
                .andExpect(jsonPath("$.message", equalTo("Query syntax is valid")));

        verify(sparqlService, times(1)).validateQuery(anyString());
    }

    /**
     * Test POST /api/sparql/validate with invalid query syntax.
     */
    @Test
    void testValidateInvalidQuery() throws Exception {
        // Arrange
        SPARQLRequest request = SPARQLRequest.builder()
                .query("SELECT ? WHERE { }")
                .build();

        doThrow(new IllegalArgumentException("Invalid SPARQL syntax: unexpected token"))
                .when(sparqlService).validateQuery(anyString());

        // Act & Assert
        mockMvc.perform(post("/api/sparql/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", equalTo(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid SPARQL syntax")));
    }

    /**
     * Test GET /api/sparql/templates returns saved templates.
     */
    @Test
    void testGetTemplates() throws Exception {
        // Arrange
        SPARQLQuery template1 = new SPARQLQuery();
        template1.setId(UUID.randomUUID());
        template1.setName("Find all classes");
        template1.setQueryText("SELECT ?class WHERE { ?class a owl:Class }");

        SPARQLQuery template2 = new SPARQLQuery();
        template2.setId(UUID.randomUUID());
        template2.setName("Find all individuals");
        template2.setQueryText("SELECT ?ind WHERE { ?ind a ?class }");

        when(sparqlService.getTemplates()).thenReturn(Arrays.asList(template1, template2));

        // Act & Assert
        mockMvc.perform(get("/api/sparql/templates")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", equalTo("Find all classes")));

        verify(sparqlService, times(1)).getTemplates();
    }

    /**
     * Test GET /api/sparql/templates/competency returns CQ1-CQ5.
     */
    @Test
    void testGetCompetencyQueries() throws Exception {
        // Arrange
        List<SPARQLQuery> cqs = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            SPARQLQuery cq = new SPARQLQuery();
            cq.setId(UUID.randomUUID());
            cq.setName("CQ" + i + ": Competency Question " + i);
            cq.setQueryText("SELECT ?x WHERE { ?x a :Class" + i + " }");
            cqs.add(cq);
        }

        when(sparqlService.getCompetencyQueries()).thenReturn(cqs);

        // Act & Assert
        mockMvc.perform(get("/api/sparql/templates/competency")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name", containsString("CQ1")))
                .andExpect(jsonPath("$[4].name", containsString("CQ5")));

        verify(sparqlService, times(1)).getCompetencyQueries();
    }

    /**
     * Test POST /api/sparql/templates saves a new template.
     */
    @Test
    void testSaveTemplate() throws Exception {
        // Arrange
        SPARQLQuery template = new SPARQLQuery();
        template.setName("Materials query");
        template.setQueryText("SELECT ?mat WHERE { ?mat a :Raw_Material }");

        SPARQLQuery savedTemplate = new SPARQLQuery();
        savedTemplate.setId(UUID.randomUUID());
        savedTemplate.setName("Materials query");
        savedTemplate.setQueryText("SELECT ?mat WHERE { ?mat a :Raw_Material }");
        savedTemplate.setCreatedAt(LocalDateTime.now());

        when(sparqlService.saveTemplate(any(SPARQLQuery.class))).thenReturn(savedTemplate);

        // Act & Assert
        mockMvc.perform(post("/api/sparql/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(template)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("Materials query")));

        verify(sparqlService, times(1)).saveTemplate(any(SPARQLQuery.class));
    }

    /**
     * Test POST /api/sparql/templates with missing name returns 400.
     */
    @Test
    void testSaveTemplateWithMissingNameReturns400() throws Exception {
        // Arrange
        SPARQLQuery template = new SPARQLQuery();
        template.setQueryText("SELECT ?x WHERE { ?x a :Class }");
        // name is null

        // Act & Assert
        mockMvc.perform(post("/api/sparql/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(template)))
                .andExpect(status().isBadRequest());
    }
}
