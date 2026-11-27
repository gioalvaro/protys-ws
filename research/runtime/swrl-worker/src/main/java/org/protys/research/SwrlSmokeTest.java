package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.swrlapi.core.SWRLRuleEngine;
import org.swrlapi.factory.SWRLAPIFactory;

public class SwrlSmokeTest {
  public static void main(String[] args) throws Exception {
    var m = OWLManager.createOWLOntologyManager();
    var f = m.getOWLDataFactory();
    String ns = "http://example.org/smoke#";
    var o = m.createOntology(IRI.create(ns));
    var person = f.getOWLClass(IRI.create(ns + "Person"));
    var input = f.getOWLDataProperty(IRI.create(ns + "hasInput"));
    var output = f.getOWLDataProperty(IRI.create(ns + "hasResult"));
    var a = f.getOWLNamedIndividual(IRI.create(ns + "a"));
    var b = f.getOWLNamedIndividual(IRI.create(ns + "b"));
    for (var entity : List.of(person, input, output, a, b))
      m.addAxiom(o, f.getOWLDeclarationAxiom(entity));
    for (var ind : List.of(a, b)) m.addAxiom(o, f.getOWLClassAssertionAxiom(person, ind));
    m.addAxiom(o, f.getOWLDataPropertyAssertionAxiom(input, a, 3.0));
    m.addAxiom(o, f.getOWLDataPropertyAssertionAxiom(input, b, 1.0));
    var x = f.getSWRLVariable(IRI.create(ns + "x"));
    var v = f.getSWRLVariable(IRI.create(ns + "v"));
    var mult = f.getSWRLVariable(IRI.create(ns + "mult"));
    var result = f.getSWRLVariable(IRI.create(ns + "result"));
    var two = f.getSWRLLiteralArgument(f.getOWLLiteral(2));
    var three = f.getSWRLLiteralArgument(f.getOWLLiteral(3));
    var four = f.getSWRLLiteralArgument(f.getOWLLiteral(4));
    String swrlb = "http://www.w3.org/2003/11/swrlb#";
    Set<SWRLAtom> body = new HashSet<>();
    body.add(f.getSWRLClassAtom(person, x));
    body.add(f.getSWRLDataPropertyAtom(input, x, v));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "greaterThan"), List.of(v, two)));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "greaterThanOrEqual"), List.of(v, three)));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "lessThan"), List.of(v, four)));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "notEqual"), List.of(v, four)));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "multiply"), List.of(mult, v, two)));
    body.add(f.getSWRLBuiltInAtom(IRI.create(swrlb + "divide"), List.of(result, mult, two)));
    m.addAxiom(o, f.getSWRLRule(body, Set.of(f.getSWRLDataPropertyAtom(output, x, result))));
    SWRLRuleEngine engine = SWRLAPIFactory.createSWRLRuleEngine(o);
    engine.infer();
    boolean positive =
        o.getDataPropertyAssertionAxioms(a).stream()
            .anyMatch(
                ax ->
                    ax.getProperty().equals(output)
                        && Math.abs(ax.getObject().parseDouble() - 3.0) < 1e-9);
    boolean negative =
        o.getDataPropertyAssertionAxioms(b).stream()
            .noneMatch(ax -> ax.getProperty().equals(output));
    if (!positive || !negative)
      throw new IllegalStateException("Built-ins or negative gate failed");
    Map<String,Object> report=new LinkedHashMap<>();
    report.put("status","PASS");
    report.put("java",System.getProperty("java.version"));
    report.put("positive",positive);
    report.put("negative",negative);
    report.put("builtin_count",6);
    report.put("imported_rules",engine.getNumberOfImportedSWRLRules());
    report.put("new_axioms",engine.getNumberOfInferredOWLAxioms());
    report.put("owlapi","4.5.27");
    report.put("arithmetic_regression",NumericPrecisionProbe.verify());
    report.put("axiom_retention",AxiomRetentionProbe.verify());
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),report);
  }
}
