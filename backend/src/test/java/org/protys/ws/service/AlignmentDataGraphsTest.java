package org.protys.ws.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.OntologyModuleRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Razonamiento con grafos de datos de conectores ERP sumados a los módulos registrados. */
class AlignmentDataGraphsTest {

    static final String ERP_GRAPH = "http://w3id.org/protys/erp/odoo";
    FusekiService fuseki;
    ReasoningService reasoning;
    AlignmentService service;
    Model module;
    Model erp;

    @BeforeEach
    void setUp() {
        fuseki = mock(FusekiService.class);
        reasoning = mock(ReasoningService.class);
        var modules = mock(OntologyModuleRepository.class);
        var rules = mock(AlignmentRuleRepository.class);
        OntologyModule m = new OntologyModule();
        m.setNamedGraph("http://w3id.org/protys/ontology/iso15531");
        m.setName("iso15531");
        when(modules.findAll()).thenReturn(List.of(m));
        when(rules.findAll()).thenReturn(List.of());
        module = ModelFactory.createDefaultModel();
        module.add(module.createResource("http://w3id.org/protys/ontology/iso15531#ProcessPlan"), RDF.type, OWL.Class);
        erp = ModelFactory.createDefaultModel();
        erp.add(erp.createResource("http://w3id.org/protys/erp/odoo/plan/8"), RDF.type,
                erp.createResource("http://w3id.org/protys/ontology/iso15531#ProcessPlan"));
        when(fuseki.getModel(anyString())).thenReturn(ModelFactory.createDefaultModel());
        when(fuseki.getModel("http://w3id.org/protys/ontology/iso15531")).thenReturn(module);
        when(fuseki.getModelOrEmpty(ERP_GRAPH)).thenReturn(erp);
        when(fuseki.getModelOrEmpty("http://w3id.org/protys/erp/vacio")).thenReturn(ModelFactory.createDefaultModel());
        when(reasoning.classify(any(Model.class), anyList())).thenReturn(ReasoningService.ClassificationResult.builder()
                .deductions(ModelFactory.createDefaultModel()).consistent(true).validationStatus("CONSISTENT").build());
        service = new AlignmentService(fuseki, rules, modules, reasoning);
    }

    Model reasonedModel() {
        ArgumentCaptor<Model> captor = ArgumentCaptor.forClass(Model.class);
        verify(reasoning).classify(captor.capture(), anyList());
        return captor.getValue();
    }

    @Test void dataGraphsAreAddedToTheReasonedModel() {
        var stats = service.executeReasoning(List.of(ERP_GRAPH));
        assertEquals("SUCCESS", stats.getStatus());
        assertEquals(List.of(ERP_GRAPH), stats.getDataGraphs());
        assertTrue(reasonedModel().containsAll(erp));
        assertTrue(reasonedModel().containsAll(module));
    }

    @Test void withoutDataGraphsNothingChangesAndNoDataGraphIsRead() {
        var stats = service.executeReasoning();
        assertEquals("SUCCESS", stats.getStatus());
        assertEquals(List.of(), stats.getDataGraphs());
        assertFalse(reasonedModel().containsAny(erp));
        verify(fuseki, never()).getModelOrEmpty(anyString());
    }

    @Test void graphsOutsideTheErpPrefixAreRejected() {
        var stats = service.executeReasoning(List.of("http://w3id.org/protys/ontology/inference-results"));
        assertEquals("FAILED", stats.getStatus());
        assertTrue(stats.getErrorMessage().startsWith("NOT_EVALUATED"));
        verify(reasoning, never()).classify(any(Model.class), anyList());
    }

    @Test void emptyDataGraphsAreRejected() {
        var stats = service.executeReasoning(List.of("http://w3id.org/protys/erp/vacio"));
        assertEquals("FAILED", stats.getStatus());
        assertTrue(stats.getErrorMessage().contains("empty"));
        verify(reasoning, never()).classify(any(Model.class), anyList());
    }
}
