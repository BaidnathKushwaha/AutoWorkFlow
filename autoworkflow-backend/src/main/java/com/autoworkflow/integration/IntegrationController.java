package com.autoworkflow.integration;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.integration.dto.IntegrationResponse;
import com.autoworkflow.integration.dto.OAuthCallbackRequest;
import com.autoworkflow.integration.oauth.OAuthStateService;
import com.autoworkflow.integration.oauth.OAuthToken;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeClient;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeRegistry;
import com.autoworkflow.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;
    private final OAuthAuthorizationService oAuthAuthorizationService;
    private final OAuthTokenExchangeRegistry oauthTokenExchangeRegistry;
    private final OAuthStateService oauthStateService;
    private final CurrentUserProvider currentUserProvider;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private String frontendUrl;

    @GetMapping
    public ApiResponse<List<IntegrationResponse>> list() {
        return ApiResponse.success(integrationService.listForUser(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/oauth/{provider}")
    public ApiResponse<Map<String, String>> initiateOAuth(@PathVariable String provider) {
        UUID userId = currentUserProvider.getCurrentUserId();
        String state = oauthStateService.create(userId, provider);
        String authUrl = oAuthAuthorizationService.buildAuthorizationUrl(provider, state);
        return ApiResponse.success(Map.of("authorizationUrl", authUrl));
    }

    @PostMapping("/oauth/{provider}/callback")
    public ApiResponse<IntegrationResponse> oauthCallback(@PathVariable String provider,
                                                           @Valid @RequestBody OAuthCallbackRequest request) {
        UUID userId = oauthStateService.consume(request.state(), provider);
        OAuthTokenExchangeClient client = resolveClient(provider);
        OAuthToken token = client.exchange(request.code());
        Integration saved = integrationService.saveTokens(
                userId, provider, token.accessToken(), token.refreshToken(),
                token.accountLabel(), token.scopes(), token.expiresAt());
        return ApiResponse.success(IntegrationResponse.from(saved), "Connected " + provider + " as " + token.accountLabel());
    }

    @GetMapping("/oauth/{provider}/callback")
    public void oauthCallbackGet(@PathVariable String provider,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (error != null || code == null || state == null) {
            redirectError(response, "Authorization was cancelled or rejected.");
            return;
        }
        try {
            UUID userId = oauthStateService.consume(state, provider);
            OAuthToken token = resolveClient(provider).exchange(code);
            integrationService.saveTokens(
                    userId, provider, token.accessToken(), token.refreshToken(),
                    token.accountLabel(), token.scopes(), token.expiresAt());
            response.sendRedirect(frontendUrl + "/integrations?status=success&provider=" +
                    URLEncoder.encode(provider, StandardCharsets.UTF_8));
        } catch (Exception e) {
            redirectError(response, "Google authorization could not be completed. Please reconnect and try again.");
        }
    }

    @PostMapping("/key/{provider}")
    public ApiResponse<IntegrationResponse> connectWithKey(@PathVariable String provider,
                                                           @RequestBody Map<String, String> body) {
        UUID userId = currentUserProvider.getCurrentUserId();
        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key is required");
        }
        Integration saved = integrationService.saveTokens(
                userId, provider, apiKey.trim(), null,
                "API Key", List.of("API Access"), null);
        return ApiResponse.success(IntegrationResponse.from(saved), "Connected " + provider + " with API Key");
    }

    @DeleteMapping("/{provider}")
    public ApiResponse<Void> disconnect(@PathVariable String provider) {
        integrationService.disconnect(currentUserProvider.getCurrentUserId(), provider);
        return ApiResponse.success(null, "Disconnected " + provider);
    }

    private OAuthTokenExchangeClient resolveClient(String provider) {
        OAuthTokenExchangeClient client = oauthTokenExchangeRegistry.resolveOrNull(provider);
        if (client == null && ("gmail".equals(provider) || "google_sheets".equals(provider))) {
            client = oauthTokenExchangeRegistry.resolveOrNull("google");
        }
        if (client == null) throw new IntegrationException("OAuth for '" + provider + "' is not configured.");
        return client;
    }

    private void redirectError(jakarta.servlet.http.HttpServletResponse response, String message) throws java.io.IOException {
        response.sendRedirect(frontendUrl + "/integrations?status=error&message=" +
                URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
