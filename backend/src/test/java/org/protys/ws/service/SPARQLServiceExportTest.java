package org.protys.ws.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.protys.ws.dto.SPARQLResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SPARQLServiceExportTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final SPARQLService service = new SPARQLService(null, null, mapper);

    private SPARQLResponse capturedQ06() throws Exception {
        try (var input = getClass().getResourceAsStream("/sparql/q06-response.json")) {
            assertNotNull(input);
            return mapper.readValue(input, SPARQLResponse.class);
        }
    }

    @Test
    void csvExportsCapturedExecutionDtoWithNullRowsAndColumns() throws Exception {
        SPARQLResponse response = capturedQ06();
        assertNull(response.getRows());
        assertNull(response.getColumns());
        String csv = service.exportResults(response, "CSV");
        String[] lines = csv.split("\r\n");
        assertEquals(4, lines.length);
        assertEquals("product,lot,liters,ghgKgCO2e,kgCO2ePerL", lines[0]);
        assertEquals("http://w3id.org/protys/fixture#WhitePaint,http://w3id.org/protys/fixture#BatchA,98.4,57.6666665,0.586043358739837398373984", lines[1]);
        assertTrue(lines[2].contains("#BatchB,195.2,115.3333335,"));
        assertTrue(lines[3].contains("#BatchC,97.392,57.6666665,"));
    }

    @Test
    void jsonPreservesTypedBindingsQueryAndMetadataInsteadOfNullResults() throws Exception {
        SPARQLResponse response = capturedQ06();
        JsonNode exported = mapper.readTree(service.exportResults(response, "JSON"));
        assertEquals(response.getSparqlJson(), exported.get("sparqlJson"));
        assertEquals(3, exported.get("results").size());
        assertEquals(response.getQueryText(), exported.get("queryText").asText());
        assertEquals(3, exported.get("resultCount").asInt());
        assertEquals("http://www.w3.org/2001/XMLSchema#decimal", exported.at("/sparqlJson/results/bindings/0/liters/datatype").asText());
        assertEquals("uri", exported.at("/sparqlJson/results/bindings/0/product/type").asText());
    }

    @Test
    void csvEscapesCommaQuotesNewlinesNullsAndKeepsDeclaredColumnOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("empty", null);
        first.put("text", "red, \"paint\"\nnext");
        Map<String, Object> second = Map.of("text", "last");
        SPARQLResponse response = SPARQLResponse.builder().columns(List.of("text", "empty"))
                .rows(List.of(first, second)).build();
        assertEquals("text,empty\r\n\"red, \"\"paint\"\"\nnext\",\r\nlast,\r\n", service.exportResults(response, "CSV"));
    }

    @Test
    void csvRetainsHeaderForEmptyTypedResults() throws Exception {
        SPARQLResponse response = SPARQLResponse.builder()
                .sparqlJson(mapper.readTree("{\"head\":{\"vars\":[\"lot\",\"amount\"]},\"results\":{\"bindings\":[]}}"))
                .resultCount(0).build();
        assertEquals("lot,amount\r\n", service.exportResults(response, "CSV"));
    }

    @Test
    void csvExportsLegacyResultMapsWithoutRowsAndPreservesMissingCells() {
        SPARQLResponse response = SPARQLResponse.builder().columns(List.of("lot", "amount"))
                .results(List.of(Map.of("lot", "A", "amount", "1"), Map.of("lot", "B"))).build();
        assertEquals("lot,amount\r\nA,1\r\nB,\r\n", service.exportResults(response, "CSV"));
    }

    @Test
    void askFalseRemainsAnAnswerInBothFormats() throws Exception {
        SPARQLResponse response = SPARQLResponse.builder().askResult(false).resultCount(1).build();
        assertEquals("askResult\r\nfalse\r\n", service.exportResults(response, "CSV"));
        assertFalse(mapper.readTree(service.exportResults(response, "JSON")).get("askResult").asBoolean());
    }

    @Test
    void unsupportedFormatsAreRejectedByTheServiceAsWellAsTheController() throws Exception {
        SPARQLResponse response = capturedQ06();
        for (String format : List.of("XML", "JSONLD", "unknown")) {
            var error = assertThrows(org.protys.ws.exception.ProtysFusekiException.class,
                    () -> service.exportResults(response, format));
            assertTrue(error.getMessage().contains("Supported formats: CSV, JSON."));
        }
    }
}
