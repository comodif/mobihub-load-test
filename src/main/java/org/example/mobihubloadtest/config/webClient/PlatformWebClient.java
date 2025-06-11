package org.example.mobihubloadtest.config.webClient;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PlatformWebClient implements BaseWebClient {
    @Value("${spring.security.admin.bearer}")
    private String token;
    @Value("${spring.security.admin.platform-url}")
    private String url;
    private WebClient webClient;

    @Override
    public WebClientType getType() {
        return WebClientType.PLATFORM;
    }

    @Override
    public WebClient get() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .defaultHeader("Authorization", String.format("Bearer %s", token))
                    .baseUrl(url)
                    .exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 10))
                            .build())
                    .build();
        }
        return webClient;
    }
}
