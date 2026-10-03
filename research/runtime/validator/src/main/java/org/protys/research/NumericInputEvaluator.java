package org.protys.research;

import java.util.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;

/** Electrical records selected by lot/process, separate from OWL consistency. */
final class NumericInputEvaluator {
  private static final String SELECTED = """
      PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
      SELECT (COUNT(DISTINCT ?operation) AS ?count) WHERE {
        ?lot a i:Lot ; i:hasProcess ?process ; i:hasActivity ?operation .
        ?operation a i:ProcessOperation ; i:partOfProcess ?process .
      }
      """;
  static Map<String,Object> evaluate(Model model,String invalid,String ambiguous,String outputInvalid) {
    Map<String,Object> result=new LinkedHashMap<>();
    long count;
    try(QueryExecution query=QueryExecutionFactory.create(QueryFactory.create(SELECTED),model)) {
      query.setTimeout(60000);count=query.execSelect().next().getLiteral("count").getLong();
    }
    result.put("selected_operation_count",count);
    result.put("scope","Electrical energy, selected factor and GHG values for lot operations of the same manufacturing process; no global data completeness or physical measurement approval.");
    result.put("output_checked",outputInvalid!=null);
    if(count==0){result.put("status","NOT_APPLICABLE");return result;}
    if(invalid==null||ambiguous==null){result.put("status","NOT_EVALUABLE");result.put("reason","CNUM input controls unavailable");return result;}
    boolean conflict=CleaningContextEvaluator.ask(model,ambiguous);
    result.put("ambiguous_input",conflict);
    if(conflict){result.put("status","AMBIGUOUS_INPUT");result.put("reason","At least one selected operation has contradictory numerical values or more than one selected emission-factor identity. SWRL and requested queries cannot proceed.");return result;}
    boolean invalidInput=CleaningContextEvaluator.ask(model,invalid);
    boolean invalidOutput=outputInvalid!=null&&CleaningContextEvaluator.ask(model,outputInvalid);
    result.put("invalid_input",invalidInput);
    if(outputInvalid!=null)result.put("invalid_output",invalidOutput);
    result.put("status",invalidInput||invalidOutput?"NOT_EVALUABLE":"VALID");
    if(invalidInput||invalidOutput)result.put("reason","At least one selected operation lacks a valid numerical input or coherent GHG output. A partial lot aggregate must not be presented as complete.");
    return result;
  }
}
