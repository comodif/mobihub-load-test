package org.example.mobihubloadtest.controller;

import lombok.RequiredArgsConstructor;
import org.example.mobihubloadtest.service.DatabaseCleanupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cleanup")
@RequiredArgsConstructor
public class DatabaseCleanupController {

    private final DatabaseCleanupService cleanupService;

    @PostMapping("/test-data")
    public ResponseEntity<String> triggerCleanup() {
        cleanupService.cleanupAllTestData();
        return ResponseEntity.ok("PostgreSQL test data cleanup process initiated successfully.");
    }
}
