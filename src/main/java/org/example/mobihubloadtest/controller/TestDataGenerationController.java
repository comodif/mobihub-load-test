package org.example.mobihubloadtest.controller;

import lombok.RequiredArgsConstructor;
import org.example.mobihubloadtest.service.TestDataGenerationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generate-test-data")
@RequiredArgsConstructor
public class TestDataGenerationController {
    private final TestDataGenerationService testDataGenerationService;

    @GetMapping()
    public void startGenerate() {
        testDataGenerationService.startGenerate();
    }
} 
