package com.autoworkflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Generic WebClient used by integration node strategies (GitHub, Slack,
 * Notion, Google Sheets, Discord, generic HTTP Request node, etc).
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
