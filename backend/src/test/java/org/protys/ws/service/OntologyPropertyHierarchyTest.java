package org.protys.ws.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.protys.ws.dto.DomainRangeDTO;
import org.protys.ws.dto.ModulePropertiesDTO;
import org.protys.ws.dto.OntologyClassDTO;
import org.protys.ws.dto.PropertyDTO;
import org.protys.ws.exception.ModuleNotFoundException;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.repository.AlignmentRuleRepository;
import org.protys.ws.repository.ERPConnectorRepository;
import org.protys.ws.repository.OntologyModuleRepository;

/** Árboles de propiedades del explorador (GET /api/ontology/modules/{id}/properties) y propiedades de cada clase. */
@ExtendWith(MockitoExtension.class)
class OntologyPropertyHierarchyTest {

    private static final String EX = "http://example.org/";

    @Mock private FusekiService fusekiService;
    @Mock private OntologyModuleRepository ontologyModuleRepository;
    @Mock private ReasoningService reasoningService;
    @Mock private AlignmentRuleRepository alignmentRuleRepository;
    @Mock private ERPConnectorRepository erpConnectorRepository;
    @InjectMocks private OntologyService ontologyService;

    private final Model m = ModelFactory.createDefaultModel();

    private Resource r(String local) { return m.createResource(EX + local); }

    private Resource objectProperty(String local) { Resource p = r(local); m.add(p, RDF.type, OWL.ObjectProperty); return p; }

    private Resource dataProperty(String local) { Resource p = r(local); m.add(p, RDF.type, OWL.DatatypeProperty); return p; }

    private Resource owlClass(String local) { Resource c = r(local); m.add(c, RDF.type, OWL.Class); return c; }

    private UUID module() {
        UUID id = UUID.randomUUID();
        OntologyModule module = new OntologyModule();
        module.setId(id);
        module.setNamedGraph(EX + "module#");
        when(ontologyModuleRepository.findById(id)).thenReturn(Optional.of(module));
        when(fusekiService.getModel(module.getNamedGraph())).thenReturn(m);
        return id;
    }

    private ModulePropertiesDTO properties() { return ontologyService.getPropertyHierarchy(module()); }

    private static List<String> uris(List<PropertyDTO> nodes) { return nodes.stream().map(PropertyDTO::getUri).toList(); }

