package org.protys.research;

import java.util.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

/** Scoped declared-link control, independent of OWL consistency and global completeness. */
final class ContextIntegrityEvaluator {
  private static final String SCOPED_LINKS = """
      PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
      PREFIX t: <http://w3id.org/protys/ontology/research#>
      SELECT (COUNT(*) AS ?count) WHERE {
        SELECT DISTINCT ?lot ?operation ?kind WHERE {
          { ?lot a i:Lot ; i:hasActivity ?operation . BIND("activity" AS ?kind) }
          UNION { ?lot t:hasGHGContribution ?operation . BIND("contribution" AS ?kind) }
          UNION { ?lot a i:Lot ; t:hasPlan ?plan . ?plan i:includesActivity ?operation . BIND("plan" AS ?kind) }
        }
      }
      """;

  static Map<String,Object> evaluate(Model model, String invalidContextQuery) {
    Map<String,Object> result = new LinkedHashMap<>();
    long count;
    try (QueryExecution query = QueryExecutionFactory.create(QueryFactory.create(SCOPED_LINKS), model)) {
      query.setTimeout(60000);
      count = query.execSelect().next().getLiteral("count").getLong();
    }
    result.put("declared_link_count", count);
    result.put("scope", "Declared lot activity, GHG contribution and plan-operation links only; no global completeness approval.");
    if (count == 0) {
      result.put("status", "NOT_APPLICABLE");
      return result;
    }
    if (invalidContextQuery == null)
      throw new IllegalStateException("Declared context links cannot be evaluated: CCTX control unavailable");
    boolean invalid = CleaningContextEvaluator.ask(model, invalidContextQuery);
    result.put("invalid_declared_link", invalid);
    result.put("status", invalid ? "CONTEXT_INTEGRITY_ERROR" : "CONTEXT_VALID");
    if (invalid) result.put("reason", "At least one declared activity, contribution or plan-operation link does not match the lot manufacturing process.");
    return result;
  }
}
