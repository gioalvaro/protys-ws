package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;

/** Executes genuine isolated OWL/SWRL engines. No mock validation success. */
class ReasoningServiceIntegrationTest {
  private ReasoningService service() {
    return new ReasoningService(
        90000, "hermit-swrlapi-drools", Path.of("..").toAbsolutePath().toString());
  }

  private Model valid() {
    Model m = ModelFactory.createDefaultModel();
    m.add(m.createResource("https://example.org/A"), RDF.type, OWL.Class);
    return m;
  }

  @Test
  void consistentCacheAndContentChange() {
    ReasoningService s = service();
    Model m = valid();
    var a = s.classify(m);
    assertEquals("CONSISTENT", a.getValidationStatus());
    assertFalse(a.isCacheHit());
    var b = s.classify(m);
    assertTrue(b.isCacheHit());
    assertEquals(0, b.getReasoningTimeMs());
    m.add(m.createResource("https://example.org/B"), RDF.type, OWL.Class);
    assertFalse(s.classify(m).isCacheHit());
  }

  @Test void identicalRdfWithNewBlankNodeLabelsReusesCache(){
    ReasoningService s=service();Model first=valid();Resource restriction=first.createResource(),b=first.createResource("https://example.org/B");Property p=first.createProperty("https://example.org/p");
    first.add(b,RDF.type,OWL.Class).add(p,RDF.type,OWL.ObjectProperty).add(first.createResource("https://example.org/A"),RDFS.subClassOf,restriction).add(restriction,RDF.type,OWL.Restriction).add(restriction,OWL.onProperty,p).add(restriction,OWL.someValuesFrom,b);
    Model second=valid();Resource other=second.createResource();second.add(b,RDF.type,OWL.Class).add(p,RDF.type,OWL.ObjectProperty).add(second.createResource("https://example.org/A"),RDFS.subClassOf,other).add(other,RDF.type,OWL.Restriction).add(other,OWL.onProperty,p).add(other,OWL.someValuesFrom,b);
    assertEquals("CONSISTENT",s.classify(first).getValidationStatus());var repeat=s.classify(second);assertTrue(repeat.isCacheHit());assertEquals(0,repeat.getReasoningTimeMs());
  }

  @Test
  void inconsistentIsNotAccepted() {
    ReasoningService s = service();
    Model m = valid();
    Resource a = m.createResource("https://example.org/A"),
        b = m.createResource("https://example.org/B"),
        x = m.createResource("https://example.org/x");
    m.add(b, RDF.type, OWL.Class)
        .add(a, OWL.disjointWith, b)
        .add(x, RDF.type, OWL2.NamedIndividual)
        .add(x, RDF.type, a)
        .add(x, RDF.type, b);
    var r = s.classify(m);
    assertEquals("INCONSISTENT", r.getValidationStatus());
    assertFalse(r.isConsistent());
    assertEquals(0, s.getCacheSize());
  }

  @Test
  void outOfProfileAndMissingRuntimeAreNotEvaluated() {
    Model m = valid();
    Resource a = m.createResource("https://example.org/dateProperty");
    m.add(a, RDF.type, OWL.DatatypeProperty)
        .add(a, RDFS.range, m.createResource("http://www.w3.org/2001/XMLSchema#date"));
    var r = service().classify(m);
    assertEquals("NOT_EVALUATED", r.getValidationStatus());
    assertFalse(r.isConsistent());
    assertEquals(
        "NOT_EVALUATED",
        new ReasoningService(10000, "hermit-swrlapi-drools", "missing-runtime")
            .classify(valid())
            .getValidationStatus());
  }

  @Test
  void dlClassificationAndPropertyConsequencesAreActuallyReturned() {
    Model model=valid();Resource child=model.createResource("https://example.org/A"),parent=model.createResource("https://example.org/Parent"),x=model.createResource("https://example.org/x"),y=model.createResource("https://example.org/y");Property p=model.createProperty("https://example.org/p"),q=model.createProperty("https://example.org/q");
    model.add(parent,RDF.type,OWL.Class).add(child,RDFS.subClassOf,parent).add(x,RDF.type,OWL2.NamedIndividual).add(y,RDF.type,OWL2.NamedIndividual).add(x,RDF.type,child).add(p,RDF.type,OWL.ObjectProperty).add(q,RDF.type,OWL.ObjectProperty).add(p,RDFS.subPropertyOf,q).add(x,p,y);
    var result=service().classify(model);assertEquals("CONSISTENT",result.getValidationStatus(),result.getErrorMessage());assertTrue(result.getDeductions().contains(x,RDF.type,parent));assertTrue(result.getDeductions().contains(x,q,y));
  }

