package org.example.mobihubloadtest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mobihubloadtest.config.webClient.WebClientFactory;
import org.example.mobihubloadtest.config.webClient.WebClientType;
import org.example.mobihubloadtest.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
    public class GatewayClient {

    private final WebClientFactory webClientFactory;
    private WebClient webClient;

    @Value("${spring.user-service.auth.url}")
    private String authUrl;

    @Value("${spring.security.admin.bearer}")
    private String ADMIN_TOKEN;

    @PostConstruct
    void init() {
        webClient = webClientFactory.get(WebClientType.GATEWAY).get();
    }

    public void createUser(Map<String, Object> userRequest) {
        try {
            webClient.post()
                    .uri(authUrl + "/api/users/create")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequest)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .doOnNext(body -> log.info("📨 Response body: {}", body)))
                    .block();
        } catch (Exception e) {
            log.error("Kullanıcı oluşturulamadı ({}): {}", userRequest.get("login"), e.getMessage());
        }
    }

    public AuthResponse authenticate(String username, String password) {
        Map<String, String> request = Map.of("username", username, "password", password);
        return webClient.post()
                .uri(authUrl + "/api/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .toEntity(String.class)
                .map(response -> {
                    try {
                        JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
                        String token = jsonNode.get("id_token").asText().replace("Bearer ", "");
                        String tenantId = response.getHeaders().getFirst("X-Tenant-ID");
                        return new AuthResponse(token, tenantId);
                    } catch (Exception e) {
                        throw new RuntimeException("Token parse edilemedi: " + e.getMessage());
                    }
                })
                .block();
    }
}
