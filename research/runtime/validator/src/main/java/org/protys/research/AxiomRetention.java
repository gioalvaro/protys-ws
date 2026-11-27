package org.protys.research;

import java.io.IOException;
import java.util.*;
import org.semanticweb.owlapi.model.*;

/** Retention of parsed local OWL axioms, including annotation axioms. */
final class AxiomRetention {
  static Set<OWLAxiom> nonSwrl(OWLOntology ontology) {
    Set<OWLAxiom> result = new HashSet<>();
    for (OWLAxiom axiom : ontology.getAxioms())
      if (!(axiom instanceof SWRLRule)) result.add(axiom);
    return result;
  }

  static Map<String,Object> require(Set<OWLAxiom> expected, Set<OWLAxiom> obtained, String stage)
      throws Loss {
    Set<OWLAxiom> missing = new HashSet<>(expected);
    missing.removeAll(obtained);
    Map<String,Object> report = new LinkedHashMap<>();
    report.put("stage", stage);
    report.put("comparison", "EXACT_STRUCTURAL_AXIOM_SET_INCLUSION");
    report.put("expected_anonymous_individual_count", anonymousCount(expected));
    report.put("observed_anonymous_individual_count", anonymousCount(obtained));
    report.put("expected_axiom_count", expected.size());
    report.put("observed_axiom_count", obtained.size());
    report.put("retained_axiom_count", expected.size()-missing.size());
    report.put("lost_axiom_count", missing.size());
    report.put("status", missing.isEmpty() ? "RETAINED" : "INFORMATION_LOSS");
    if (!missing.isEmpty()) {
      report.put("missing_axioms", missing.stream().map(Object::toString).sorted().toList());
      if (missing.stream().anyMatch(axiom -> !axiom.getAnonymousIndividuals().isEmpty())) {
        report.put("status", "NOT_EVALUABLE_ANONYMOUS_IDENTITY");
        report.put("unmatched_axiom_count", missing.size());
        report.put("lost_axiom_count", null);
        report.put("reason", "An anonymous individual's identifier may change during serialization. No renaming correspondence is implemented; this is not evidence of scientific information loss. The evaluated case uses named identities.");
      }
      throw new Loss(report);
    }
    return report;
  }

  static int anonymousCount(Set<OWLAxiom> axioms) {
    Set<OWLAnonymousIndividual> individuals=new HashSet<>();
    for(OWLAxiom axiom:axioms)individuals.addAll(axiom.getAnonymousIndividuals());
    return individuals.size();
  }

  static final class Loss extends IOException {
    final Map<String,Object> report;
    Loss(Map<String,Object> report) {
      super(report.get("status") + " at " + report.get("stage") + ": " + report.get("missing_axioms"));
      this.report = report;
    }
  }
}