  @Test
  void declaredContextConflictIsVisibleAndSeparateFromOwlConsistency() {
    Model model=valid();
    String ns="http://w3id.org/protys/ontology/iso15531#";
    Resource lotType=model.createResource(ns+"Lot");model.add(lotType,RDF.type,OWL.Class);
    for(String property:List.of("hasActivity","hasProcess","partOfProcess"))model.add(model.createResource(ns+property),RDF.type,OWL.ObjectProperty);
    Resource lot=model.createResource("https://example.org/lot"),op=model.createResource("https://example.org/op"),process=model.createResource("https://example.org/process"),foreign=model.createResource("https://example.org/foreign");
    for(Resource individual:List.of(lot,op,process,foreign))model.add(individual,RDF.type,OWL2.NamedIndividual);
    model.add(lot,RDF.type,lotType).add(lot,model.createProperty(ns+"hasActivity"),op).add(lot,model.createProperty(ns+"hasProcess"),process).add(op,model.createProperty(ns+"partOfProcess"),foreign);
    ReasoningService service=service();var conflict=service.classify(model);
    assertEquals("CONSISTENT",conflict.getValidationStatus(),conflict.getErrorMessage());
    assertEquals("CONTEXT_INTEGRITY_ERROR",conflict.getContextEvaluation().get("status"));
    var cached=service.classify(model);assertTrue(cached.isCacheHit());assertEquals("CONTEXT_INTEGRITY_ERROR",cached.getContextEvaluation().get("status"));
    model.removeAll(op,model.createProperty(ns+"partOfProcess"),null).add(op,model.createProperty(ns+"partOfProcess"),process);
    var fixed=service.classify(model);assertFalse(fixed.isCacheHit());assertEquals("CONTEXT_VALID",fixed.getContextEvaluation().get("status"));
    Model standalone=valid();standalone.add(op,RDF.type,OWL2.NamedIndividual);
    assertEquals("NOT_APPLICABLE",service.classify(standalone).getContextEvaluation().get("status"));
  }

  @Test
  void actualRuleActivationChangesMaterializedResult() throws Exception {
    ReasoningService s = service();
    Model m = ModelFactory.createDefaultModel();
    Path root = Path.of("..").toAbsolutePath();
    var catalog = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve("research/catalog.json").toFile());
    for (var config : catalog.path("configurations")) if(config.path("id").asText().equals("integrated")) {
      for (var f : config.path("tbox_paths")) RDFDataMgr.read(m,root.resolve(f.asText()).toUri().toString());
      for (var f : config.path("rules_paths")) RDFDataMgr.read(m,root.resolve(f.asText()).toUri().toString());
    }
    RDFDataMgr.read(m, root.resolve("research/data/fixtures/compatibility.ttl").toUri().toString());
    var on = s.classify(m, List.of("R03"));
    assertEquals("CONSISTENT", on.getValidationStatus(), on.getErrorMessage());
    assertEquals(1, on.getImportedRuleCount());
    assertTrue(
        on.getDeductions()
            .contains(
                null,
                m.createProperty("http://w3id.org/protys/ontology/alignment#hasRecordedInputFlowForLot"),
                (RDFNode) null));
    var off = s.classify(m);
    assertEquals("CONSISTENT", off.getValidationStatus());
    assertFalse(off.isCacheHit());
    assertFalse(
        off.getDeductions()
            .contains(
                null,
                m.createProperty("http://w3id.org/protys/ontology/alignment#hasRecordedInputFlowForLot"),
                (RDFNode) null));
  }

  @Test void ambiguousNumericalRecordsBlockBeforeSwrlAndAreNeverCached() {
    Model m=valid();String ns="http://w3id.org/protys/ontology/iso15531#";
    for(String type:List.of("Lot","ProcessOperation"))m.add(m.createResource(ns+type),RDF.type,OWL.Class);
    for(String name:List.of("hasActivity","hasProcess","partOfProcess"))m.add(m.createResource(ns+name),RDF.type,OWL.ObjectProperty);
    Property energy=m.createProperty(ns+"consumesEnergyKWh");m.add(energy,RDF.type,OWL.DatatypeProperty);
    Resource lot=m.createResource("https://example.org/numericLot"),op=m.createResource("https://example.org/numericOp"),process=m.createResource("https://example.org/numericProcess");
    for(Resource individual:List.of(lot,op,process))m.add(individual,RDF.type,OWL2.NamedIndividual);
    m.add(lot,RDF.type,m.createResource(ns+"Lot")).add(op,RDF.type,m.createResource(ns+"ProcessOperation"));
    m.add(lot,m.createProperty(ns+"hasActivity"),op).add(lot,m.createProperty(ns+"hasProcess"),process).add(op,m.createProperty(ns+"partOfProcess"),process);
    m.add(op,energy,m.createTypedLiteral(10.0)).add(op,energy,m.createTypedLiteral(20.0));
    ReasoningService s=service();var result=s.classify(m,List.of("R01"));
    assertEquals("NOT_EVALUATED",result.getValidationStatus(),result.getErrorMessage());
    assertEquals("CONSISTENT",result.getOwlValidationStatus());
    assertEquals("AMBIGUOUS_INPUT",result.getNumericEvaluation().get("status"));
    assertFalse(result.isConsistent());assertFalse(result.isCacheHit());assertEquals(0,result.getImportedRuleCount());assertTrue(result.getDeductions().isEmpty());assertEquals(0,s.getCacheSize());
    var repeated=s.classify(m,List.of("R01"));assertFalse(repeated.isCacheHit());assertEquals("AMBIGUOUS_INPUT",repeated.getNumericEvaluation().get("status"));assertEquals(0,s.getCacheSize());
  }
}
