package org.example.mobihubloadtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttLoadService {

    private final MqttPahoClientFactory mqttClientFactory;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topics}")
    private String defaultTopic;

    @Value("${mqtt.load-test.filePath}")
    private String loadTestFilePath;

    @Value("${mqtt.load-test.maxConcurrentDevices}")
    private int maxConcurrentDevices;

    @Value("${mqtt.load-test.messageIntervalMillis}")
    private long messageIntervalMillis;

    @Value("${mqtt.load-test.maxConnectionRetries}")
    private int maxConnectionRetries;

    @Value("${mqtt.load-test.connectionRetryDelay}")
    private long connectionRetryDelay;

    private static final String REFERENCE_TIME_STR= "2023-11-16T12:53:11Z";
    private static final DateTimeFormatter EROL_ISTANBUL_JSON_TIME_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    private static final List<String> DATA_KEYS = Arrays.asList(
            "tripIgnitionData", "harshTurnData", "harshAccelerationData", "harshDecelerationData",
            "tripGPSData"
    );

    private static final List<String> TIME_KEYS = List.of("time", "msgTime");
    private static final Map<Integer, Integer> TYPE_ID_TO_DRV_BX = Map.of(
            12, 1,
            13, 2,
            18, 3
    );
    public void sendLoadTestData(int numberOfDevices, int minusHours, int minConcurrentDevices) {
        log.info("Starting load test - Number of devices: {}, Hour offset: {}", numberOfDevices, minusHours);

        JsonNode erolIstanbulJsonArray = loadDummyJsonData();
        ZonedDateTime fileReferenceTime = ZonedDateTime.parse(REFERENCE_TIME_STR, EROL_ISTANBUL_JSON_TIME_FORMATTER);
        ZonedDateTime newTargetTime = ZonedDateTime.now(ZoneId.systemDefault()).minusHours(minusHours);
        Duration timeShiftDuration = Duration.between(fileReferenceTime, newTargetTime);
        ExecutorService executorService = createThreadPool(numberOfDevices);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = minConcurrentDevices; i <= numberOfDevices; i++) {
            final String deviceImei = "TEST_IMEI_" + i;
            log.info("Queueing message generation task for device {} (index: {})", deviceImei, i);
            futures.add(CompletableFuture.runAsync(
                    () -> processDeviceMessages(deviceImei, erolIstanbulJsonArray, timeShiftDuration),
                    executorService
            ));
        }
        awaitCompletion(futures, executorService);
        log.info("Load test completed for {} devices", numberOfDevices);
    }

    private JsonNode loadDummyJsonData() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(loadTestFilePath);
            if (inputStream != null) {
                log.info("Loading test data from classpath: {}", loadTestFilePath);
                return objectMapper.readTree(inputStream);
            }

            File file = new File(loadTestFilePath);
            if (file.exists()) {
                log.info("Loading test data from file system: {}", file.getAbsolutePath());
                return objectMapper.readTree(file);
            }

            log.error("Test data file not found: {}", loadTestFilePath);
            throw new IOException("Test data file not found: " + loadTestFilePath);
        } catch (IOException e) {
            log.error("Error loading test data file: {}", loadTestFilePath, e);
            throw new RuntimeException("Failed to load test data: " + e.getMessage(), e);
        }
    }

    private void processDeviceMessages(String deviceImei, JsonNode dummyJsonArray, Duration timeShiftDuration) {
        IMqttClient mqttClient = null;
        String mqttClientId = generateClientId(deviceImei);

        try {
            mqttClient = createAndConnectClient(mqttClientId);
            for (JsonNode originalEventRecord : dummyJsonArray) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Thread was interrupted");
                }
                if (originalEventRecord.hasNonNull("tripCanData") || originalEventRecord.hasNonNull("tripDTCData")) {
                    continue;
                }

                Optional<ObjectNode> transformedMessageOpt = buildMqttMessagePayload(originalEventRecord, deviceImei, timeShiftDuration);

                if (transformedMessageOpt.isPresent()) {
                    sendMqttMessage(mqttClient, transformedMessageOpt.get());
                }
                sleepIfNeeded();
            }
        } catch (Exception e) {
            log.error("Error sending message for device {}: {}", deviceImei, e.getMessage(), e);
        } finally {
                disconnectAndCloseClient(mqttClient, deviceImei, mqttClientId);
        }
    }

    private Optional<ObjectNode> buildMqttMessagePayload(JsonNode originalEventRecord, String deviceImei, Duration timeShiftDuration) {
        Optional<String> originalTimeStringOpt = findTimeStringInData(originalEventRecord);
        if (originalTimeStringOpt.isEmpty()) {
            return Optional.empty();
        }
        ZonedDateTime originalMsgTime = ZonedDateTime.parse(originalTimeStringOpt.get(), EROL_ISTANBUL_JSON_TIME_FORMATTER);
        ZonedDateTime newTimestamp = originalMsgTime.plus(timeShiftDuration);

        ObjectNode transformedMessage = objectMapper.createObjectNode();
        transformedMessage.put("imei", deviceImei);
        transformedMessage.put("ts", newTimestamp.toInstant().toEpochMilli());

        return findDataObject(originalEventRecord).map(dataObj -> {
            transformedMessage.set("values", createValuesNode(dataObj, originalEventRecord));
            return transformedMessage;
        });
    }

    private Optional<String> findTimeStringInData(JsonNode originalEventRecord) {
        return findDataObject(originalEventRecord)
                .flatMap(dataObj -> TIME_KEYS.stream()
                        .map(key -> dataObj.path(key).asText(null))
                        .filter(val -> val != null && !val.isEmpty())
                        .findFirst())
                .or(() -> Optional.ofNullable(originalEventRecord.path("time").asText(null)));
    }

    private ObjectNode createValuesNode(JsonNode dataObj, JsonNode originalEventRecord) {
        ObjectNode valuesNode = objectMapper.createObjectNode();

        valuesNode.put("lat", dataObj.path("latitude").asDouble(0.0));
        valuesNode.put("lng", dataObj.path("longitude").asDouble(0.0));
        valuesNode.put("vss", determineSpeed(originalEventRecord));
        valuesNode.put("vol", 0.0);
        valuesNode.put("odo", dataObj.path("totalOdometer").asInt(0));
        valuesNode.put("etc", 0);
        valuesNode.put("tp", 0);
        valuesNode.put("load_pct", 0);
        valuesNode.put("fc", 0);
        valuesNode.put("rpm", 0);

        int drvBx = determineDrvBxValue(originalEventRecord);
        if (drvBx > 0) {
            valuesNode.put("drv_bx", drvBx);
        }

        Integer ignValue = determineIgnValue(originalEventRecord);
        if (ignValue != null) {
            valuesNode.put("ign", ignValue);
        }
        int typeId = originalEventRecord.path("typeId").asInt();
        switch (typeId) {
            case 12:
                valuesNode.put("acc_value", dataObj.path("accValue").asDouble(0.0));
                break;
            case 13:
                valuesNode.put("dec_value", dataObj.path("decValue").asDouble(0.0));
                break;
            case 18:
                valuesNode.put("turn_value", dataObj.path("turnValue").asDouble(0.0));
                break;
            default:
                break;
        }
        log.info(valuesNode.toString());
        return valuesNode;
    }

    private Optional<JsonNode> findDataObject(JsonNode originalEventRecord) {
        for (String key : DATA_KEYS) {
            if (originalEventRecord.hasNonNull(key)) {
                return Optional.of(originalEventRecord.path(key));
            }
        }
        return Optional.empty();
    }

    private int determineSpeed(JsonNode originalEventRecord) {
        if (originalEventRecord.hasNonNull("tripGPSData")) {
            return originalEventRecord.path("tripGPSData").path("gpsSpeed").asInt(0);
        }
        if (originalEventRecord.hasNonNull("harshDecelerationData")) {
            return originalEventRecord.path("harshDecelerationData").path("speedBeforeDec").asInt(0);
        }
        if (originalEventRecord.hasNonNull("harshAccelerationData")) {
            return originalEventRecord.path("harshAccelerationData").path("speedAfterAcc").asInt(0);
        }
        return 0;
    }

    private Integer determineIgnValue(JsonNode originalEventRecord) {
        if (originalEventRecord.path("typeId").asInt() == 2) {
            int ignitionTypeId = originalEventRecord.path("tripIgnitionData").path("ignitionTypeId").asInt();
            if (ignitionTypeId == 4) return 1;
            if (ignitionTypeId == 5) return 0;
        }
        return null;
    }

    private int determineDrvBxValue(JsonNode originalEventRecord) {
        int typeId = originalEventRecord.path("typeId").asInt(-1);
        if (TYPE_ID_TO_DRV_BX.containsKey(typeId)) {
            return TYPE_ID_TO_DRV_BX.get(typeId);
        }

        Optional<String> dataKeyOpt = findDataKey(originalEventRecord);
        if (dataKeyOpt.isPresent()) {
            String dataKey = dataKeyOpt.get();
            return switch (dataKey) {
                case "harshAccelerationData" -> 1;
                case "harshDecelerationData" -> 2;
                case "harshTurnData" -> 3;
                default -> 0;
            };
        }
        return 0;
    }

    private Optional<String> findDataKey(JsonNode originalEventRecord) {
        for (String key : DATA_KEYS) {
            if (originalEventRecord.hasNonNull(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private void sleepIfNeeded() throws InterruptedException {
        if (messageIntervalMillis > 0) {
            Thread.sleep(messageIntervalMillis);
        }
    }


    private void shutdownExecutorService(ExecutorService executorService) {
        if (executorService == null || executorService.isShutdown()) {
            return;
        }
        log.info("Shutting down thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Thread pool did not shut down in 30 seconds, forcing shutdown now...");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("Thread pool could not be forcibly shut down.");
                }
            }
            log.info("Thread pool shut down successfully.");
        } catch (InterruptedException e) {
            log.error("Interruption occurred while shutting down thread pool.", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    private IMqttClient createAndConnectClient(String clientId) throws MqttException, InterruptedException {
        MqttConnectOptions options = mqttClientFactory.getConnectionOptions();
        String serverUri = options.getServerURIs()[0];

        IMqttClient client = new MqttClient(serverUri, clientId, new MemoryPersistence());

        int retryCount = 0;
        while (retryCount < maxConnectionRetries) {
            try {
                client.connect(options);
                log.debug("Connected to MQTT broker: {}", serverUri);
                return client;
            } catch (MqttException e) {
                retryCount++;
                if (retryCount >= maxConnectionRetries) {
                    throw e;
                }
                log.warn("Connection attempt {} failed for client {}. Retrying in {} ms...",
                        retryCount, clientId, connectionRetryDelay);
                Thread.sleep(connectionRetryDelay);
            }
        }
        throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
    }


    private void sendMqttMessage(IMqttClient client, ObjectNode message) throws MqttException {
        try {
            String messageString = objectMapper.writeValueAsString(message);
            MqttMessage mqttMessage = new MqttMessage(messageString.getBytes(StandardCharsets.UTF_8));

            client.publish(defaultTopic, mqttMessage);
            log.trace("Message sent to topic {}: {}", defaultTopic, messageString);
        } catch (IOException e) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION, e);
        }
    }
    private String generateClientId(String deviceImei) {
        return "loadtest-" + deviceImei + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void disconnectAndCloseClient(IMqttClient client, String deviceImei, String clientId) {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                log.debug("Disconnected client {} for device {}", clientId, deviceImei);
            }
        } catch (MqttException e) {
            log.warn("Error disconnecting client {}: {}", clientId, e.getMessage());
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (MqttException e) {
                log.warn("Error closing client {}: {}", clientId, e.getMessage());
            }
        }
    }
    private ExecutorService createThreadPool(int numberOfDevices) {
        int poolSize = Math.min(numberOfDevices, maxConcurrentDevices);
        log.info("Creating thread pool with size {}.", poolSize);
        return Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "mqtt-loadtest-worker-" + counter.incrementAndGet());
            }
        });
    }
    private void awaitCompletion(List<CompletableFuture<Void>> futures, ExecutorService executorService) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            log.error("Load test timed out after 5 minutes");
        } catch (Exception e) {
            log.error("Error during load test execution", e);
        } finally {
            shutdownExecutorService(executorService);
        }
    }

}
