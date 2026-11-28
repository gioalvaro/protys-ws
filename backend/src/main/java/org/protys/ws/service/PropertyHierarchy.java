package org.protys.ws.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.protys.ws.dto.DomainRangeDTO;
import org.protys.ws.dto.ModulePropertiesDTO;
import org.protys.ws.dto.PropertyDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Propiedades de objeto y de datos del grafo declarado de un módulo, sin inferencia.
 *
 * Entran las IRI declaradas como owl:ObjectProperty o owl:DatatypeProperty. Raíz es toda propiedad sin
 * superpropiedad nombrada (rdfs:subPropertyOf) dentro del mismo grupo del módulo; igual que en la
 * jerarquía de clases, una propiedad con varias superpropiedades aparece bajo cada una, los ciclos se
 * cortan y las propiedades que sólo forman parte de un ciclo se agregan como raíces. Las propiedades de
 * anotación no entran a los árboles: sólo se cuentan.
 */
class PropertyHierarchy {

    private static final Map<Resource, String> CHARACTERISTICS = new LinkedHashMap<>();

    static {
        CHARACTERISTICS.put(OWL.FunctionalProperty, "FUNCTIONAL");
        CHARACTERISTICS.put(OWL.InverseFunctionalProperty, "INVERSE_FUNCTIONAL");
        CHARACTERISTICS.put(OWL.TransitiveProperty, "TRANSITIVE");
        CHARACTERISTICS.put(OWL.SymmetricProperty, "SYMMETRIC");
    }

    private final Model model;
    private final Function<Resource, String> labeler;
    private final Comparator<Resource> byLabel;

    PropertyHierarchy(Model model, Function<Resource, String> labeler) {
        this.model = model;
        this.labeler = labeler;
        this.byLabel = Comparator.comparing((Resource r) -> labeler.apply(r).toLowerCase()).thenComparing(Resource::getURI);
    }

    ModulePropertiesDTO build() {
        Set<Resource> objects = declared(OWL.ObjectProperty);
        Set<Resource> datas = declared(OWL.DatatypeProperty);
        return ModulePropertiesDTO.builder()
                .objectProperties(tree(objects, PropertyDTO.OBJECT))
                .dataProperties(tree(datas, PropertyDTO.DATA))
                .objectPropertyCount(objects.size())
                .dataPropertyCount(datas.size())
                .annotationPropertyCount(declared(OWL.AnnotationProperty).size())
                .build();
    }

    /**
     * Propiedades de objeto (o de datos, según {@code type}) cuyo dominio declarado incluye cada clase:
     * la clase es uno de sus rdfs:domain o forma parte de una unión declarada como dominio. Las
     * propiedades van sin subpropiedades y ordenadas por etiqueta.
     */
    Map<Resource, List<PropertyDTO>> byDomainClass(String type) {
        Map<Resource, List<PropertyDTO>> result = new HashMap<>();
        List<Resource> props = new ArrayList<>(declared(PropertyDTO.OBJECT.equals(type) ? OWL.ObjectProperty : OWL.DatatypeProperty));
        props.sort(byLabel);
        for (Resource p : props) {
            PropertyDTO flat = describe(p, type);
            Set<String> classes = new HashSet<>();
            for (DomainRangeDTO d : flat.getDomains()) {
                if (d.getUri() != null) classes.add(d.getUri());
                else if ("unionOf".equals(d.getOperator())) classes.addAll(d.getMembers());
            }
            for (String c : classes) result.computeIfAbsent(model.createResource(c), k -> new ArrayList<>()).add(flat);
        }
        return result;
    }

    private Set<Resource> declared(Resource type) {
        Set<Resource> set = new HashSet<>();
        model.listSubjectsWithProperty(RDF.type, type).filterKeep(Resource::isURIResource).forEachRemaining(set::add);
        return set;
    }

