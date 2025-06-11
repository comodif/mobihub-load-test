package org.example.mobihubloadtest.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mobihubloadtest.config.webClient.WebClientFactory;
import org.example.mobihubloadtest.config.webClient.WebClientType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;


@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRestClient {
    private final WebClientFactory webClientFactory;
    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = webClientFactory.get(WebClientType.DEVICE_REST_SERVICE).get();
    }

    public void invalidateAssetCache(Long assetId) {
        webClient.post().uri(uriBuilder -> uriBuilder
                .path("/api/device-cache/assets/{assetId}/invalidate")
                .build(assetId))
            .retrieve()
            .toBodilessEntity()
            .block();
    }

}