    private static PropertyDTO find(List<PropertyDTO> roots, String uri) {
        ArrayDeque<PropertyDTO> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            PropertyDTO n = todo.pop();
            if (n.getUri().equals(uri)) return n;
            todo.addAll(n.getSubproperties());
        }
        throw new AssertionError("no está " + uri);
    }

    /** Raíz: sin superpropiedad nombrada dentro del grupo; las superpropiedades externas o del otro grupo no cuentan. */
    @Test
    void rootsAreThePropertiesWithoutANamedSuperpropertyInTheModule() {
        Resource hasPart = objectProperty("hasPart");
        Resource hasComponent = objectProperty("hasComponent");
        Resource usesTool = objectProperty("usesTool");
        Resource quantity = dataProperty("quantity");
        Resource weight = dataProperty("weight");
        m.add(hasComponent, RDFS.subPropertyOf, hasPart)
                .add(usesTool, RDFS.subPropertyOf, m.createResource("http://other.org/uses"))
                .add(weight, RDFS.subPropertyOf, quantity)
                .add(quantity, RDFS.subPropertyOf, hasPart);
        ModulePropertiesDTO result = properties();
        assertEquals(List.of(hasPart.getURI(), usesTool.getURI()), uris(result.getObjectProperties()));
        assertEquals(List.of(quantity.getURI()), uris(result.getDataProperties()));
        assertEquals(3, result.getObjectPropertyCount());
        assertEquals(2, result.getDataPropertyCount());
        assertEquals(List.of("http://other.org/uses"), find(result.getObjectProperties(), usesTool.getURI()).getSuperPropertyUris());
        assertEquals(List.of(hasPart.getURI()), find(result.getDataProperties(), quantity.getURI()).getSuperPropertyUris());
    }

    /** Subpropiedades anidadas, ordenadas por etiqueta y después por IRI; una con dos padres aparece bajo cada uno; los ciclos se cortan. */
    @Test
    void subpropertiesAreNestedSortedAndCyclesAreCut() {
        Resource top = objectProperty("top");
        Resource b = objectProperty("b");
        Resource a = objectProperty("a");
        Resource other = objectProperty("other");
        Resource shared = objectProperty("shared");
        m.add(b, RDFS.subPropertyOf, top).add(a, RDFS.subPropertyOf, top)
                .add(b, RDFS.label, "Beta").add(a, RDFS.label, "beta")
                .add(shared, RDFS.subPropertyOf, top).add(shared, RDFS.subPropertyOf, other);
        Resource x = objectProperty("x"), y = objectProperty("y");
        m.add(x, RDFS.subPropertyOf, y).add(y, RDFS.subPropertyOf, x);
        ModulePropertiesDTO result = properties();
        assertEquals(List.of(other.getURI(), top.getURI(), x.getURI()), uris(result.getObjectProperties()));
        PropertyDTO topNode = find(result.getObjectProperties(), top.getURI());
        assertEquals(List.of(a.getURI(), b.getURI(), shared.getURI()), uris(topNode.getSubproperties()));
        assertEquals(List.of(shared.getURI()), uris(find(result.getObjectProperties(), other.getURI()).getSubproperties()));
        assertEquals(List.of(y.getURI()), uris(find(result.getObjectProperties(), x.getURI()).getSubproperties()));
        assertEquals(List.of(), find(result.getObjectProperties(), y.getURI()).getSubproperties());
        assertEquals(7, result.getObjectPropertyCount());
    }

    /** Varios rdfs:domain y rdfs:range: van todos, cada uno como declarado. */
    @Test
    void everyDeclaredDomainAndRangeIsReturned() {
        Resource p = objectProperty("locatedIn");
        Resource d = dataProperty("code");
        m.add(p, RDFS.domain, r("Machine")).add(p, RDFS.domain, r("Operator")).add(p, RDFS.range, r("Plant"))
                .add(d, RDFS.range, XSD.xstring).add(d, RDFS.range, XSD.integer);
        ModulePropertiesDTO result = properties();
        PropertyDTO located = result.getObjectProperties().get(0);
        assertEquals(List.of(EX + "Machine", EX + "Operator"), located.getDomains().stream().map(DomainRangeDTO::getUri).toList());
        assertEquals(List.of(EX + "Plant"), located.getRanges().stream().map(DomainRangeDTO::getUri).toList());
        assertEquals(PropertyDTO.OBJECT, located.getType());
        PropertyDTO code = result.getDataProperties().get(0);
        assertEquals(PropertyDTO.DATA, code.getType());
        assertEquals(List.of(), code.getDomains());
        assertEquals(List.of(XSD.integer.getURI(), XSD.xstring.getURI()), code.getRanges().stream().map(DomainRangeDTO::getUri).toList());
    }

    /** Un dominio anónimo (owl:unionOf) se devuelve como la lista de las clases que lo componen, sin IRI propio. */
    @Test
    void anonymousUnionDomainIsReturnedAsItsMemberClasses() {
        Resource p = objectProperty("operates");
        Resource union = m.createResource();
        m.add(union, RDF.type, OWL.Class)
                .add(union, OWL.unionOf, m.createList(new RDFNode[] {r("Machine"), r("Line")}))
                .add(p, RDFS.domain, union);
        Resource restriction = m.createResource();
        m.add(restriction, RDF.type, OWL.Restriction).add(p, RDFS.range, restriction);
        PropertyDTO operates = properties().getObjectProperties().get(0);
        assertEquals(1, operates.getDomains().size());
        DomainRangeDTO domain = operates.getDomains().get(0);
        assertNull(domain.getUri());
        assertEquals("unionOf", domain.getOperator());
        assertEquals(List.of(EX + "Line", EX + "Machine"), domain.getMembers());
        assertEquals("other", operates.getRanges().get(0).getOperator());
        assertEquals(List.of(), operates.getRanges().get(0).getMembers());
    }

    /** Características declaradas e inversa, en cualquiera de los dos sentidos de owl:inverseOf. */
    @Test
    void characteristicsAndInverseAreTheDeclaredOnes() {
        Resource hasPart = objectProperty("hasPart");
        Resource partOf = objectProperty("partOf");
        Resource id = dataProperty("id");
        m.add(hasPart, RDF.type, OWL.TransitiveProperty).add(hasPart, RDF.type, OWL.InverseFunctionalProperty)
                .add(partOf, OWL.inverseOf, hasPart).add(partOf, RDF.type, OWL.FunctionalProperty)
                .add(id, RDF.type, OWL.FunctionalProperty);
        ModulePropertiesDTO result = properties();
        PropertyDTO hp = find(result.getObjectProperties(), hasPart.getURI());
        PropertyDTO po = find(result.getObjectProperties(), partOf.getURI());
        assertEquals(List.of("INVERSE_FUNCTIONAL", "TRANSITIVE"), hp.getCharacteristics());
        assertEquals(List.of(partOf.getURI()), hp.getInverseOf());
        assertEquals(List.of("FUNCTIONAL"), po.getCharacteristics());
        assertEquals(List.of(hasPart.getURI()), po.getInverseOf());
        assertEquals(List.of("FUNCTIONAL"), result.getDataProperties().get(0).getCharacteristics());
        assertEquals(List.of(), result.getDataProperties().get(0).getInverseOf());
    }

    /** Un módulo sin propiedades devuelve listas vacías, no null. */
    @Test
    void moduleWithoutPropertiesReturnsEmptyLists() {
        owlClass("Machine");
        ModulePropertiesDTO result = properties();
        assertEquals(List.of(), result.getObjectProperties());
        assertEquals(List.of(), result.getDataProperties());
        assertEquals(0, result.getObjectPropertyCount());
        assertEquals(0, result.getDataPropertyCount());
        assertEquals(0, result.getAnnotationPropertyCount());
    }

    @Test
    void missingModuleThrows() {
        UUID id = UUID.randomUUID();
        when(ontologyModuleRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ModuleNotFoundException.class, () -> ontologyService.getPropertyHierarchy(id));
        verifyNoInteractions(fusekiService);
    }

    /** Las propiedades de anotación no entran a los árboles; sólo se cuentan. */
    @Test
    void annotationPropertiesAreCountedButLeftOutOfTheTrees() {
        objectProperty("hasPart");
        Resource note = r("editorialNote");
        Resource source = r("source");
        m.add(note, RDF.type, OWL.AnnotationProperty).add(source, RDF.type, OWL.AnnotationProperty)
                .add(source, RDFS.subPropertyOf, note).add(note, RDFS.domain, r("Machine"));
        ModulePropertiesDTO result = properties();
        assertEquals(List.of(EX + "hasPart"), uris(result.getObjectProperties()));
        assertEquals(List.of(), result.getDataProperties());
        assertEquals(2, result.getAnnotationPropertyCount());
        assertEquals(1, result.getObjectPropertyCount());
    }

    /**
     * En la jerarquía de clases, cada clase trae las propiedades cuyo dominio declarado la incluye (también por una
     * unión), con el dominio completo; propertyCount es la suma de las dos listas. Una propiedad no declarada como de
     * objeto o de datos, o de anotación, no cuenta.
     */
    @Test
    void classesListThePropertiesWhoseDeclaredDomainIncludesThem() {
        Resource machine = owlClass("Machine");
        owlClass("Line");
        Resource union = m.createResource();
        m.add(union, OWL.unionOf, m.createList(new RDFNode[] {machine, r("Line")}));
        Resource operates = objectProperty("operates");
        Resource serial = dataProperty("serial");
        Resource note = r("note");
        Resource undeclared = r("undeclared");
        m.add(operates, RDFS.domain, union).add(serial, RDFS.domain, machine).add(serial, RDFS.domain, r("Tool"))
                .add(note, RDF.type, OWL.AnnotationProperty).add(note, RDFS.domain, machine)
                .add(undeclared, RDFS.domain, machine);
        UUID id = module();
        List<OntologyClassDTO> classes = ontologyService.getClassHierarchy(id);
        OntologyClassDTO m1 = classes.stream().filter(c -> c.getUri().equals(machine.getURI())).findFirst().orElseThrow();
        OntologyClassDTO line = classes.stream().filter(c -> c.getUri().endsWith("Line")).findFirst().orElseThrow();
        assertEquals(List.of(operates.getURI()), uris(m1.getObjectProperties()));
        assertEquals(List.of(serial.getURI()), uris(m1.getDataProperties()));
        assertEquals(2, m1.getPropertyCount());
        assertEquals(1, line.getPropertyCount());
        assertEquals(List.of(), line.getDataProperties());
        DomainRangeDTO unionDomain = line.getObjectProperties().get(0).getDomains().get(0);
        assertEquals("unionOf", unionDomain.getOperator());
        assertEquals(List.of(EX + "Line", EX + "Machine"), unionDomain.getMembers());
        assertEquals(2, m1.getDataProperties().get(0).getDomains().size());
        assertNull(m1.getDataProperties().get(0).getSubproperties());
        Set<String> all = new HashSet<>();
        classes.forEach(c -> all.add(c.getUri()));
        assertEquals(Set.of(machine.getURI(), EX + "Line"), all);
    }
}