    private List<PropertyDTO> tree(Set<Resource> props, String type) {
        Map<Resource, List<Resource>> children = new HashMap<>();
        List<Resource> roots = new ArrayList<>();
        for (Resource p : props) {
            boolean hasNamedParent = false;
            for (Resource parent : namedSuperProperties(p)) {
                if (props.contains(parent) && !parent.equals(p)) {
                    children.computeIfAbsent(parent, k -> new ArrayList<>()).add(p);
                    hasNamedParent = true;
                }
            }
            if (!hasNamedParent) roots.add(p);
        }
        roots.sort(byLabel);
        children.values().forEach(list -> list.sort(byLabel));

        Set<Resource> reached = new HashSet<>();
        List<PropertyDTO> result = new ArrayList<>();
        for (Resource root : roots) result.add(node(root, type, children, new HashSet<>(), reached));
        // Propiedades que sólo forman parte de un ciclo de subPropertyOf: no tienen raíz alcanzable.
        for (Resource p : props.stream().filter(p -> !reached.contains(p)).sorted(byLabel).toList()) {
            if (!reached.contains(p)) result.add(node(p, type, children, new HashSet<>(), reached));
        }
        return result;
    }

    private PropertyDTO node(Resource p, String type, Map<Resource, List<Resource>> children, Set<Resource> path, Set<Resource> reached) {
        reached.add(p);
        path.add(p);
        PropertyDTO dto = describe(p, type);
        List<PropertyDTO> subs = new ArrayList<>();
        for (Resource child : children.getOrDefault(p, List.of())) {
            if (!path.contains(child)) subs.add(node(child, type, children, path, reached));
        }
        dto.setSubproperties(subs);
        path.remove(p);
        return dto;
    }

    /** La propiedad sin subpropiedades. */
    private PropertyDTO describe(Resource p, String type) {
        Statement comment = p.getProperty(RDFS.comment);
        Set<String> inverse = new HashSet<>();
        model.listObjectsOfProperty(p, OWL.inverseOf).filterKeep(RDFNode::isURIResource)
                .forEachRemaining(o -> inverse.add(o.asResource().getURI()));
        model.listSubjectsWithProperty(OWL.inverseOf, p).filterKeep(Resource::isURIResource)
                .forEachRemaining(s -> inverse.add(s.getURI()));
        inverse.remove(p.getURI());
        List<String> characteristics = new ArrayList<>();
        CHARACTERISTICS.forEach((t, name) -> { if (model.contains(p, RDF.type, t)) characteristics.add(name); });
        return PropertyDTO.builder()
                .uri(p.getURI())
                .localName(p.getLocalName())
                .label(labeler.apply(p))
                .comment(comment != null && comment.getObject().isLiteral() ? comment.getString() : null)
                .type(type)
                .domains(terms(p, RDFS.domain))
                .ranges(terms(p, RDFS.range))
                .inverseOf(inverse.stream().sorted().toList())
                .characteristics(characteristics)
                .superPropertyUris(namedSuperProperties(p).stream().map(Resource::getURI).sorted().toList())
                .build();
    }

    private List<Resource> namedSuperProperties(Resource p) {
        List<Resource> parents = new ArrayList<>();
        model.listObjectsOfProperty(p, RDFS.subPropertyOf).forEachRemaining(o -> {
            if (o.isURIResource()) parents.add(o.asResource());
        });
        return parents;
    }

    /** Dominios o rangos declarados: nombrados por su IRI; anónimos, por las clases nombradas que los forman. */
    private List<DomainRangeDTO> terms(Resource p, org.apache.jena.rdf.model.Property predicate) {
        List<DomainRangeDTO> terms = new ArrayList<>();
        model.listObjectsOfProperty(p, predicate).forEachRemaining(o -> {
            if (o.isURIResource()) {
                terms.add(DomainRangeDTO.builder().uri(o.asResource().getURI()).members(List.of()).build());
            } else if (o.isAnon()) {
                Resource anon = o.asResource();
                String operator = "other";
                List<String> members = new ArrayList<>();
                for (org.apache.jena.rdf.model.Property op : List.of(OWL.unionOf, OWL.intersectionOf)) {
                    Resource list = anon.getPropertyResourceValue(op);
                    if (list != null && list.canAs(RDFList.class)) {
                        operator = op.getLocalName();
                        for (RDFNode m : list.as(RDFList.class).asJavaList()) if (m.isURIResource()) members.add(m.asResource().getURI());
                        break;
                    }
                }
                terms.add(DomainRangeDTO.builder().operator(operator).members(members.stream().sorted().toList()).build());
            }
        });
        terms.sort(Comparator.comparing((DomainRangeDTO t) -> t.getUri() == null ? "~" : t.getUri())
                .thenComparing(t -> String.join(" ", t.getMembers())));
        return terms;
    }
}
