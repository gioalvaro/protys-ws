package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

/** Genuine worker/round-trip checks and equal-cardinality information-loss negative. */
public final class AxiomRetentionProbe {
  public static Map<String,Object> verify() throws Exception {
    ObjectMapper json=new ObjectMapper();
    Path dir=Files.createTempDirectory("protys-axiom-retention-probe-");
    try {
      OWLOntologyManager manager=OWLManager.createOWLOntologyManager();
      OWLDataFactory f=manager.getOWLDataFactory();
      String ns="https://example.org/axiom-retention#";
      OWLOntology input=manager.createOntology(IRI.create(ns));
      OWLClass person=f.getOWLClass(IRI.create(ns+"Person")),agent=f.getOWLClass(IRI.create(ns+"Agent")),primary=f.getOWLClass(IRI.create(ns+"PrimaryFlag")),secondary=f.getOWLClass(IRI.create(ns+"SecondaryFlag"));
      OWLDataProperty value=f.getOWLDataProperty(IRI.create(ns+"value"));
      OWLObjectProperty relation=f.getOWLObjectProperty(IRI.create(ns+"relatedTo"));
      OWLNamedIndividual a=f.getOWLNamedIndividual(IRI.create(ns+"a")),b=f.getOWLNamedIndividual(IRI.create(ns+"b"));
      for(OWLEntity entity:List.of(person,agent,primary,secondary,value,relation,a,b))manager.addAxiom(input,f.getOWLDeclarationAxiom(entity));
      OWLAxiom annotatedSubclass=f.getOWLSubClassOfAxiom(person,agent).getAnnotatedAxiom(Set.of(f.getOWLAnnotation(f.getRDFSComment(),f.getOWLLiteral("Axioma con anotación conservada","es"))));
      manager.addAxiom(input,annotatedSubclass);
      manager.addAxiom(input,f.getOWLClassAssertionAxiom(person,a));
      manager.addAxiom(input,f.getOWLClassAssertionAxiom(person,b));
      manager.addAxiom(input,f.getOWLObjectPropertyAssertionAxiom(relation,a,b));
      manager.addAxiom(input,f.getOWLDataPropertyAssertionAxiom(value,a,3));
      manager.addAxiom(input,f.getOWLAnnotationAssertionAxiom(f.getRDFSLabel(),person.getIRI(),f.getOWLLiteral("Persona conservada","es")));
      SWRLVariable x=f.getSWRLVariable(IRI.create(ns+"x"));
      for(int index=1;index<=2;index++) {
        SWRLRule rule=f.getSWRLRule(Set.of(f.getSWRLClassAtom(person,x)),Set.of(f.getSWRLClassAtom(index==1?primary:secondary,x)));
        rule=(SWRLRule)rule.getAnnotatedAxiom(Set.of(f.getOWLAnnotation(f.getRDFSLabel(),f.getOWLLiteral("R0"+index))));
        manager.addAxiom(input,rule);
      }
      Path source=dir.resolve("input.owl");manager.saveOntology(input,new RDFXMLDocumentFormat(),IRI.create(source.toUri()));
      Set<OWLAxiom> original=AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(source.toFile()));
      List<Map<String,Object>> modes=new ArrayList<>();
      for(String mode:List.of("ALL","R01","OWL_ONLY")) {
        Path output=dir.resolve(mode);
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx2g","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),SwrlWorker.class.getName(),source.toString(),output.toString(),mode)
            .redirectOutput(dir.resolve(mode+"-stdout.txt").toFile()).redirectError(dir.resolve(mode+"-stderr.txt").toFile()).start();
        if(!process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Retention worker probe timeout");}
        if(process.exitValue()!=0)throw new IOException(Files.readString(dir.resolve(mode+"-stderr.txt")));
        var report=json.readTree(output.resolve("swrl.json").toFile());
        OWLOntology loaded=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(output.resolve("materialized.owl").toFile());
        Map<String,Object> check=AxiomRetention.require(original,AxiomRetention.nonSwrl(loaded),"probe_independent_saved_reload");
        int expected=mode.equals("ALL")?2:mode.equals("R01")?1:0;
        if(report.path("imported_rule_count").asInt()!=expected||report.path("axiom_preservation").path("intentionally_removed_swrl_rule_count").asInt()!=2-expected)throw new IOException("Rule deactivation retention contract failed");
        boolean primaryPresent=loaded.containsAxiom(f.getOWLClassAssertionAxiom(primary,a));
        boolean secondaryPresent=loaded.containsAxiom(f.getOWLClassAssertionAxiom(secondary,a));
        if(primaryPresent!=(expected>0)||secondaryPresent!=mode.equals("ALL"))throw new IOException("Rule activation result failed");
        modes.add(Map.of("mode",mode,"active_rules",expected,"intentionally_removed_rules",2-expected,"retention",check,"worker_preservation",json.convertValue(report.get("axiom_preservation"),Map.class)));
      }
      // Equal counts cannot establish retention: replace a required assertion.
      Set<OWLAxiom> damaged=new HashSet<>(original);
      OWLAxiom lost=f.getOWLClassAssertionAxiom(person,a);
      damaged.remove(lost);damaged.add(f.getOWLClassAssertionAxiom(agent,b));
      if(damaged.size()!=original.size())throw new IOException("Loss counterexample must retain total cardinality");
      Map<String,Object> negative;
      try {AxiomRetention.require(original,damaged,"synthetic_equal_cardinality_loss");throw new IOException("Information-loss guard did not reject missing assertion");}
      catch(AxiomRetention.Loss expected){negative=expected.report;}
      OWLOntology damagedOntology=manager.createOntology(damaged,IRI.create(ns+"damaged"));
      Path damagedFile=dir.resolve("damaged.owl");manager.saveOntology(damagedOntology,new RDFXMLDocumentFormat(),IRI.create(damagedFile.toUri()));
      Map<String,Object> savedNegative;
      try {AxiomRetention.require(original,AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(damagedFile.toFile())),"synthetic_saved_exchange_loss");throw new IOException("Reloaded information-loss guard did not reject missing assertion");}
      catch(AxiomRetention.Loss expected){savedNegative=expected.report;}
      OWLAnonymousIndividual oldId=f.getOWLAnonymousIndividual("before-renaming"),newId=f.getOWLAnonymousIndividual("after-renaming");
      Map<String,Object> anonymousLimit;
      try {AxiomRetention.require(Set.of(f.getOWLClassAssertionAxiom(person,oldId)),Set.of(f.getOWLClassAssertionAxiom(person,newId)),"synthetic_anonymous_identifier_change");throw new IOException("Anonymous renaming must not be approved by an unimplemented correspondence");}
      catch(AxiomRetention.Loss expected){anonymousLimit=expected.report;}
      if(!anonymousLimit.get("status").equals("NOT_EVALUABLE_ANONYMOUS_IDENTITY")||anonymousLimit.get("lost_axiom_count")!=null)throw new IOException("Anonymous renaming incorrectly labelled as proven information loss");
      OWLAxiom restriction=f.getOWLSubClassOfAxiom(person,f.getOWLObjectSomeValuesFrom(relation,agent));
      OWLOntology restrictionOntology=manager.createOntology(Set.of(restriction),IRI.create(ns+"restriction"));
      Path restrictionFile=dir.resolve("restriction.owl");manager.saveOntology(restrictionOntology,new RDFXMLDocumentFormat(),IRI.create(restrictionFile.toUri()));
      Map<String,Object> restrictionRetention=AxiomRetention.require(Set.of(restriction),AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(restrictionFile.toFile())),"anonymous_owl_expression_saved_reload");
      OWLOntology fullOutput=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(dir.resolve("ALL/materialized.owl").toFile());
      Set<OWLAxiom> completePostInference=AxiomRetention.nonSwrl(fullOutput);
      OWLAxiom inferred=f.getOWLClassAssertionAxiom(primary,a);
      if(original.contains(inferred)||!completePostInference.contains(inferred))throw new IOException("Counterexample must remove a genuine inferred axiom, not an input axiom");
      Set<OWLAxiom> withoutInference=new HashSet<>(completePostInference);withoutInference.remove(inferred);
      OWLOntology inferenceLostOntology=manager.createOntology(withoutInference,IRI.create(ns+"inference-lost"));
      Path inferenceLostFile=dir.resolve("inference-lost.owl");manager.saveOntology(inferenceLostOntology,new RDFXMLDocumentFormat(),IRI.create(inferenceLostFile.toUri()));
      Set<OWLAxiom> inferenceLostReload=AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(inferenceLostFile.toFile()));
      Map<String,Object> oldInputCheck=AxiomRetention.require(original,inferenceLostReload,"counterexample_original_input_is_retained");
      OWLOntology separateDeductions=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(dir.resolve("ALL/deductions.owl").toFile());
      if(!separateDeductions.containsAxiom(inferred))throw new IOException("Counterexample must retain the missing inference in the separate deductions file");
      Map<String,Object> inferredLoss;
      try {SwrlWorker.guard(completePostInference,inferenceLostReload,"synthetic_saved_inference_missing",dir.resolve("ALL"));throw new IOException("Inference missing from the materialized model was incorrectly approved");}
      catch(AxiomRetention.Loss expected){inferredLoss=expected.report;}
      if(!"INFORMATION_LOSS".equals(inferredLoss.get("status"))||((Number)inferredLoss.get("lost_axiom_count")).intValue()!=1)throw new IOException("Inference loss guard did not isolate the missing inferred axiom");
      if(Files.exists(dir.resolve("ALL/swrl.json"))||!Files.exists(dir.resolve("ALL/preservation-error.json")))throw new IOException("Loss must remove any positive worker report and preserve the rejection detail");
      Set<OWLAxiom> noAnnotation=new HashSet<>(original);noAnnotation.remove(annotatedSubclass);noAnnotation.add(annotatedSubclass.getAxiomWithoutAnnotations());
      OWLOntology noAnnotationOntology=manager.createOntology(noAnnotation,IRI.create(ns+"annotation-lost"));
      Path noAnnotationFile=dir.resolve("annotation-lost.owl");manager.saveOntology(noAnnotationOntology,new RDFXMLDocumentFormat(),IRI.create(noAnnotationFile.toUri()));
      Map<String,Object> annotatedLoss;
      try {AxiomRetention.require(original,AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(noAnnotationFile.toFile())),"synthetic_axiom_annotation_removed_during_saved_exchange");throw new IOException("Axiom annotation loss was incorrectly approved");}
      catch(AxiomRetention.Loss expected){annotatedLoss=expected.report;}
      return Map.of("status","PASS","java",System.getProperty("java.version"),"scope","Parsed local non-SWRL OWL axioms; real worker/serialization, active-rule removals separated; no complete ISO equivalence.","modes",modes,"equal_cardinality_loss_rejected",negative,"saved_exchange_loss_rejected",savedNegative,"anonymous_identity_limit",anonymousLimit,"anonymous_expression_retention",restrictionRetention,"inferred_axiom_loss_rejected",Map.of("original_input_retention",oldInputCheck,"deductions_file_retains_inferred_axiom",true,"complete_materialized_retention",inferredLoss),"annotated_axiom_loss_rejected",annotatedLoss);
    } finally {
      try(var files=Files.walk(dir)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
  }
  public static void main(String[] args)throws Exception {
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),verify());
  }
}
