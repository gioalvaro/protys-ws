package org.protys.ws.service;

import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Uses an isolated OWLAPI5 validator and a genuine OWLAPI4/SWRLAPI Drools worker. */
@Service
public class ReasoningService {
  private final long timeoutMs;
  private final Path root;
  private record CacheEntry(Model source,ClassificationResult result){}
  private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public ReasoningService(
      @Value("${protys.reasoning.timeout-ms:180000}") long timeoutMs,
      @Value("${protys.reasoning.engine:hermit-swrlapi-drools}") String engine,
      @Value("${protys.research.root:..}") String root) {
    this.timeoutMs = timeoutMs;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Getter
  @Builder(toBuilder = true)
  public static class ClassificationResult {
    private final Model deductions;
    private final long inferredTripleCount;
    private final int namedClassCount;
    private final boolean consistent;
    private final long reasoningTimeMs;
    private final String reasonerName;
    private final String validationStatus;
    private final boolean cacheHit;
    private final String errorMessage;
    private final int importedRuleCount;
    private final Map<String,Object> cleaningEvaluation;
    private final Map<String,Object> contextEvaluation;
    private final Map<String,Object> numericEvaluation;
    private final String owlValidationStatus;
  }

  public ClassificationResult classify(Model model) {
    return classify(model, List.of());
  }

  public ClassificationResult classify(Model model, List<String> enabled) {
    String key =
        hash(model)
            + ":"
            + new TreeSet<>(enabled)
            + ":OWLAPI5.1.20:HermiT1.4.5.519:SWRLAPI2.1.3:OWLAPI4.5.27:Drools7.74.1:"
            + runtimeFingerprint();
    CacheEntry cached = cache.get(key);
    if (cached != null && cached.source().isIsomorphicWith(model)) return cached.result().toBuilder().cacheHit(true).reasoningTimeMs(0).build();
    long start = System.nanoTime();
    Path dir = null;
    try {
      dir = Files.createTempDirectory("protys-reasoning-");
      try (OutputStream out = Files.newOutputStream(dir.resolve("input.owl"))) {
        RDFDataMgr.write(out, model, Lang.RDFXML);
      }
      ObjectMapper json = new ObjectMapper();
      Map<String, Object> req = new LinkedHashMap<>(Map.of(
              "files",
              List.of(dir.resolve("input.owl").toString()),
              "enabled_rules",
              enabled.isEmpty() ? "NONE" : String.join(",", enabled),
              "queries",
              List.of()));
      req.put("numeric_controls_enabled",true);
      if(!enabled.isEmpty()){
        JsonNode catalog=json.readTree(root.resolve("research/catalog.json").toFile());
        List<String> constructs=new ArrayList<>();
        for(JsonNode task:catalog.path("pre_inference_constructs"))constructs.add(task.isTextual()?task.asText():task.path("path").asText());
        req.put("pre_inference_constructs",constructs);
      }
      json.writeValue(dir.resolve("request.json").toFile(), req);
      String cp =
          Files.readString(root.resolve("research/runtime/validator/target/classpath.txt")).trim();
      String clazz =
          root.resolve("research/runtime/validator/target/classes") + File.pathSeparator + cp;
      Process p =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin/java").toString(),
                  "-Xmx2g",
                  "-Djava.awt.headless=true",
                  "-cp",
                  clazz,
                  "org.protys.research.AcademicRunner",
                  root.toString(),
                  dir.resolve("request.json").toString(),
                  dir.resolve("out").toString())
              .redirectError(dir.resolve("stderr.txt").toFile())
              .redirectOutput(dir.resolve("stdout.txt").toFile())
              .start();
      if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        p.waitFor(5, TimeUnit.SECONDS);
        throw new IOException("Reasoning timeout");
      }
      JsonNode validation = json.readTree(dir.resolve("out/validation.json").toFile());
      String status = validation.path("status").asText("NOT_EVALUATED");
      if (!status.equals("CONSISTENT"))
        return result(
            ModelFactory.createDefaultModel(), status, start, false, validation.toString(), 0).toBuilder().cleaningEvaluation(context(json,validation,"cleaning_evaluation"))
              .contextEvaluation(context(json,validation,"context_evaluation")).numericEvaluation(context(json,validation,"numeric_evaluation")).owlValidationStatus(validation.path("owl_validation_status").asText("NOT_EVALUATED")).build();
      Path postPath=dir.resolve("out/validation-after-swrl.json");
      if(Files.exists(postPath)){
        JsonNode post=json.readTree(postPath.toFile());
        if(!post.path("status").asText("NOT_EVALUATED").equals("CONSISTENT"))return result(ModelFactory.createDefaultModel(),post.path("status").asText("NOT_EVALUATED"),start,false,post.toString(),0).toBuilder().cleaningEvaluation(context(json,post,"cleaning_evaluation"))
              .contextEvaluation(context(json,post,"context_evaluation")).numericEvaluation(context(json,post,"numeric_evaluation")).owlValidationStatus(post.path("owl_validation_status").asText("NOT_EVALUATED")).build();
        validation=post;
      }
      if (p.exitValue() != 0)
        throw new IOException("Execution failed: " + Files.readString(dir.resolve("stderr.txt")));
      Model added = ModelFactory.createDefaultModel();
      for(String relative:List.of("out/dl-pre-deductions.ttl","out/dl-post-deductions.ttl","out/swrl/deductions.owl")) {
        Path file=dir.resolve(relative);if(Files.exists(file)) RDFDataMgr.read(added,file.toUri().toString());
      }
      added=added.difference(model); // Remove declarations already in the asserted graph.
      added.removeAll(null,RDF.type,OWL.Ontology);
      int imported = 0;
      if (Files.exists(dir.resolve("out/swrl/swrl.json")))
        imported =
            json.readTree(dir.resolve("out/swrl/swrl.json").toFile())
                .path("imported_rule_count")
                .asInt();
      if (imported != enabled.size())
        throw new IOException("Expected " + enabled.size() + " active rules, imported " + imported);
      ClassificationResult value =
          result(added, "CONSISTENT", start, false, null, imported).toBuilder()
              .namedClassCount(model.listSubjectsWithProperty(RDF.type, OWL.Class).toList().size())
              .cleaningEvaluation(context(json,validation,"cleaning_evaluation"))
              .contextEvaluation(context(json,validation,"context_evaluation"))
              .numericEvaluation(context(json,validation,"numeric_evaluation"))
              .owlValidationStatus(validation.path("owl_validation_status").asText("NOT_EVALUATED"))
              .build();
      if(cache.size()>=64)cache.clear(); // Bound retained materialized graphs; invalidation remains content based.
      cache.put(key, new CacheEntry(ModelFactory.createDefaultModel().add(model),value));
      return value;
    } catch (Exception e) {
      return result(
          ModelFactory.createDefaultModel(), "NOT_EVALUATED", start, false, e.toString(), 0);
    } finally {
      if (dir != null)
        try (var stream = Files.walk(dir)) {
          for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }
  }

