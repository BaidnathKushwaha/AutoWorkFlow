package com.autoworkflow.integration.oauth;

import com.autoworkflow.integration.OAuthProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GoogleTokenExchangeBase extends GoogleTokenExchange {
    public GoogleTokenExchangeBase(WebClient.Builder webClientBuilder, OAuthProviderConfig oAuthProviderConfig) {
        super(webClientBuilder, oAuthProviderConfig, "google");
    }
}
