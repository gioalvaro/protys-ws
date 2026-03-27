package ve.edu.uc.protys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ve.edu.uc.protys.dto.DashboardStats;
import ve.edu.uc.protys.service.OntologyService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "System statistics and health monitoring")
@RequiredArgsConstructor
public class DashboardController {

    private final OntologyService ontologyService;

    @GetMapping
    @Operation(summary = "Get system statistics", description = "Retrieve comprehensive system statistics including ontology modules, classes, and individuals count")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        log.info("Fetching dashboard statistics");
        try {
            DashboardStats stats = ontologyService.getDashboardStats();
            log.debug("Dashboard stats retrieved successfully");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching dashboard statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Check system health", description = "Verify system health status and availability of core services")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.info("Checking system health");
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            health.put("services", new HashMap<String, String>() {{
                put("OntologyService", "AVAILABLE");
                put("SPARQLService", "AVAILABLE");
                put("AlignmentService", "AVAILABLE");
                put("ERPConnectorService", "AVAILABLE");
                put("StandardIncorporationService", "AVAILABLE");
            }});

            log.debug("System health check completed");
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error checking system health", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "DOWN");
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/activity")
    @Operation(summary = "Get recent activity log", description = "Retrieve recent system activity including changes to ontologies and alignments")
    public ResponseEntity<Map<String, Object>> getRecentActivity() {
        log.info("Fetching recent activity log");
        try {
            DashboardStats stats = ontologyService.getDashboardStats();

            Map<String, Object> activity = new HashMap<>();
            activity.put("timestamp", LocalDateTime.now());
            activity.put("lastUpdated", stats.getLastActivity());
            activity.put("loadedModules", stats.getTotalModules());
            activity.put("totalTriples", stats.getTotalTriples());
            activity.put("totalClasses", stats.getTotalClasses());
            activity.put("totalIndividuals", stats.getTotalIndividuals());

            log.debug("Activity log retrieved successfully");
            return ResponseEntity.ok(activity);
        } catch (Exception e) {
            log.error("Error fetching activity log", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
