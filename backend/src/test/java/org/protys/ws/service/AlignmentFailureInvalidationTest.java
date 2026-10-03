package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.repository.*;

class AlignmentFailureInvalidationTest {
  @Test void aRejectedNumericRunCannotLeaveEarlierInferenceResultsVisible() {
    FusekiService fuseki=mock(FusekiService.class);AlignmentRuleRepository rules=mock(AlignmentRuleRepository.class);OntologyModuleRepository modules=mock(OntologyModuleRepository.class);ReasoningService reasoning=mock(ReasoningService.class);
    OntologyModule module=new OntologyModule();module.setNamedGraph("https://example.org/asserted");module.setName("case");
    Model source=ModelFactory.createDefaultModel();source.add(source.createResource("https://example.org/A"),RDF.type,OWL.Class);
    when(modules.findAll()).thenReturn(List.of(module));when(rules.findAll()).thenReturn(List.of());when(fuseki.getModel(anyString())).thenReturn(source);
    when(reasoning.classify(any(Model.class),anyList())).thenReturn(ReasoningService.ClassificationResult.builder().deductions(ModelFactory.createDefaultModel()).consistent(false).validationStatus("NOT_EVALUATED").owlValidationStatus("CONSISTENT").numericEvaluation(Map.of("status","AMBIGUOUS_INPUT")).errorMessage("Ambiguous numerical records").build());
    var result=new AlignmentService(fuseki,rules,modules,reasoning).executeReasoning();
    assertEquals("FAILED",result.getStatus());assertEquals("NOT_EVALUATED",result.getValidationStatus());assertEquals("CONSISTENT",result.getOwlValidationStatus());assertEquals("AMBIGUOUS_INPUT",result.getNumericEvaluation().get("status"));
    verify(fuseki).putModel(eq("http://w3id.org/protys/ontology/inference-results"),argThat(Model::isEmpty));
  }
}
