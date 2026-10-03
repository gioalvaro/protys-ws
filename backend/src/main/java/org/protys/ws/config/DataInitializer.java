package org.protys.ws.config;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.protys.ws.model.SPARQLQuery;
import org.protys.ws.repository.SPARQLQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Loads portable query templates from the same reviewed academic catalog as the runner. */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {
  private final SPARQLQueryRepository repository;
  private final Path root;

  public DataInitializer(
      SPARQLQueryRepository repository, @Value("${protys.research.root:..}") String root) {
    this.repository = repository;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public void run(String... args) throws Exception {
    Path catalog = root.resolve("research/catalog.json");
    if (!Files.exists(catalog)) {
      log.warn("Academic catalog absent; no unverified templates initialized: {}", catalog);
      return;
    }
    if (repository.count() > 0) {
      log.info("Existing query templates preserved; no automatic replacement");
      return;
    }
    JsonNode source = new ObjectMapper().readTree(catalog.toFile());
    for (JsonNode q : source.path("queries")) {
      String id = q.path("id").asText();
      SPARQLQuery value =
          SPARQLQuery.builder()
              .id(UUID.randomUUID())
              .name(id + " - " + q.path("title").asText())
              .description(q.path("scope").asText())
              .category("Academic catalog")
              .competencyQuestion(q.path("competency_questions").toString())
              .isTemplate(true)
              .queryType(SPARQLQuery.QueryType.SELECT)
              .executionCount(0)
              .queryText(Files.readString(root.resolve(q.path("path").asText())))
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();
      repository.save(value);
    }
    log.info("Loaded21reviewedquery templates, mapping competency questions P1–P5 explicitly");
  }
}
