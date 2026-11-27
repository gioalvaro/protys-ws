package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

/**
 * P6 y P7 con el caso de pintura más paint-case-study-instances.ttl: carga la configuración integrada de
 * research/catalog.json y el archivo de instancias, razona con los motores reales y aislados (perfil OWL 2 DL,
 * HermiT, SWRLAPI/Drools con las 22 reglas activas) y compara P6 y P7 con sus respuestas esperadas, calculadas sin
 * pasar por la red (research/integration/consultas-p6-p7/esperadas).
 */
class PaintCaseInstancesP6P7IntegrationTest {
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
  private static final Path EVIDENCIA = ROOT.resolve("research/integration/consultas-p6-p7");
  private static final Path INSTANCIAS =
      Path.of(System.getProperty("protys.instancias", ROOT.resolve("ontologies/paint-case-study-instances.ttl").toString()));

  @Test
  void theNetworkWithTheInstancesIsConsistentAndAnswersP6AndP7AsExpected() throws Exception {
    JsonNode catalogo = new ObjectMapper().readTree(ROOT.resolve("research/catalog.json").toFile());
    JsonNode integrada = null;
    for (JsonNode c : catalogo.path("configurations")) if ("integrated".equals(c.path("id").asText())) integrada = c;
    assertNotNull(integrada, "configuración integrated en research/catalog.json");

    Model red = ModelFactory.createDefaultModel();
    for (String grupo : List.of("tbox_paths", "abox_paths", "rules_paths"))
      for (JsonNode ruta : integrada.path(grupo)) RDFDataMgr.read(red, ROOT.resolve(ruta.asText()).toString());
    RDFDataMgr.read(red, INSTANCIAS.toString());
    List<String> reglas = new ArrayList<>();
    integrada.path("enabled_rule_ids").forEach(r -> reglas.add(r.asText()));
    assertEquals(22, reglas.size());

    var resultado = new ReasoningService(180000, "hermit-swrlapi-drools", ROOT.toString()).classify(red, reglas);
    assertEquals("CONSISTENT", resultado.getValidationStatus(),
        "perfil OWL 2 DL y consistencia con las 22 reglas: " + resultado.getErrorMessage());
    assertEquals(22, resultado.getImportedRuleCount());

    Model razonado = ModelFactory.createDefaultModel().add(red).add(resultado.getDeductions());
    for (String consulta : List.of("P6", "P7")) {
      assertEquals(esperadas(consulta), obtenidas(razonado, consulta), consulta + " razonada contra lo esperado");
      assertEquals(esperadas(consulta), obtenidas(red, consulta), consulta + " sin razonamiento contra lo esperado");
    }
  }

  private Set<Map<String, String>> obtenidas(Model modelo, String consulta) throws Exception {
    Query q = QueryFactory.create(Files.readString(EVIDENCIA.resolve("consultas/" + consulta + ".rq")));
    Set<Map<String, String>> filas = new HashSet<>();
    try (QueryExecution ejecucion = QueryExecutionFactory.create(q, modelo)) {
      ResultSet rs = ejecucion.execSelect();
      while (rs.hasNext()) {
        QuerySolution s = rs.next();
        Map<String, String> fila = new TreeMap<>();
        rs.getResultVars().forEach(v -> {
          if (s.get(v) != null) fila.put(v, s.get(v).isLiteral() ? s.getLiteral(v).getLexicalForm() : s.getResource(v).getURI());
        });
        filas.add(fila);
      }
    }
    return filas;
  }

  private Set<Map<String, String>> esperadas(String consulta) throws Exception {
    JsonNode json = new ObjectMapper().readTree(EVIDENCIA.resolve("esperadas/" + consulta + ".json").toFile());
    Set<Map<String, String>> filas = new HashSet<>();
    for (JsonNode b : json.path("results").path("bindings")) {
      Map<String, String> fila = new TreeMap<>();
      b.fields().forEachRemaining(e -> fila.put(e.getKey(), e.getValue().path("value").asText()));
      filas.add(fila);
    }
    assertFalse(filas.isEmpty(), "respuesta esperada de " + consulta);
    return filas;
  }
}
