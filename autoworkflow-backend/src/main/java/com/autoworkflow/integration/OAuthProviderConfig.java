package com.autoworkflow.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.oauth")
@Data
public class OAuthProviderConfig {
    private ProviderCreds github = new ProviderCreds();
    private ProviderCreds slack = new ProviderCreds();
    private ProviderCreds google = new ProviderCreds();
    private ProviderCreds notion = new ProviderCreds();
    private ProviderCreds discord = new ProviderCreds();

    @Data
    public static class ProviderCreds {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
    }
}
