package org.protys.ws.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.protys.ws.dto.IndividualEditResult;
import org.protys.ws.dto.IndividualRequest;
import org.protys.ws.dto.OntologyIndividualDTO;
import org.protys.ws.exception.ProtysConflictException;
import org.protys.ws.exception.ProtysNotFoundException;
import org.protys.ws.exception.ProtysValidationException;
import org.protys.ws.model.OntologyModule;
import org.protys.ws.repository.OntologyModuleRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Alta, lectura, modificación y baja de individuos de la red.
 *
 * <p>El individuo se identifica siempre por su IRI absoluto, que viaja como parámetro de consulta
 * {@code uri} (codificado como URL). Cada edición escribe el grafo del módulo que contiene la clase (alta)
 * o el individuo (modificación y baja), guarda una copia del grafo anterior para poder deshacerla y, al
 * terminar, ejecuta la validación de consistencia del módulo y la devuelve con su duración.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndividualService {

    static final String GENERATED_PREFIX = "http://w3id.org/protys/instances/";
    private static final int UNDO_CAPACITY = 20;

    private final FusekiService fusekiService;
    private final OntologyModuleRepository ontologyModuleRepository;
    private final OntologyService ontologyService;

    private record Snapshot(UUID moduleId, String namedGraph, Model before, Model after, String uri) {}

    private final Map<String, Snapshot> snapshots = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
            return size() > UNDO_CAPACITY;
        }
    });

    private record Located(OntologyModule module, Model model) {}

    // ---------------------------------------------------------------- lectura

    /** Individuos cuyo rdf:type es exactamente la clase dada, en todos los módulos registrados. */
    public List<OntologyIndividualDTO> listByClass(String classUri) {
        requireIri("classUri", classUri);
        Resource cls = ResourceFactory.createResource(classUri);
        Map<String, OntologyIndividualDTO> out = new TreeMap<>();
        for (OntologyModule module : modules()) {
            Model model = fusekiService.getModel(module.getNamedGraph());
            model.listSubjectsWithProperty(RDF.type, cls).forEachRemaining(subject -> {
                if (subject.isURIResource()) {
                    out.putIfAbsent(subject.getURI(), OntologyIndividualDTO.builder()
                            .uri(subject.getURI())
                            .localName(subject.getLocalName())
                            .label(label(subject))
                            .type(classUri)
                            .build());
                }
            });
        }
        return new ArrayList<>(out.values());
    }

    public OntologyIndividualDTO get(String uri) {
        requireIri("uri", uri);
        Located located = locate(uri).orElseThrow(() -> new ProtysNotFoundException("Individual not found: " + uri));
        return toDto(located.model().getResource(uri));
    }

    // ---------------------------------------------------------------- edición

    public synchronized IndividualEditResult create(String classUri, IndividualRequest request) {
        requireIri("classUri", classUri);
        IndividualRequest body = request == null ? new IndividualRequest() : request;
        String uri = blank(body.getUri()) ? GENERATED_PREFIX + UUID.randomUUID() : body.getUri().trim();
        requireIri("uri", uri);
        requirePropertyIris(body.getProperties());

        Resource cls = ResourceFactory.createResource(classUri);
        OntologyModule target = null;
        Model targetModel = null;
        for (OntologyModule module : modules()) {
            Model model = fusekiService.getModel(module.getNamedGraph());
            if (model.contains(ResourceFactory.createResource(uri), null, (RDFNode) null)) {
                throw new ProtysConflictException("Individual already exists: " + uri + " (module " + module.getName() + ")");
            }
            if (target == null && (model.contains(cls, RDF.type, OWL.Class) || model.contains(cls, RDF.type, RDFS.Class))) {
                target = module;
                targetModel = model;
            }
        }
        if (target == null) {
            throw new ProtysValidationException("Class not declared in any registered module: " + classUri,
                    "classUri must be an owl:Class or rdfs:Class declared in a registered module");
        }
        Model before = copy(targetModel);
        Resource individual = targetModel.createResource(uri);
        individual.addProperty(RDF.type, targetModel.createResource(classUri));
        individual.addProperty(RDF.type, org.apache.jena.vocabulary.OWL2.NamedIndividual);
        if (!blank(body.getLabel())) individual.addProperty(RDFS.label, body.getLabel().trim());
        addProperties(targetModel, individual, body.getProperties());
        return write("CREATE", uri, target, before, targetModel);
    }

    public synchronized IndividualEditResult update(String uri, IndividualRequest request) {
        requireIri("uri", uri);
        IndividualRequest body = request == null ? new IndividualRequest() : request;
        if (!blank(body.getUri()) && !body.getUri().trim().equals(uri)) {
            throw new ProtysValidationException("The IRI of an individual cannot be changed", "body.uri differs from the uri parameter");
        }
        requirePropertyIris(body.getProperties());
        Located located = locate(uri).orElseThrow(() -> new ProtysNotFoundException("Individual not found: " + uri));
        Model model = located.model();
        Model before = copy(model);
        Resource individual = model.getResource(uri);
        if (body.getLabel() != null) {
            model.removeAll(individual, RDFS.label, null);
            if (!body.getLabel().isBlank()) individual.addProperty(RDFS.label, body.getLabel().trim());
        }
        if (body.getProperties() != null) {
            for (Map.Entry<String, String> entry : body.getProperties().entrySet()) {
                Property property = model.createProperty(entry.getKey());
                model.removeAll(individual, property, null);
                if (entry.getValue() != null && !entry.getValue().isBlank()) individual.addProperty(property, entry.getValue());
            }
        }
        return write("UPDATE", uri, located.module(), before, model);
    }

    public synchronized IndividualEditResult delete(String uri) {
        requireIri("uri", uri);
        Located located = locate(uri).orElseThrow(() -> new ProtysNotFoundException("Individual not found: " + uri));
        Model model = located.model();
        Model before = copy(model);
        Resource individual = model.getResource(uri);
        model.removeAll(individual, null, null);
        model.removeAll(null, null, individual);
        return write("DELETE", uri, located.module(), before, model);
    }

    /** Deshace una edición si el grafo no cambió desde entonces; si cambió, 409. */
    public synchronized IndividualEditResult undo(String editId) {
        Snapshot snapshot = snapshots.get(editId);
        if (snapshot == null) throw new ProtysNotFoundException("Edit not found or no longer undoable: " + editId);
        Model current = fusekiService.getModel(snapshot.namedGraph());
        if (!current.isIsomorphicWith(snapshot.after())) {
            throw new ProtysConflictException("The module graph changed after this edit; it cannot be undone automatically");
        }
        long start = System.nanoTime();
        fusekiService.putModel(snapshot.namedGraph(), snapshot.before());
        long writeMs = (System.nanoTime() - start) / 1_000_000;
        snapshots.remove(editId);
        OntologyModule module = ontologyModuleRepository.findById(snapshot.moduleId()).orElse(null);
        return IndividualEditResult.builder()
                .operation("UNDO").uri(snapshot.uri())
                .moduleId(snapshot.moduleId().toString()).moduleName(module == null ? null : module.getName())
                .writeTimeMs(writeMs)
                .validation(validate(snapshot.moduleId()))
                .build();
    }

    // ---------------------------------------------------------------- apoyo

    private IndividualEditResult write(String operation, String uri, OntologyModule module, Model before, Model after) {
        long start = System.nanoTime();
        fusekiService.putModel(module.getNamedGraph(), after);
        long writeMs = (System.nanoTime() - start) / 1_000_000;
        String editId = UUID.randomUUID().toString();
        snapshots.put(editId, new Snapshot(module.getId(), module.getNamedGraph(), before, copy(after), uri));
        log.info("{} {} in module {} ({} ms)", operation, uri, module.getName(), writeMs);
        return IndividualEditResult.builder()
                .operation(operation).uri(uri)
                .moduleId(module.getId().toString()).moduleName(module.getName())
                .editId(editId).writeTimeMs(writeMs)
                .validation(validate(module.getId()))
                .build();
    }

    /** Validación de consistencia del módulo editado: perfil OWL 2 DL y consistencia con HermiT (ReasoningService). */
    IndividualEditResult.Validation validate(UUID moduleId) {
        long start = System.nanoTime();
        try {
            ReasoningService.ClassificationResult result = ontologyService.validateDetailed(moduleId);
            long ms = (System.nanoTime() - start) / 1_000_000;
            String status = result.getValidationStatus();
            List<String> violations = new ArrayList<>();
            String message = null;
            if (result.getErrorMessage() != null) {
                try {
                    JsonNode error = new ObjectMapper().readTree(result.getErrorMessage());
                    error.path("profile_violations").forEach(v -> violations.add(v.asText()));
                    message = error.path("error").asText(null);
                } catch (Exception notJson) {
                    message = result.getErrorMessage();
                }
            }
            if ("NOT_EVALUATED".equals(status) || "CONSISTENT".equals(status)) {
                return IndividualEditResult.Validation.builder().status(status)
                        .consistent("NOT_EVALUATED".equals(status) ? null : Boolean.TRUE)
                        .timeMs(ms).cacheHit(result.isCacheHit()).message(message).violations(violations).build();
            }
            return IndividualEditResult.Validation.builder().status("INCONSISTENT").consistent(Boolean.FALSE)
                    .timeMs(ms).cacheHit(result.isCacheHit()).message(message == null ? status : message).violations(violations).build();
        } catch (Exception failure) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("Validation after edit failed: {}", failure.getMessage());
            return IndividualEditResult.Validation.builder().status("NOT_EVALUATED").timeMs(ms)
                    .message("Validation could not run: " + failure.getMessage()).violations(List.of()).build();
        }
    }

    private Optional<Located> locate(String uri) {
        Resource subject = ResourceFactory.createResource(uri);
        for (OntologyModule module : modules()) {
            Model model = fusekiService.getModel(module.getNamedGraph());
            if (model.contains(subject, RDF.type, (RDFNode) null)) return Optional.of(new Located(module, model));
        }
        return Optional.empty();
    }

    private List<OntologyModule> modules() {
        return ontologyModuleRepository.findAll().stream().filter(AlignmentService::isPermanentModule)
                .sorted(Comparator.comparing(OntologyModule::getLoadedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private OntologyIndividualDTO toDto(Resource resource) {
        List<String> types = new ArrayList<>();
        Map<String, List<String>> properties = new TreeMap<>();
        resource.listProperties().forEachRemaining(statement -> {
            RDFNode object = statement.getObject();
            String value = object.isURIResource() ? object.asResource().getURI()
                    : object.isLiteral() ? object.asLiteral().getLexicalForm() : object.toString();
            if (statement.getPredicate().equals(RDF.type)) {
                if (!org.apache.jena.vocabulary.OWL2.NamedIndividual.equals(object)) types.add(value);
            } else {
                properties.computeIfAbsent(statement.getPredicate().getURI(), k -> new ArrayList<>()).add(value);
            }
        });
        Collections.sort(types);
        return OntologyIndividualDTO.builder()
                .uri(resource.getURI()).localName(resource.getLocalName()).label(label(resource))
                .type(types.isEmpty() ? null : types.get(0)).types(types).properties(properties)
                .build();
    }

    private static String label(Resource resource) {
        Statement label = resource.getProperty(RDFS.label);
        return label == null ? null : label.getObject().isLiteral() ? label.getString() : label.getObject().toString();
    }

    private static void addProperties(Model model, Resource individual, Map<String, String> properties) {
        if (properties == null) return;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                individual.addProperty(model.createProperty(entry.getKey()), entry.getValue());
            }
        }
    }

    private static void requirePropertyIris(Map<String, String> properties) {
        if (properties == null) return;
        for (String key : properties.keySet()) requireIri("properties key", key);
    }

    /** IRI absoluto, sin espacios ni caracteres que RDF no admite en un IRI. */
    static void requireIri(String field, String value) {
        if (blank(value)) throw new ProtysValidationException("Missing " + field, field + " is required");
        String v = value.trim();
        if (v.chars().anyMatch(c -> c <= ' ' || "<>\"{}|^`\\".indexOf(c) >= 0)) {
            throw new ProtysValidationException("Malformed IRI in " + field + ": " + value, "an IRI cannot contain spaces or <>\"{}|^`\\");
        }
        try {
            URI parsed = new URI(v);
            if (!parsed.isAbsolute()) throw new URISyntaxException(v, "not absolute");
        } catch (URISyntaxException e) {
            throw new ProtysValidationException("Malformed IRI in " + field + ": " + value, field + " must be an absolute IRI (with scheme)");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Model copy(Model model) {
        return ModelFactory.createDefaultModel().add(model);
    }
}
