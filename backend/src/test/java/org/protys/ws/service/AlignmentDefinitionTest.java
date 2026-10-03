package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class AlignmentDefinitionTest {
  @Test void removesExecutableListsButRetainsDomainAssertionsAndVariableDeclarations(){
    Model m=ModelFactory.createDefaultModel();String swrl="http://www.w3.org/2003/11/swrl#";
    Resource rule=m.createResource("https://example.org/R03"),variable=m.createResource("https://example.org/var"),atom=m.createResource(),x=m.createResource("https://example.org/x");
    RDFList list=m.createList(new RDFNode[]{atom});
    m.add(rule,RDF.type,m.createResource(swrl+"Imp")).add(rule,m.createProperty(swrl+"body"),list).add(atom,RDF.type,m.createResource(swrl+"ClassAtom")).add(atom,m.createProperty(swrl+"argument1"),variable).add(variable,RDF.type,m.createResource(swrl+"Variable")).add(x,RDF.type,m.createResource("https://example.org/DomainClass"));
    Model clean=AlignmentService.withoutRuleDefinitions(m);
    assertFalse(clean.contains(rule,null,(RDFNode)null));assertFalse(clean.contains(atom,null,(RDFNode)null));assertFalse(clean.contains(list,null,(RDFNode)null));
    assertTrue(clean.contains(variable,RDF.type,m.createResource(swrl+"Variable")));assertTrue(clean.contains(x,RDF.type,m.createResource("https://example.org/DomainClass")));assertTrue(m.contains(rule,RDF.type,m.createResource(swrl+"Imp")));
  }
}
