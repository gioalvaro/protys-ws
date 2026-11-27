package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.protys.ws.exception.ProtysValidationException;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.ERPConnectorRepository;
import org.protys.ws.repository.OntologyModuleRepository;
import org.springframework.mock.web.MockMultipartFile;

/** Carga de módulos: el lector se elige por la extensión; un Turtle se lee como Turtle, no como RDF/XML. */
@ExtendWith(MockitoExtension.class)
class OntologyModuleFormatTest {

    @Mock FusekiService fusekiService;
    @Mock OntologyModuleRepository ontologyModuleRepository;
    @Mock ReasoningService reasoningService;
    @Mock AlignmentRuleRepository alignmentRuleRepository;
    @Mock ERPConnectorRepository erpConnectorRepository;
    @InjectMocks OntologyService ontologyService;

    static final String TTL = "@prefix ex: <http://example.org/> .\nex:a a ex:B ; ex:p \"x\" .\n";
    static final String RDFXML = "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:ex=\"http://example.org/\">"
            + "<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>x</ex:p></rdf:Description></rdf:RDF>";

    @Test
    void languageFollowsTheExtension() {
        assertEquals(Lang.TURTLE, OntologyService.moduleLanguage("datos.TTL"));
        assertEquals(Lang.N3, OntologyService.moduleLanguage("datos.n3"));
        assertEquals(Lang.NTRIPLES, OntologyService.moduleLanguage("datos.nt"));
        assertEquals(Lang.RDFXML, OntologyService.moduleLanguage("red.owl"));
        assertEquals(Lang.RDFXML, OntologyService.moduleLanguage("red.rdf"));
    }

    @Test
    void aTurtleFileIsLoadedWithAllItsTriples() {
        var file = new MockMultipartFile("file", "instancias.ttl", "text/turtle", TTL.getBytes(StandardCharsets.UTF_8));
        var dto = ontologyService.loadModule(file, "turtle-module");
        ArgumentCaptor<Model> stored = ArgumentCaptor.forClass(Model.class);
        verify(fusekiService).putModel(eq("http://w3id.org/protys/ontology/turtle-module#"), stored.capture());
        assertEquals(2, stored.getValue().size());
        assertEquals(2L, dto.getTripleCount());
    }

    @Test
    void anRdfXmlFileStillLoads() {
        var file = new MockMultipartFile("file", "red.owl", "application/rdf+xml", RDFXML.getBytes(StandardCharsets.UTF_8));
        assertEquals(1L, ontologyService.loadModule(file, "xml-module").getTripleCount());
    }

    @Test
    void malformedContentIsAValidationErrorNamingTheFormat() {
        var file = new MockMultipartFile("file", "roto.ttl", "text/turtle", "esto no es turtle".getBytes(StandardCharsets.UTF_8));
        var error = assertThrows(ProtysValidationException.class, () -> ontologyService.loadModule(file, "roto"));
        assertTrue(error.getMessage().contains("Turtle"));
        verify(fusekiService, never()).putModel(anyString(), any());
    }
}
