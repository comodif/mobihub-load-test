package org.example.mobihubloadtest.controller;

import lombok.RequiredArgsConstructor;
import org.example.mobihubloadtest.service.MqttLoadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mqtt-data")
@RequiredArgsConstructor
public class MqttLoadController {

    private final MqttLoadService mqttLoadService;

    @PostMapping("/load-test")
    public ResponseEntity<?> loadTestData(
            @RequestParam int numberOfDevices,
            @RequestParam int minusHours,
            @RequestParam int minConcurrentDevices) {
        try {
            if (numberOfDevices <= 0) {
                return ResponseEntity.badRequest().body("numberOfDevices must be a positive value.");
            }

            mqttLoadService.sendLoadTestData(numberOfDevices, minusHours, minConcurrentDevices);
            return ResponseEntity.ok("MQTT load test data sending started for " + numberOfDevices + " devices.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred while starting the load test: " + e.getMessage());
        }
    }
} 
