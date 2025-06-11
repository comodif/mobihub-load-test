package org.example.mobihubloadtest.config.webClient;

import org.springframework.web.reactive.function.client.WebClient;

public interface BaseWebClient {
    WebClientType getType();
    WebClient get();
}
