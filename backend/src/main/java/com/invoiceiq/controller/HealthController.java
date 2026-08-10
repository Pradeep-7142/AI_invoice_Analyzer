package com.invoiceiq.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight public health probe for the frontend/dev workflow, separate
 * from the ops-facing /actuator/health used by orchestrators.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "invoiceiq-backend",
            "timestamp", Instant.now().toString()
        );
    }
}
