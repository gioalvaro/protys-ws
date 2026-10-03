package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.swrlapi.core.SWRLRuleEngine;
import org.swrlapi.factory.SWRLAPIFactory;

public class SwrlWorker {
  public static void main(String[] args) throws Exception {
    long start = System.nanoTime();
    Path input = Path.of(args[0]), out = Path.of(args[1]);
    Files.createDirectories(out);
    Files.deleteIfExists(out.resolve("swrl.json")); // A failed retry cannot retain a previous positive report.
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLOntology o = m.loadOntologyFromOntologyDocument(input.toFile());
    if (o.isAnonymous())
      m.applyChange(
          new SetOntologyID(
              o, new OWLOntologyID(IRI.create("https://w3id.org/protys/runtime/materialized"))));
    Set<String> enabled = new HashSet<>();
    if (args.length > 2 && !args[2].equals("ALL")) {
      if (!args[2].equals("OWL_ONLY"))
        for (String id : args[2].split(",")) {
          enabled.add(id.replaceFirst("^(R[0-9]+)_.*$", "$1"));
        }
    }
    Set<OWLAxiom> inputNonSwrl=AxiomRetention.nonSwrl(o);
    int initial = o.getAxioms(AxiomType.SWRL_RULE).size();
    if (args.length > 2 && !args[2].equals("ALL"))
      for (SWRLRule rule : new HashSet<>(o.getAxioms(AxiomType.SWRL_RULE))) {
        String label =
            rule.getAnnotations().stream()
                .filter(a -> a.getProperty().isLabel())
                .map(a -> a.getValue().toString())
                .findFirst()
                .orElse("");
        boolean keep = enabled.stream().anyMatch(id -> label.matches(".*\\b" + id + "\\b.*"));
        if (!keep) m.removeAxiom(o, rule);
      }
    int active = o.getAxioms(AxiomType.SWRL_RULE).size();
    if (args.length > 2 && !args[2].equals("ALL") && active != enabled.size())
      throw new IOException("Requested " + enabled + " but matched " + active + " rules");
    Set<OWLAxiom> before = new HashSet<>(o.getAxioms());
    SWRLRuleEngine engine = SWRLAPIFactory.createSWRLRuleEngine(o);
    long loaded = System.nanoTime();
    engine.infer();
    long done = System.nanoTime();
    if (engine.getNumberOfImportedSWRLRules() != active)
      throw new IOException("Imported rule count does not match active rules");
    long retentionStart=System.nanoTime();
    Set<OWLAxiom> postInferenceNonSwrl=AxiomRetention.nonSwrl(o);
    Map<String,Object> inferenceRetention=guard(inputNonSwrl,postInferenceNonSwrl,"after_inference",out);
    Set<OWLAxiom> added = new HashSet<>(o.getAxioms());
    added.removeAll(before);
    m.saveOntology(
        o, new RDFXMLDocumentFormat(), IRI.create(out.resolve("materialized.owl").toUri()));
    OWLOntology d =
        m.createOntology(added, IRI.create("https://w3id.org/protys/runtime/deductions"));
    m.saveOntology(
        d, new RDFXMLDocumentFormat(), IRI.create(out.resolve("deductions.owl").toUri()));
    OWLOntology saved=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(out.resolve("materialized.owl").toFile());
    Map<String,Object> savedRetention=guard(postInferenceNonSwrl,AxiomRetention.nonSwrl(saved),"saved_complete_post_inference_reload_owlapi4",out);
    OWLOntology savedDeductions=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(out.resolve("deductions.owl").toFile());
    Map<String,Object> deductionsRetention=guard(added,new HashSet<>(savedDeductions.getAxioms()),"saved_deductions_reload_owlapi4",out);
    Map<String,Object> preservation=new LinkedHashMap<>();
    preservation.put("status","RETAINED");
    preservation.put("scope","All non-SWRL axioms parsed from the local input ontology, including annotation axioms. Excludes deliberately deactivated SWRL rules, removed imports, ontology identifiers/header annotations and RDF not represented as parsed OWL axioms; no equivalence with complete ISO standards.");
    preservation.put("input_non_swrl_axiom_count",inputNonSwrl.size());
    preservation.put("post_inference_non_swrl_axiom_count",postInferenceNonSwrl.size());
    preservation.put("input_anonymous_individual_count",AxiomRetention.anonymousCount(inputNonSwrl));
    preservation.put("anonymous_identity_limit","Exact structural comparison; no anonymous-individual renaming correspondence. A mismatch involving an anonymous individual is NOT_EVALUABLE, not a claim of scientific information loss. OWL anonymous expressions and RDF lists are compared as parsed axioms.");
    preservation.put("intentionally_removed_swrl_rule_count",initial-active);
    preservation.put("after_inference",inferenceRetention);
    preservation.put("saved_materialized_reload",savedRetention);
    preservation.put("saved_deductions_reload",deductionsRetention);
    preservation.put("verification_and_serialization_ms",(System.nanoTime()-retentionStart)/1e6);
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("status", "EXECUTED");
    report.put("engine", "SWRLAPI Drools");
    report.put("axiom_preservation",preservation);
    report.put("input_sha256",sha256(input));
    report.put("materialized_sha256",sha256(out.resolve("materialized.owl")));
    report.put("deductions_sha256",sha256(out.resolve("deductions.owl")));
    report.put("swrlapi", "2.1.3");
    report.put("owlapi", "4.5.27");
    report.put("drools", "7.74.1.Final");
    report.put("input_rule_count", initial);
    report.put("active_rule_count", o.getAxioms(AxiomType.SWRL_RULE).size());
    report.put("imported_rule_count", engine.getNumberOfImportedSWRLRules());
    report.put("new_axiom_count", added.size());
    report.put("engine_inferred_axiom_count", engine.getNumberOfInferredOWLAxioms());
    report.put("peak_heap_bytes",java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType()==java.lang.management.MemoryType.HEAP).mapToLong(pool -> pool.getPeakUsage().getUsed()).sum());
    report.put("load_ms", (loaded - start) / 1e6);
    report.put("swrl_ms", (done - loaded) / 1e6);
    report.put("total_ms", (System.nanoTime() - start) / 1e6);
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(out.resolve("swrl.json").toFile(), report);
  }
  static Map<String,Object> guard(Set<OWLAxiom> expected,Set<OWLAxiom> obtained,String stage,Path out)throws Exception {
    try { return AxiomRetention.require(expected,obtained,stage); }
    catch(AxiomRetention.Loss loss) {
      Files.deleteIfExists(out.resolve("swrl.json"));
      new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.resolve("preservation-error.json").toFile(),loss.report);
      throw loss;
    }
  }
  static String sha256(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
}
