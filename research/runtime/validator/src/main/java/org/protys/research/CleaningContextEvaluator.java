package org.protys.research;

import java.util.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

/** Closed-record evaluation, distinct from OWL consistency and physical cleanliness. */
final class CleaningContextEvaluator {
  static Map<String, Object> evaluate(Model model, String incompleteQuery, String missingRecordQuery) {
    long count = model.listStatements(null, model.createProperty("http://w3id.org/protys/ontology/iso15531#requiresCleaningBetween"), (org.apache.jena.rdf.model.RDFNode)null).toList().size();
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("required_pair_count", count);
    result.put("scope", "Recorded cleaning interval and equipment only; no physical cleanliness approval.");
    if (count == 0) { result.put("status", "NOT_APPLICABLE"); return result; }
    if (incompleteQuery == null || missingRecordQuery == null) {
      result.put("status", "NOT_EVALUABLE"); result.put("reason", "Context controls unavailable"); return result;
    }
    boolean incomplete = ask(model, incompleteQuery);
    result.put("incomplete_context", incomplete);
    if (incomplete) {
      result.put("status", "NOT_EVALUABLE");
      result.put("reason", "At least one required pair lacks shared equipment or unique numerical, ordered interval endpoints. Contradictory endpoint values make the interval non-evaluable.");
      return result;
    }
    boolean missing = ask(model, missingRecordQuery);
    result.put("missing_record", missing);
    result.put("status", missing ? "MISSING_CLEANING_RECORD" : "COMPLETE_CLEANING_RECORD");
    return result;
  }
  static boolean ask(Model model, String text) {
    try (QueryExecution query = QueryExecutionFactory.create(QueryFactory.create(text), model)) {
      query.setTimeout(60000);
      return query.execAsk();
    }
  }
}
