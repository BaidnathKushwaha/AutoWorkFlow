package com.autoworkflow.integration.oauth;

import com.autoworkflow.integration.OAuthProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GmailTokenExchange extends GoogleTokenExchange {
    public GmailTokenExchange(WebClient.Builder webClientBuilder, OAuthProviderConfig oAuthProviderConfig) {
        super(webClientBuilder, oAuthProviderConfig, "gmail");
    }
}
