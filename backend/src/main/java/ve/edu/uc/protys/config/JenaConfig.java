package ve.edu.uc.protys.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache Jena and Fuseki configuration.
 * Initializes RDFConnection, namespace prefixes, and Jena utilities.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class JenaConfig {

    @Value("${fuseki.endpoint:http://localhost:3030/protys}")
    private String fusekiEndpoint;

    /**
     * Creates RDFConnection bean for Fuseki communication.
     *
     * @return RDFConnection
     */
    @Bean
    public RDFConnection rdfConnection() {
        log.info("Initializing RDFConnection to Fuseki endpoint: {}", fusekiEndpoint);

        try {
            RDFConnection connection = RDFConnectionFactory.connect(fusekiEndpoint);
            log.info("RDFConnection successfully created");
            return connection;
        } catch (Exception e) {
            log.error("Failed to create RDFConnection", e);
            throw new RuntimeException("Failed to initialize Fuseki connection: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes standard RDF namespace prefixes.
     *
     * @return Map of prefix to URI
     */
    @Bean
    public Map<String, String> rdfPrefixes() {
        Map<String, String> prefixes = new HashMap<>();

        // Standard vocabularies
        prefixes.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        prefixes.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        prefixes.put("owl", "http://www.w3.org/2002/07/owl#");
        prefixes.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        prefixes.put("dcterms", "http://purl.org/dc/terms/");
        prefixes.put("skos", "http://www.w3.org/2004/02/skos/core#");
        prefixes.put("foaf", "http://xmlns.com/foaf/0.1/");
        prefixes.put("vcard", "http://www.w3.org/2006/vcard/ns#");

        // PROTYS-specific vocabularies
        prefixes.put("protys", "http://protys.ontology/");
        prefixes.put("protys-kb", "http://protys.ontology/kb#");
        prefixes.put("protys-erp", "http://protys.erp/");
        prefixes.put("protys-instances", "http://protys.instances/");
        prefixes.put("protys-alignment", "http://protys.alignment/");

        // Manufacturing domain vocabularies
        prefixes.put("iso", "http://purl.org/iso/");
        prefixes.put("mfg", "http://purl.org/manufacturing/");
        prefixes.put("supply", "http://purl.org/supply-chain/");

        // SWRL
        prefixes.put("swrl", "http://www.w3.org/2003/11/swrl#");
        prefixes.put("swrlb", "http://www.w3.org/2003/11/swrlb#");

        log.info("Initialized {} RDF namespace prefixes", prefixes.size());
        return prefixes;
    }

    /**
     * Creates a default Jena Model with standard prefixes configured.
     *
     * @param prefixes the prefix map
     * @return configured Model
     */
    @Bean
    public org.apache.jena.rdf.model.Model defaultModel(Map<String, String> prefixes) {
        log.debug("Creating default Jena Model with configured prefixes");

        org.apache.jena.rdf.model.Model model = ModelFactory.createDefaultModel();

        for (Map.Entry<String, String> prefix : prefixes.entrySet()) {
            model.setNsPrefix(prefix.getKey(), prefix.getValue());
        }

        return model;
    }

    /**
     * Provides PROTYS ontology namespace.
     *
     * @return PROTYS namespace URI
     */
    @Bean
    public String protysNamespace() {
        return "http://protys.ontology/";
    }

    /**
     * Provides PROTYS ERP namespace.
     *
     * @return PROTYS ERP namespace URI
     */
    @Bean
    public String protysERPNamespace() {
        return "http://protys.erp/";
    }

    /**
     * Provides PROTYS instances namespace.
     *
     * @return PROTYS instances namespace URI
     */
    @Bean
    public String protysInstancesNamespace() {
        return "http://protys.instances/";
    }

    /**
     * Provides PROTYS alignment namespace.
     *
     * @return PROTYS alignment namespace URI
     */
    @Bean
    public String protysAlignmentNamespace() {
        return "http://protys.alignment/";
    }

    /**
     * Configuration properties for Jena Model creation.
     *
     * @return JenaModelConfig
     */
    @Bean
    public JenaModelConfig jenaModelConfig() {
        return new JenaModelConfig();
    }

    /**
     * Static configuration class for Jena settings.
     */
    public static class JenaModelConfig {
        // Jena model type
        public static final String MODEL_TYPE = "OWL";

        // Inference settings
        public static final boolean ENABLE_INFERENCE = true;
        public static final String INFERENCE_TYPE = "RDFS"; // Can be RDFS, OWL, or MICRO_RULE

        // Caching
        public static final boolean ENABLE_CACHING = true;
        public static final int CACHE_SIZE = 10000;

        // Validation
        public static final boolean VALIDATE_ON_LOAD = true;

        // Serialization
        public static final String DEFAULT_SERIALIZATION_FORMAT = "RDFXML"; // RDFXML, TURTLE, JSONLD, N3

        public String getModelType() { return MODEL_TYPE; }
        public boolean isEnableInference() { return ENABLE_INFERENCE; }
        public String getInferenceType() { return INFERENCE_TYPE; }
        public boolean isEnableCaching() { return ENABLE_CACHING; }
        public int getCacheSize() { return CACHE_SIZE; }
        public boolean isValidateOnLoad() { return VALIDATE_ON_LOAD; }
        public String getDefaultSerializationFormat() { return DEFAULT_SERIALIZATION_FORMAT; }
    }
}
