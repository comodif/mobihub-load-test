package org.example.mobihubloadtest.config.webClient;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeviceRestServiceWebClient implements BaseWebClient {
    @Value("${spring.security.admin.bearer}")
    private String token;
    @Value("${spring.device-rest-service.device-rest-service-url}")
    private String url;
    private WebClient webClient;

    @Override
    public WebClientType getType() {
        return WebClientType.DEVICE_REST_SERVICE;
    }

    @Override
    public WebClient get() {
        if(webClient == null) {
            webClient = WebClient.builder()
                    .defaultHeader("Authorization", String.format("Bearer %s", token))
                    .baseUrl(url)
                    .build();
        }
        return webClient;
    }
}
