package ve.edu.uc.protys;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Main Spring Boot application class for PROTYS-WS.
 * PROTYS-WS is a comprehensive ontology-based standards management system that integrates
 * OWL ontologies, SPARQL querying, alignment rules, ERP connectors, and standard incorporation.
 *
 * The system provides REST APIs for:
 * - Dashboard monitoring and system health
 * - Ontology module and individual management
 * - SPARQL query execution and templating
 * - Alignment rules and reasoning
 * - ERP connector integration and data materialization
 * - Multi-step standard incorporation wizard
 *
 * @author PROTYS Development Team
 * @version 3.0
 */
@SpringBootApplication
public class ProtyswsApplication {

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ProtyswsApplication.class, args);
    }

    /**
     * Configure OpenAPI (Swagger 3.0) documentation
     *
     * @return OpenAPI configuration with application metadata and server information
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PROTYS-WS REST API")
                        .version("3.0.0")
                        .description("""
                                PROTYS-WS (Ontology-based PROcess and Technology Standards-based Web Services) is an enterprise-grade \
                                REST API for comprehensive ontology and standards management.

                                Core Features:
                                - Ontology Module Management: Upload, validate, and manage OWL ontologies
                                - SPARQL Query Execution: Execute SPARQL queries with templating and result export
                                - Alignment Rules: Define and execute SWRL-based alignment rules
                                - ERP Integration: Connect to ERP systems via JDBC and materialize RDF data using R2RML
                                - Standard Incorporation: Multi-step wizard for integrating new standards
                                - Dashboard Monitoring: Real-time system statistics and health monitoring

                                The API provides comprehensive support for ontology engineering workflows including consistency \
                                validation, reasoning, and inference verification.
                                """)
                        .contact(new Contact()
                                .name("PROTYS Development Team")
                                .url("https://www.uc.edu.ve")
                                .email("protys@uc.edu.ve"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development Server"),
                        new Server()
                                .url("https://api.protys.ingar.conicet.gob.ar")
                                .description("Production Server")
                ));
    }
}
