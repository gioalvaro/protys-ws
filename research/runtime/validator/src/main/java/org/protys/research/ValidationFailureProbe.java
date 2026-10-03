package org.protys.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.reasoner.*;

/** Exercises failures after a real HermiT consistency check, including partial export. */
public class ValidationFailureProbe {
  static Model input() {
    return RDFParser.fromString("""
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix f: <https://example.org/validation-failure#> .
        f:Parent a owl:Class . f:Child a owl:Class ; rdfs:subClassOf f:Parent .
        f:related a owl:ObjectProperty . f:specific a owl:ObjectProperty ; rdfs:subPropertyOf f:related .
        f:x a owl:NamedIndividual, f:Child ; f:specific f:y .
        f:y a owl:NamedIndividual, f:Parent .
        """).lang(Lang.TURTLE).toModel();
  }

  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> cases = new ArrayList<>();
    Model positive = input();
    Map<String, Object> valid = AcademicRunner.validate(positive);
    if (!"CONSISTENT".equals(valid.get("status")) || !positive.contains(
        positive.createResource("https://example.org/validation-failure#x"),
        org.apache.jena.vocabulary.RDF.type,
        positive.createResource("https://example.org/validation-failure#Parent"))) {
      throw new IllegalStateException("Real HermiT classification control failed: " + valid);
    }
    cases.add(Map.of("operation", "real_consistency_and_named_classification", "status", valid.get("status"), "pass", true));
    for (String failedMethod : List.of("precomputeInferences", "getObjectPropertyValues", "getUnsatisfiableClasses", "dispose")) {
      AtomicBoolean checked = new AtomicBoolean();
      AtomicBoolean thrown = new AtomicBoolean();
      OWLReasonerFactory realFactory = new ReasonerFactory();
      OWLReasonerFactory factory = (OWLReasonerFactory) Proxy.newProxyInstance(
          OWLReasonerFactory.class.getClassLoader(), new Class<?>[] {OWLReasonerFactory.class},
          (proxy, method, arguments) -> {
            Object result = invoke(method, realFactory, arguments);
            if (!(result instanceof OWLReasoner real)) return result;
            return Proxy.newProxyInstance(OWLReasoner.class.getClassLoader(), new Class<?>[] {OWLReasoner.class},
                (reasonerProxy, operation, parameters) -> {
                  if (checked.get() && operation.getName().equals(failedMethod)) {
                    if (failedMethod.equals("dispose")) invoke(operation, real, parameters);
                    thrown.set(true);
                    throw new IllegalStateException("Induced post-consistency failure at " + failedMethod);
                  }
                  Object value = invoke(operation, real, parameters);
                  if (operation.getName().equals("isConsistent") && Boolean.TRUE.equals(value)) checked.set(true);
                  return value;
                });
          });
      Map<String, Object> failed = AcademicRunner.validate(input(), factory);
      boolean passed = checked.get() && thrown.get() && "NOT_EVALUATED".equals(failed.get("status"))
          && String.valueOf(failed.get("error")).contains(failedMethod);
      cases.add(Map.of("operation", failedMethod, "real_consistency_previously_true", checked.get(),
          "failure_exercised", thrown.get(), "status", failed.get("status"), "pass", passed,
          "error", String.valueOf(failed.get("error"))));
      if (!passed) throw new IllegalStateException("Post-consistency failure incorrectly approved: " + failed);
    }
    Model lostAnnotation=input();
    var named=lostAnnotation.createResource("https://example.org/validation-failure#x");
    lostAnnotation.add(named,org.apache.jena.vocabulary.RDFS.label,lostAnnotation.createLiteral("Anotación original","es"));
    Map<String,Object> lost=AcademicRunner.validate(lostAnnotation,mutationAfterConsistency(() -> lostAnnotation.removeAll(named,org.apache.jena.vocabulary.RDFS.label,(RDFNode)null)));
    var lostDetails=(Map<?,?>)lost.get("axiom_preservation_error");
    boolean rejected="NOT_EVALUATED".equals(lost.get("status"))&&lostDetails!=null&&"INFORMATION_LOSS".equals(lostDetails.get("status"))&&((Number)lostDetails.get("lost_axiom_count")).intValue()==1;
    cases.add(Map.of("operation","annotation_axiom_removed_after_real_consistency","status",lost.get("status"),"preservation",lostDetails==null?Map.of():lostDetails,"pass",rejected));
    if(!rejected)throw new IllegalStateException("Loss of a parsed annotation axiom incorrectly approved: "+lost);

    Model anonymous=input();
    var blank=anonymous.createResource(AnonId.create("original-anonymous-individual"));
    anonymous.add(blank,org.apache.jena.vocabulary.RDF.type,anonymous.createResource("https://example.org/validation-failure#Parent"));
    anonymous.add(anonymous.createResource("https://example.org/validation-failure#x"),anonymous.createProperty("https://example.org/validation-failure#related"),blank);
    Map<String,Object> renamed=AcademicRunner.validate(anonymous,mutationAfterConsistency(() -> {
      List<Statement> statements=anonymous.listStatements().toList();
      Resource replacement=anonymous.createResource(AnonId.create("changed-anonymous-individual"));
      for(Statement statement:statements)if(statement.getSubject().equals(blank)||statement.getObject().equals(blank)){
        anonymous.remove(statement);
        anonymous.add(statement.getSubject().equals(blank)?replacement:statement.getSubject(),statement.getPredicate(),statement.getObject().equals(blank)?replacement:statement.getObject());
      }
    }));
    var anonymousDetails=(Map<?,?>)renamed.get("axiom_preservation_error");
    boolean unevaluable="NOT_EVALUATED".equals(renamed.get("status"))&&anonymousDetails!=null&&"NOT_EVALUABLE_ANONYMOUS_IDENTITY".equals(anonymousDetails.get("status"))&&anonymousDetails.get("lost_axiom_count")==null;
    cases.add(Map.of("operation","anonymous_individual_identifier_change","status",renamed.get("status"),"preservation",anonymousDetails==null?Map.of():anonymousDetails,"pass",unevaluable));
    if(!unevaluable)throw new IllegalStateException("Anonymous identifier change incorrectly approved or claimed as proven loss: "+renamed);
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),
        Map.of("status", "PASS", "scope", "Induced failures after real HermiT consistency; production uses real unwrapped reasoner", "cases", cases));
  }

  static OWLReasonerFactory mutationAfterConsistency(Runnable mutation) {
    AtomicBoolean checked=new AtomicBoolean(),mutated=new AtomicBoolean();
    OWLReasonerFactory realFactory=new ReasonerFactory();
    return (OWLReasonerFactory)Proxy.newProxyInstance(OWLReasonerFactory.class.getClassLoader(),new Class<?>[]{OWLReasonerFactory.class},(proxy,method,arguments)->{
      Object result=invoke(method,realFactory,arguments);
      if(!(result instanceof OWLReasoner real))return result;
      return Proxy.newProxyInstance(OWLReasoner.class.getClassLoader(),new Class<?>[]{OWLReasoner.class},(reasonerProxy,operation,parameters)->{
        if(checked.get()&&operation.getName().equals("precomputeInferences")&&mutated.compareAndSet(false,true))mutation.run();
        Object value=invoke(operation,real,parameters);
        if(operation.getName().equals("isConsistent")&&Boolean.TRUE.equals(value))checked.set(true);
        return value;
      });
    });
  }

  static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
    try { return method.invoke(target, arguments); }
    catch (InvocationTargetException error) { throw error.getCause(); }
  }
}