  private ClassificationResult result(
      Model deductions, String status, long start, boolean hit, String error, int count) {
    return ClassificationResult.builder()
        .deductions(deductions)
        .inferredTripleCount(deductions.size())
        .consistent(status.equals("CONSISTENT"))
        .validationStatus(status)
        .owlValidationStatus("NOT_EVALUATED")
        .cacheHit(hit)
        .reasoningTimeMs((System.nanoTime() - start) / 1000000)
        .reasonerName("HermiT/SWRLAPI-Drools")
        .errorMessage(error)
        .importedRuleCount(count)
        .build();
  }

  private String runtimeFingerprint() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      Path catalogPath=root.resolve("research/catalog.json");
      if(Files.exists(catalogPath)){
        digest.update(Files.readAllBytes(catalogPath));
        JsonNode catalog=new ObjectMapper().readTree(catalogPath.toFile());
        for(String key:List.of("pre_inference_constructs","additional_controls","validations")) for(JsonNode task:catalog.path(key)){
          Path file=root.resolve(task.isTextual()?task.asText():task.path("path").asText());
          updateFingerprintFile(digest,file);
          if(task.path("stage_paths").isObject()) for(JsonNode stage:task.path("stage_paths")) updateFingerprintFile(digest,root.resolve(stage.asText()));
        }
      }
      for (String module : List.of("validator", "swrl-worker")) {
        Path dir = root.resolve("research/runtime/" + module);
        for (Path file : List.of(dir.resolve("pom.xml"), dir.resolve("target/classpath.txt"))) {
          if (Files.exists(file)) digest.update(Files.readAllBytes(file));
        }
        Path classes = dir.resolve("target/classes");
        if (Files.exists(classes))
          try (var walk = Files.walk(classes)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList())
              digest.update(Files.readAllBytes(file));
          }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
  private void updateFingerprintFile(MessageDigest digest,Path file)throws IOException {
    digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
    digest.update((byte)0);
    if(Files.isRegularFile(file))digest.update(Files.readAllBytes(file));
    else digest.update("MISSING_CONTROL".getBytes(StandardCharsets.UTF_8));
    digest.update((byte)0);
  }
  @SuppressWarnings("unchecked")
  private Map<String,Object> context(ObjectMapper json,JsonNode result,String key){return result.has(key)?json.convertValue(result.get(key),Map.class):Map.of("status","NOT_EVALUATED","reason","Context evaluation not available");}

  private String hash(Model m) {
    try {
      // A transport-independent bucket fingerprint, confirmed by RDF isomorphism before reuse.
      String[] lines=m.listStatements().toList().stream().map(s->term(s.getSubject().asNode())+" "+term(s.getPredicate().asNode())+" "+term(s.getObject().asNode())).toArray(String[]::new);
      Arrays.sort(lines);
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  private String term(org.apache.jena.graph.Node node){return node.isBlank()?"_:blank":org.apache.jena.riot.out.NodeFmtLib.strNT(node);}

  public boolean isConsistent(Model model) {
    ClassificationResult r = classify(model);
    if (r.getValidationStatus().equals("NOT_EVALUATED"))
      throw new IllegalStateException(r.getErrorMessage());
    return r.isConsistent();
  }

  public Model computeDeductions(Model m) {
    return classify(m).getDeductions();
  }

  public void clearCache() {
    cache.clear();
  }

  public int getCacheSize() {
    return cache.size();
  }

  @PreDestroy
  public void shutdown() {
    cache.clear();
  }
}
