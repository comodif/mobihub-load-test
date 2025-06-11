package org.example.mobihubloadtest.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mobihubloadtest.config.webClient.WebClientFactory;
import org.example.mobihubloadtest.config.webClient.WebClientType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformClient {

    private final WebClientFactory webClientFactory;
    private WebClient webClient;

    @Value("${vehicle.url}")
    private String vehicleUrl;

    @Value("${device.url}")
    private String deviceUrl;

    @Value("${admin.token}")
    private String ADMIN_TOKEN;

    @Value("${tenant.id}")
    private String TENANT_ID;

    @Value("${inventory.url}")
    private String inventoryUrl;

    @PostConstruct
    void init() {
        webClient = webClientFactory.get(WebClientType.PLATFORM).get();
    }

    public void createInventory(Map<String, Object> request) {
        webClient.post()
                .uri(inventoryUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                .header("X-Tenant-ID", TENANT_ID)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String createVehicle(Map<String, Object> request, String token, String tenantId) {
        return webClient.post()
                .uri(vehicleUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-ID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void pairDevice(Map<String, Object> request, String token, String tenantId) {
        webClient.post()
                .uri(deviceUrl + "/pair/")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-ID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
