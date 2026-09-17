package com.autoworkflow.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

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

    /**
     * Gmail is polled periodically, so a connection can sit idle between polls.
     * Keep a short, dedicated pool lifetime to avoid reusing a connection that
     * the remote endpoint has already closed, which otherwise appears as a
     * Reactor Netty "Connection reset" during the next poll.
     */
    @Bean
    @Qualifier("gmailWebClientBuilder")
    public WebClient.Builder gmailWebClientBuilder() {
        ConnectionProvider provider = ConnectionProvider.builder("gmail-api")
                .maxConnections(8)
                .maxIdleTime(Duration.ofSeconds(15))
                .maxLifeTime(Duration.ofMinutes(2))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .evictInBackground(Duration.ofSeconds(15))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
