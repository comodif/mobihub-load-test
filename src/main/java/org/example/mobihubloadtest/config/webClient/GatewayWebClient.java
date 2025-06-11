package org.example.mobihubloadtest.config.webClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GatewayWebClient implements BaseWebClient {
    @Value("${spring.security.admin.bearer}")
    private String token;
    @Value("${spring.security.admin.gateway-url}")
    private String url;
    private WebClient webClient;

    @Override
    public WebClientType getType() {
        return WebClientType.GATEWAY;
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
