package ve.edu.uc.protys.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for PROTYS-WS.
 * Configures allowed origins, methods, and headers for React frontend communication.
 */
@Configuration
@Slf4j
public class ProtysCorsConfig {

    /**
     * Configures CORS mappings globally for all endpoints.
     *
     * @return WebMvcConfigurer
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        log.info("Configuring CORS settings for PROTYS-WS");

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                log.debug("Setting up CORS mappings");

                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:3000",
                                "http://localhost:8080",
                                "http://127.0.0.1:3000",
                                "http://127.0.0.1:8080",
                                "http://localhost",
                                "http://127.0.0.1"
                        )
                        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                        .allowedHeaders("*")
                        .exposedHeaders(
                                "Content-Type",
                                "Authorization",
                                "X-Total-Count",
                                "X-Page-Number",
                                "X-Page-Size",
                                "X-Request-Id",
                                "X-Error-Message"
                        )
                        .allowCredentials(true)
                        .maxAge(3600)
                        .applyPermitDefaultValues();

                registry.addMapping("/actuator/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "OPTIONS")
                        .maxAge(3600);

                registry.addMapping("/swagger-ui/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "OPTIONS")
                        .maxAge(3600);

                log.info("CORS mappings configured successfully");
            }
        };
    }

    /**
     * CORS configuration properties.
     *
     * @return CorProperties
     */
    @Bean
    @ConfigurationProperties(prefix = "cors")
    public CorsProperties corsProperties() {
        return new CorsProperties();
    }

    /**
     * CORS configuration properties holder.
     */
    public static class CorsProperties {
        private List<String> allowedOrigins = Arrays.asList(
                "http://localhost:3000",
                "http://localhost:8080",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:8080"
        );

        private List<String> allowedMethods = Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        );

        private List<String> allowedHeaders = Arrays.asList(
                "Content-Type",
                "Authorization",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        );

        private List<String> exposedHeaders = Arrays.asList(
                "Content-Type",
                "Authorization",
                "X-Total-Count",
                "X-Page-Number",
                "X-Page-Size",
                "X-Request-Id"
        );

        private long maxAge = 3600L;
        private boolean allowCredentials = true;

        // Getters and Setters
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }

        public List<String> getExposedHeaders() { return exposedHeaders; }
        public void setExposedHeaders(List<String> exposedHeaders) { this.exposedHeaders = exposedHeaders; }

        public long getMaxAge() { return maxAge; }
        public void setMaxAge(long maxAge) { this.maxAge = maxAge; }

        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
    }
}
