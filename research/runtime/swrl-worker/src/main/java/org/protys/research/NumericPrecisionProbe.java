package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.swrlapi.factory.SWRLAPIFactory;

/** Regression probe for SWRLAPI 2.1.3's numerator-scale division behavior. */
public class NumericPrecisionProbe {
  public static void main(String[] args) throws Exception {
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(), verify());
  }

  static Map<String, Object> verify() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var factory = manager.getOWLDataFactory();
    var ontology = manager.createOntology(IRI.create("https://example.org/numeric-probe"));
    String ns = "https://example.org/numeric-probe#";
    String builtin = "http://www.w3.org/2003/11/swrlb#";
    var numerator = factory.getOWLDataProperty(IRI.create(ns + "numerator"));
    var denominator = factory.getOWLDataProperty(IRI.create(ns + "denominator"));
    var decimal = factory.getOWLDatatype(IRI.create("http://www.w3.org/2001/XMLSchema#decimal"));
    var floating = factory.getOWLDatatype(IRI.create("http://www.w3.org/2001/XMLSchema#double"));
    for (var entity : List.of(numerator, denominator))
      manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(entity));
    double[][] operands = {{20, 57.6666665}, {100, 40}, {1, 3}, {1.25, 2.5}, {1, 0}};
    List<OWLNamedIndividual> subjects = new ArrayList<>();
    for (int i = 0; i < operands.length; i++) {
      var subject = factory.getOWLNamedIndividual(IRI.create(ns + "case" + i));
      subjects.add(subject);
      manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(subject));
      String numeratorLexical = i == 3 ? "1.25" : Integer.toString((int) operands[i][0]);
      String denominatorLexical = i == 0 ? "57.6666665" : i == 3 ? "2.5" : Integer.toString((int) operands[i][1]);
      manager.addAxiom(ontology, factory.getOWLDataPropertyAssertionAxiom(numerator, subject, factory.getOWLLiteral(numeratorLexical, decimal)));
      manager.addAxiom(ontology, factory.getOWLDataPropertyAssertionAxiom(denominator, subject, factory.getOWLLiteral(denominatorLexical, decimal)));
    }
    Map<String, OWLDataProperty> outputs = new LinkedHashMap<>();
    for (String mode : List.of("direct", "decimalScale", "doubleScale")) {
      var output = factory.getOWLDataProperty(IRI.create(ns + mode));
      outputs.put(mode, output);
      manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(output));
      var x = factory.getSWRLVariable(IRI.create(ns + "x" + mode));
      var n = factory.getSWRLVariable(IRI.create(ns + "n" + mode));
      var d = factory.getSWRLVariable(IRI.create(ns + "d" + mode));
      var scaled = factory.getSWRLVariable(IRI.create(ns + "scaled" + mode));
      var result = factory.getSWRLVariable(IRI.create(ns + "result" + mode));
      Set<SWRLAtom> body = new LinkedHashSet<>();
      body.add(factory.getSWRLDataPropertyAtom(numerator, x, n));
      body.add(factory.getSWRLDataPropertyAtom(denominator, x, d));
      body.add(factory.getSWRLBuiltInAtom(IRI.create(builtin + "greaterThan"), List.of(d, factory.getSWRLLiteralArgument(factory.getOWLLiteral(0)))));
      SWRLDArgument dividend = n;
      if (!mode.equals("direct")) {
        var one = factory.getSWRLLiteralArgument(factory.getOWLLiteral("1.0000000000000000", mode.equals("decimalScale") ? decimal : floating));
        body.add(factory.getSWRLBuiltInAtom(IRI.create(builtin + "multiply"), List.of(scaled, n, one)));
        dividend = scaled;
      }
      body.add(factory.getSWRLBuiltInAtom(IRI.create(builtin + "divide"), List.of(result, dividend, d)));
      manager.addAxiom(ontology, factory.getSWRLRule(body, Set.of(factory.getSWRLDataPropertyAtom(output, x, result))));
    }
    var engine = SWRLAPIFactory.createSWRLRuleEngine(ontology);
    engine.infer();
    List<Map<String, Object>> cases = new ArrayList<>();
    for (int i = 0; i < operands.length; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("numerator", operands[i][0]);
      item.put("denominator", operands[i][1]);
      for (var entry : outputs.entrySet()) {
        var values = ontology.getDataPropertyAssertionAxioms(subjects.get(i)).stream().filter(ax -> ax.getProperty().equals(entry.getValue())).map(ax -> ax.getObject()).toList();
        var literals = values.stream().map(Object::toString).sorted().toList();
        item.put(entry.getKey(), literals);
        if (entry.getKey().equals("decimalScale")) {
          if (operands[i][1] == 0) {
            if (!values.isEmpty()) throw new IllegalStateException("Zero divisor produced a value");
          } else if (values.size() != 1 || Math.abs(values.get(0).parseDouble() - operands[i][0] / operands[i][1]) > 1e-12) {
            throw new IllegalStateException("Decimal precision regression failed for " + item);
          }
        }
      }
      cases.add(item);
    }
    return Map.of("status", "PASS", "swrlapi", "2.1.3", "owlapi", "4.5.27", "cases", cases,
        "precision_policy", "Multiply dividend by decimal 1.0000000000000000 before guarded division; same mathematical ratio, at least 16 fractional decimal places before rounding.");
  }
}
