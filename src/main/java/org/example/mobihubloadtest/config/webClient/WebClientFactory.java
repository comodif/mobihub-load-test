package org.example.mobihubloadtest.config.webClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WebClientFactory {
    private final Map<WebClientType, BaseWebClient> webClientMap;

    @Autowired
    private WebClientFactory(List<BaseWebClient> webClientList) {
        webClientMap = webClientList.stream().collect(Collectors.toUnmodifiableMap(BaseWebClient::getType, Function.identity()));
    }

    public BaseWebClient get(WebClientType viewerType) {
        return Optional.ofNullable(webClientMap.get(viewerType)).orElseThrow(IllegalArgumentException::new);
    }
}
