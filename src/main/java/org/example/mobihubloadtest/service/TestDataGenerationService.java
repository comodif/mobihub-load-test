package org.example.mobihubloadtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mobihubloadtest.client.GatewayClient;
import org.example.mobihubloadtest.client.PlatformClient;
import org.example.mobihubloadtest.dto.AuthResponse;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataGenerationService {

    private final GatewayClient gatewayClient;
    private final PlatformClient platformClient;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public void startLoadTest() {
        int threadCount = 10;
        int numberOfUsersToProcess = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean stopSignal = new AtomicBoolean(false);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= numberOfUsersToProcess; i++) {
            if (stopSignal.get()) {
                log.warn("Not submitting new user task (processing order {}) due to a critical error. Load test is stopping.", i);
                break;
            }
            final int userIdSuffix = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processUser(userIdSuffix);
            }, executor).exceptionally(ex -> {
                stopSignal.set(true);
                if (!executor.isShutdown()) {
                    log.warn("Shutting down ExecutorService immediately (shutdownNow) due to an error. Active tasks may be interrupted.");
                    executor.shutdownNow();
                }
                return null;
            });
            futures.add(future);
        }

        if (!stopSignal.get() && !futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CancellationException | CompletionException e) {
                log.warn("Error occurred while waiting for tasks to complete (likely due to early shutdown): {}", e.getMessage());
            }
        }

        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        try {
            log.info("Shutting down ExecutorService, waiting for existing tasks to complete...");
            if (!executor.awaitTermination(stopSignal.get() ? 5 : 60, TimeUnit.SECONDS)) {
                log.warn("ExecutorService did not terminate in time, forcing shutdown again (shutdownNow).");
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("ExecutorService could not be forcibly shut down.");
                }
            }
        } catch (InterruptedException e) {
            log.error("Interruption occurred while shutting down ExecutorService: {}", e.getMessage(), e);
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }

        if (stopSignal.get()) {
            log.error("GENERATION STOPPED PREMATURELY DUE TO AN ERROR!");
        }
    }

    private void processUser(int i) {
        String login = String.format("+905%09d", i);
        try {
            String password = "Password123!";
            List<Map<String, Object>> rolesList = new ArrayList<>();
            Map<String, Object> roleMap = new HashMap<>();
            roleMap.put("id", 1);
            rolesList.add(roleMap);

            Map<String, Object> userRequest = new HashMap<>();
            userRequest.put("login", login);
            userRequest.put("password", password);
            userRequest.put("email", i + "@example.com");
            userRequest.put("firstName", "Test");
            userRequest.put("lastName", "User" + i);
            userRequest.put("langKey", "tr");
            userRequest.put("activated", true);
            userRequest.put("roles", rolesList);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            userRequest.put("createdDate", now.toString());
            userRequest.put("lastModifiedDate", now.toString());

            log.debug("Creation request for user {}: {}", login, userRequest);

            gatewayClient.createUser(userRequest);
            log.info("Creation request sent for user {}.", login);

            AuthResponse auth = gatewayClient.authenticate(login, password);
            String token = auth.getToken();
            String tenantId = auth.getTenantId();
            log.info("Authentication successful for user {}. Tenant ID: {}", login, tenantId);

            Map<String, Object> inventoryRequest = new HashMap<>();
            inventoryRequest.put("createdDate", now.toString());
            inventoryRequest.put("modifiedDate", now.toString());
            inventoryRequest.put("imei", "TEST_IMEI_" + i);
            inventoryRequest.put("deviceType", "B2C_DONGLE");
            inventoryRequest.put("msisdn", "+905000000" + String.format("%03d", i));
            inventoryRequest.put("iccid", "TEST_ICCID_" + i);
            inventoryRequest.put("imsi", "TEST_IMSI_" + i);
            inventoryRequest.put("sold", true);
            inventoryRequest.put("installed", false);
            inventoryRequest.put("sellingDate", OffsetDateTime.now(ZoneOffset.UTC).toString());

            platformClient.createInventory(inventoryRequest);
            log.info("Inventory successfully created for user {}: IMEI={}", login, inventoryRequest.get("imei"));

            Map<String, Object> vehicleRequest = new HashMap<>();
            vehicleRequest.put("plate", "34TEST" + String.format("%03d", i));
            vehicleRequest.put("brand", "Test Brand");
            vehicleRequest.put("startStop", true);
            vehicleRequest.put("modelId", 1);
            vehicleRequest.put("year", 2024);
            vehicleRequest.put("vin", "TEST_VIN_" + i);
            vehicleRequest.put("createdDate", now.toString());
            vehicleRequest.put("modifiedDate", now.toString());

            String vehicleResponseString = platformClient.createVehicle(vehicleRequest, token, tenantId);
            JsonNode vehicleNode = objectMapper.readTree(vehicleResponseString);
            Long vehicleId = vehicleNode.get("id").asLong();
            log.info("Vehicle successfully created for user {}: ID={}, Plate={}", login, vehicleId, vehicleRequest.get("plate"));

            Map<String, Object> pairRequest = new HashMap<>();
            pairRequest.put("imei", inventoryRequest.get("imei"));
            pairRequest.put("assetId", vehicleId);
            platformClient.pairDevice(pairRequest, token, tenantId);
            log.info("Device pairing successful for user {}.", login);

            log.info("All operations successfully completed for user {}.", login);

        } catch (Exception e) {
            log.error("An error occurred while processing user {}: {}", login, e.getMessage(), e);
            throw new RuntimeException("Error processing user " + login + ": " + e.getMessage(), e);
        }
    }
}
