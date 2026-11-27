package org.protys.research;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.profiles.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.*;

public class AcademicRunner {
  static ObjectMapper json = new ObjectMapper();

  static Map<String, Object> validate(Model model) throws Exception {
    return validate(model, new ReasonerFactory());
  }

  static Map<String, Object> validate(Model model, OWLReasonerFactory factory) throws Exception {
    return validate(model, factory, null, null, null);
  }

  static Map<String, Object> validate(Model model, OWLReasonerFactory factory, String incompleteCleaningQuery, String missingCleaningQuery, String invalidContextQuery) throws Exception {
    return validate(model,factory,incompleteCleaningQuery,missingCleaningQuery,invalidContextQuery,null,null,null);
  }

  static Map<String, Object> validate(Model model, OWLReasonerFactory factory, String incompleteCleaningQuery, String missingCleaningQuery, String invalidContextQuery,String invalidNumericQuery,String ambiguousNumericQuery,String invalidNumericOutputQuery) throws Exception {
    return validate(model,factory,incompleteCleaningQuery,missingCleaningQuery,invalidContextQuery,invalidNumericQuery,ambiguousNumericQuery,invalidNumericOutputQuery,true);
  }

  static Map<String, Object> validate(Model model, OWLReasonerFactory factory, String incompleteCleaningQuery, String missingCleaningQuery, String invalidContextQuery,String invalidNumericQuery,String ambiguousNumericQuery,String invalidNumericOutputQuery,boolean numericControlsEnabled) throws Exception {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", "NOT_EVALUATED");
    out.put("profile_valid", false);
    OWLReasoner reasoner = null;
    long start = System.nanoTime();
    try {
      out.put("cleaning_evaluation", CleaningContextEvaluator.evaluate(model, incompleteCleaningQuery, missingCleaningQuery));
      out.put("context_evaluation", ContextIntegrityEvaluator.evaluate(model, invalidContextQuery));
      out.put("numeric_evaluation",numericControlsEnabled?NumericInputEvaluator.evaluate(model,invalidNumericQuery,ambiguousNumericQuery,invalidNumericOutputQuery):Map.of("status","NOT_APPLICABLE","applicable_configuration",false,"selected_operation_count",0,"output_checked",false,"reason","Integrated electrical-inventory controls are not applicable to this source configuration."));
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      RDFDataMgr.write(b, model, Lang.RDFXML);
      OWLOntologyManager m = OWLManager.createOWLOntologyManager();
      OWLOntology o = m.loadOntologyFromOntologyDocument(new ByteArrayInputStream(b.toByteArray()));
      out.put("swrl_rule_count", o.getAxioms(AxiomType.SWRL_RULE).size());
      m.removeAxioms(o, o.getAxioms(AxiomType.SWRL_RULE));
      Set<OWLAxiom> inputAxioms=AxiomRetention.nonSwrl(o);
      OWLProfileReport p = new OWL2DLProfile().checkOntology(o);
      out.put("profile_valid", p.isInProfile());
      out.put("profile_violations", p.getViolations().stream().map(Object::toString).toList());
      if(!p.isInProfile()){out.put("failure_kind","OWL_PROFILE_INVALID");out.put("error","Structural ontology is outside the OWL 2 DL profile");}
      if (p.isInProfile()) {
        reasoner = factory.createReasoner(o);
        boolean c = reasoner.isConsistent();
        if (!c) {out.put("status", "INCONSISTENT");out.put("failure_kind","OWL_INCONSISTENT");}
        if (c) {
          reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY,InferenceType.CLASS_ASSERTIONS,InferenceType.OBJECT_PROPERTY_HIERARCHY,InferenceType.DATA_PROPERTY_HIERARCHY);
          out.put("dl_materialized_triples", materializeNamedClosure(reasoner,model));
          out.put("dl_axiom_preservation",AxiomRetention.require(inputAxioms,parsedNonSwrl(model),"named_dl_export_reload_owlapi5"));
        }
        out.put(
            "unsatisfiable_classes",
            c
                ? reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().stream()
                    .map(x -> x.getIRI().toString())
                    .sorted()
                    .toList()
                : List.of());
        // A positive status certifies all requested classification/export stages completed.
        if (c) {
          out.put("cleaning_evaluation", CleaningContextEvaluator.evaluate(model, incompleteCleaningQuery, missingCleaningQuery));
          out.put("context_evaluation", ContextIntegrityEvaluator.evaluate(model, invalidContextQuery));
          out.put("status", "CONSISTENT");
        }
      }
    } catch (Exception | LinkageError e) {
      out.put("status", "NOT_EVALUATED");
      out.put("error", e.getClass().getName() + ": " + e.getMessage());
      out.put("failure_kind","OWL_VALIDATION_ERROR");
      if(e instanceof AxiomRetention.Loss loss)out.put("axiom_preservation_error",loss.report);
    } finally {
      if (reasoner != null) {
        try { reasoner.dispose(); }
        catch (Exception | LinkageError e) {
          out.put("status", "NOT_EVALUATED");
          out.put("error", e.getClass().getName() + ": " + e.getMessage());
          out.put("failure_kind","OWL_VALIDATION_ERROR");
        }
      }
    }
    out.put("dl_ms", (System.nanoTime() - start) / 1e6);
    out.put("owl_validation_status",out.get("status"));
    return out;
  }

  static void rejectNumericAmbiguity(Map<String,Object> result) {
    Object value=result.get("numeric_evaluation");
    if(value instanceof Map<?,?> numeric && "AMBIGUOUS_INPUT".equals(numeric.get("status")) && "CONSISTENT".equals(result.get("status"))) {
      result.put("status","NOT_EVALUATED");
      result.put("failure_kind","AMBIGUOUS_NUMERIC_INPUT");
      result.put("error",numeric.get("reason"));
    }
  }

  /** Named class/type/property consequences; no creation of existential witnesses. */
  static long materializeNamedClosure(OWLReasoner reasoner,Model model)throws Exception {
    OWLOntologyManager manager=OWLManager.createOWLOntologyManager();
    OWLOntology inferred=manager.createOntology();
    var generators=List.<InferredAxiomGenerator<? extends OWLAxiom>>of(new InferredClassAssertionAxiomGenerator(),new InferredPropertyAssertionGenerator(),new InferredSubClassAxiomGenerator());
    // fillOntology logs and skips a failing generator. Propagate failures instead,
    // so a partial export cannot be certified as a completed validation.
    for (var generator : generators)
      manager.addAxioms(inferred, generator.createAxioms(manager.getOWLDataFactory(), reasoner));
    long before=model.size();
    for(OWLAxiom axiom:inferred.getAxioms()){
      if(axiom instanceof OWLClassAssertionAxiom a && !a.getIndividual().isAnonymous()&&!a.getClassExpression().isAnonymous()) model.add(model.createResource(a.getIndividual().asOWLNamedIndividual().getIRI().toString()),org.apache.jena.vocabulary.RDF.type,model.createResource(a.getClassExpression().asOWLClass().getIRI().toString()));
      else if(axiom instanceof OWLObjectPropertyAssertionAxiom a && !a.getSubject().isAnonymous()&&!a.getObject().isAnonymous()&&!a.getProperty().isAnonymous()) model.add(model.createResource(a.getSubject().asOWLNamedIndividual().getIRI().toString()),model.createProperty(a.getProperty().asOWLObjectProperty().getIRI().toString()),model.createResource(a.getObject().asOWLNamedIndividual().getIRI().toString()));
      else if(axiom instanceof OWLDataPropertyAssertionAxiom a && !a.getSubject().isAnonymous()) {OWLLiteral value=a.getObject();Literal literal=value.hasLang()?model.createLiteral(value.getLiteral(),value.getLang()):model.createTypedLiteral(value.getLiteral(),org.apache.jena.datatypes.TypeMapper.getInstance().getSafeTypeByName(value.getDatatype().getIRI().toString()));model.add(model.createResource(a.getSubject().asOWLNamedIndividual().getIRI().toString()),model.createProperty(a.getProperty().asOWLDataProperty().getIRI().toString()),literal);}
      else if(axiom instanceof OWLSubClassOfAxiom a && !a.getSubClass().isAnonymous()&&!a.getSuperClass().isAnonymous()) model.add(model.createResource(a.getSubClass().asOWLClass().getIRI().toString()),org.apache.jena.vocabulary.RDFS.subClassOf,model.createResource(a.getSuperClass().asOWLClass().getIRI().toString()));
    }
    return model.size()-before;
  }

  static void write(Path p, Object o) throws Exception {
    json.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), o);
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]).toAbsolutePath(),
        request = Path.of(args[1]),
        out = Path.of(args[2]);
    Files.createDirectories(out);
    long start = System.nanoTime();
    JsonNode spec = json.readTree(request.toFile());
    boolean numericControlsEnabled=spec.path("numeric_controls_enabled").asBoolean(true);
    String incompleteCleaningQuery=Files.readString(root.resolve("research/model/validations/C26-context-completeness.sparql"));
    String missingCleaningQuery=Files.readString(root.resolve("research/model/validations/R26.sparql"));
    String invalidContextQuery=Files.readString(root.resolve("research/model/validations/CCTX-context-completeness.sparql"));
    String invalidNumericQuery=Files.readString(root.resolve("research/model/validations/CNUM-input-invalid.sparql"));
    String ambiguousNumericQuery=Files.readString(root.resolve("research/model/validations/CNUM-input-ambiguous.sparql"));
    String invalidNumericOutputQuery=Files.readString(root.resolve("research/model/validations/CNUM-output-invalid.sparql"));
    Model model = ModelFactory.createDefaultModel();
    try {
      for (JsonNode f : spec.path("files"))
        RDFDataMgr.read(model, root.resolve(f.asText()).toUri().toString());
    } catch (Exception e) {
      write(
          out.resolve("validation.json"),
          Map.of("status", "NOT_EVALUATED", "owl_validation_status","NOT_EVALUATED", "failure_kind","LOAD_ERROR", "profile_valid", false, "error", e.toString()));
      System.exit(2);
    }
    model.removeAll(
        null, model.createProperty("http://www.w3.org/2002/07/owl#imports"), (RDFNode) null);
    long rawInputTriples=model.size();
    long inputNamedIndividuals=model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type,org.apache.jena.vocabulary.OWL2.NamedIndividual).toList().size();
    try(OutputStream stream=Files.newOutputStream(out.resolve("raw-input.owl"))){RDFDataMgr.write(stream,model,Lang.RDFXML);}
    List<Map<String, Object>> constructs = new ArrayList<>();
    for (JsonNode c : spec.path("pre_inference_constructs")) {
      String path = c.isTextual() ? c.asText() : c.path("path").asText();
      Query query = QueryFactory.create(Files.readString(root.resolve(path)));
      if (!query.isConstructType()) throw new IOException("Pre-inference task must be CONSTRUCT");
      long cs = System.nanoTime();
      Model additions;
      try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
        additions = qe.execConstruct();
      }
      long before = model.size();
      model.add(additions);
      constructs.add(
          Map.of(
              "path",
              path,
              "added_triples",
              model.size() - before,
              "ms",
              (System.nanoTime() - cs) / 1e6));
    }
    long loaded = System.nanoTime();
    long inputTriples = model.size();
    Path prepared=out.resolve("prepared-input.ttl");try(OutputStream stream=Files.newOutputStream(prepared)){RDFDataMgr.write(stream,model,Lang.TURTLE);}
    Model beforeDl=ModelFactory.createDefaultModel().add(model);
    Map<String, Object> result = validate(model, new ReasonerFactory(), incompleteCleaningQuery, missingCleaningQuery, invalidContextQuery,invalidNumericQuery,ambiguousNumericQuery,null,numericControlsEnabled);
    rejectNumericAmbiguity(result);
    try(OutputStream stream=Files.newOutputStream(out.resolve("dl-pre-deductions.ttl"))){RDFDataMgr.write(stream,model.difference(beforeDl),Lang.TURTLE);}
    write(out.resolve("validation.json"), result);
    if (!result.get("status").equals("CONSISTENT")) System.exit(3);
    Path combined = out.resolve("combined.owl");
    try (OutputStream stream = Files.newOutputStream(combined)) {
      RDFDataMgr.write(stream, model, Lang.RDFXML);
    }
    String enabled = spec.path("enabled_rules").asText("NONE");
    if (!enabled.equals("NONE")) {
      String cp =
          Files.readString(root.resolve("research/runtime/swrl-worker/target/classpath.txt"))
              .trim();
      String worker =
          root.resolve("research/runtime/swrl-worker/target/classes") + File.pathSeparator + cp;
      Process proc =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin/java").toString(),
                  "-Xmx2g",
                  "-Djava.awt.headless=true",
                  "-cp",
                  worker,
                  "org.protys.research.SwrlWorker",
                  combined.toString(),
                  out.resolve("swrl").toString(),
                  enabled)
              .redirectError(out.resolve("swrl-stderr.txt").toFile())
              .redirectOutput(out.resolve("swrl-stdout.txt").toFile())
              .start();
      if (!proc.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)) {
        proc.destroyForcibly();
        throw new IOException("SWRL timeout");
      }
      if (proc.exitValue() != 0)
        throw new IOException("SWRL failed: " + Files.readString(out.resolve("swrl-stderr.txt")));
      JsonNode exchange=json.readTree(out.resolve("swrl/swrl.json").toFile());
      if(!sha256(combined).equals(exchange.path("input_sha256").asText()) || !sha256(out.resolve("swrl/materialized.owl")).equals(exchange.path("materialized_sha256").asText()) || !sha256(out.resolve("swrl/deductions.owl")).equals(exchange.path("deductions_sha256").asText()))throw new IOException("SWRL exchange hash mismatch");
      if(!exchange.path("axiom_preservation").path("status").asText().equals("RETAINED"))throw new IOException("Worker did not establish input axiom retention");
      Set<OWLAxiom> beforeExchange=parsedNonSwrl(model);
      Set<OWLAxiom> completeSavedExchange=AxiomRetention.nonSwrl(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(out.resolve("swrl/materialized.owl").toFile()));
      long workerSavedCount=exchange.path("axiom_preservation").path("saved_materialized_reload").path("observed_axiom_count").asLong(-1);
      if(workerSavedCount!=completeSavedExchange.size())throw new IOException("Materialized exchange parsed axiom count differs between worker and parent: "+workerSavedCount+" vs "+completeSavedExchange.size());
      result.put("exchange_loaded_non_swrl_axiom_count",completeSavedExchange.size());
      model = ModelFactory.createDefaultModel();
      RDFDataMgr.read(model, out.resolve("swrl/materialized.owl").toUri().toString());
      Set<OWLAxiom> loadedExchange=parsedNonSwrl(model);
      try {
        result.put("exchange_input_axiom_preservation",AxiomRetention.require(beforeExchange,loadedExchange,"input_axioms_retained_in_worker_exchange_owlapi5"));
        result.put("exchange_axiom_preservation",AxiomRetention.require(completeSavedExchange,loadedExchange,"complete_worker_file_loaded_jena_then_owlapi5"));
      }
      catch(AxiomRetention.Loss loss) { write(out.resolve("exchange-preservation-error.json"),loss.report); throw loss; }
      result.put("swrl", exchange);
      result.put("exchange_hashes_verified",true);
      Model beforePostDl=ModelFactory.createDefaultModel().add(model);
      Map<String, Object> post = validate(model, new ReasonerFactory(), incompleteCleaningQuery, missingCleaningQuery, invalidContextQuery,invalidNumericQuery,ambiguousNumericQuery,invalidNumericOutputQuery,numericControlsEnabled);
      rejectNumericAmbiguity(post);
      try(OutputStream stream=Files.newOutputStream(out.resolve("dl-post-deductions.ttl"))){RDFDataMgr.write(stream,model.difference(beforePostDl),Lang.TURTLE);}
      write(out.resolve("validation-after-swrl.json"), post);
      result.put("post_swrl_dl_materialized_triples",post.getOrDefault("dl_materialized_triples",0));
      result.put("post_swrl_dl_ms",post.getOrDefault("dl_ms",0));
      result.put("post_swrl_dl_axiom_preservation",post.get("dl_axiom_preservation"));
      result.put("cleaning_evaluation",post.get("cleaning_evaluation"));
      result.put("context_evaluation",post.get("context_evaluation"));
      result.put("numeric_evaluation",post.get("numeric_evaluation"));
      if (!post.get("status").equals("CONSISTENT"))
        throw new IOException("Materialized model not consistent/profile valid: " + post);
    }
    long reasoned = System.nanoTime();
    List<Map<String, Object>> queries = new ArrayList<>();
    for (JsonNode q : spec.path("queries")) {
      String id = q.path("id").asText(),
          text = Files.readString(root.resolve(q.path("path").asText()));
      Query parsed = QueryFactory.create(text);
      long qs = System.nanoTime();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (QueryExecution qe = QueryExecutionFactory.create(parsed, model)) {
        qe.setTimeout(60000);
        if (parsed.isSelectType()) ResultSetFormatter.outputAsJSON(bytes, qe.execSelect());
        else if (parsed.isAskType()) ResultSetFormatter.outputAsJSON(bytes, qe.execAsk());
        else throw new IOException("Unsupported query " + id);
      }
      JsonNode value = json.readTree(bytes.toByteArray());
      write(out.resolve(id + ".json"), value);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", id);
      row.put("query_ms", (System.nanoTime() - qs) / 1e6);
      row.put("result_count", value.path("results").path("bindings").size());
      if (value.has("boolean")) row.put("boolean", value.get("boolean").asBoolean());
      queries.add(row);
    }
    long queried = System.nanoTime();
    try (OutputStream stream = Files.newOutputStream(out.resolve("result-model.ttl"))) {
      RDFDataMgr.write(stream, model, Lang.TURTLE);
    }
    result.put("constructs", constructs);
    result.put("construct_added_triples",constructs.stream().mapToLong(x->((Number)x.get("added_triples")).longValue()).sum());
    result.put("raw_input_triples",rawInputTriples);
    result.put("input_named_individuals",inputNamedIndividuals);
    result.put("materialized_named_individuals",model.listSubjectsWithProperty(org.apache.jena.vocabulary.RDF.type,org.apache.jena.vocabulary.OWL2.NamedIndividual).toList().size());
    result.put("queries", queries);
    result.put("input_triples", inputTriples);
    result.put("materialized_triples", model.size());
    result.put("load_ms", (loaded - start) / 1e6);
    result.put("reasoning_stage_ms", (reasoned - loaded) / 1e6);
    result.put("query_stage_ms", (queried - reasoned) / 1e6);
    result.put("total_ms", (System.nanoTime() - start) / 1e6);
    result.put(
        "peak_heap_bytes",
        ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getType() == MemoryType.HEAP)
            .mapToLong(p -> p.getPeakUsage().getUsed())
            .sum());
    result.put("java", System.getProperty("java.version"));
    write(out.resolve("run.json"), result);
  }
  static Set<OWLAxiom> parsedNonSwrl(Model model)throws Exception {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    RDFDataMgr.write(bytes,model,Lang.RDFXML);
    OWLOntology ontology=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(bytes.toByteArray()));
    return AxiomRetention.nonSwrl(ontology);
  }
  static String sha256(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
}
