package com.autoworkflow.integration;

import com.autoworkflow.common.exception.IntegrationException;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.integration.dto.IntegrationResponse;
import com.autoworkflow.integration.dto.OAuthCallbackRequest;
import com.autoworkflow.integration.oauth.OAuthToken;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeClient;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeRegistry;
import com.autoworkflow.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    private final CurrentUserProvider currentUserProvider;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
    private String frontendUrl;

    @GetMapping
    public ApiResponse<List<IntegrationResponse>> list() {
        return ApiResponse.success(integrationService.listForUser(currentUserProvider.getCurrentUserId()));
    }

    /** Initiates OAuth handshake: frontend redirects the browser to the returned URL. */
    @GetMapping("/oauth/{provider}")
    public ApiResponse<Map<String, String>> initiateOAuth(@PathVariable String provider) {
        UUID userId = currentUserProvider.getCurrentUserId();
        String authUrl = oAuthAuthorizationService.buildAuthorizationUrl(provider, userId.toString());
        return ApiResponse.success(Map.of("authorizationUrl", authUrl));
    }

    /**
     * Receives the auth code, trades it for a real token, and stores it
     * encrypted. Zero provider-specific code here — the correct
     * OAuthTokenExchangeClient is picked up purely by provider name.
     * Adding a new provider never touches this method.
     */
    @PostMapping("/oauth/{provider}/callback")
    public ApiResponse<IntegrationResponse> oauthCallback(@PathVariable String provider,
                                                            @Valid @RequestBody OAuthCallbackRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();

        OAuthTokenExchangeClient client = oauthTokenExchangeRegistry.resolve(provider);
        OAuthToken token = client.exchange(request.code());

        Integration saved = integrationService.saveTokens(
                userId, provider, token.accessToken(), token.refreshToken(),
                token.accountLabel(), token.scopes(), token.expiresAt());

        return ApiResponse.success(IntegrationResponse.from(saved), "Connected " + provider + " as " + token.accountLabel());
    }

    /**
     * Handles incoming browser redirect from external OAuth providers.
     */
    @GetMapping("/oauth/{provider}/callback")
    public void oauthCallbackGet(@PathVariable String provider,
                                 @RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (error != null || code == null || state == null) {
            String errMsg = error != null ? error : "Authorization failed or cancelled";
            response.sendRedirect(frontendUrl + "/integrations?status=error&message=" + java.net.URLEncoder.encode(errMsg, java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        try {
            UUID userId = UUID.fromString(state);
            OAuthTokenExchangeClient client = oauthTokenExchangeRegistry.resolveOrNull(provider);
            if (client == null) {
                // Check if provider starts with google aliases
                if ("gmail".equals(provider) || "google_sheets".equals(provider)) {
                    client = oauthTokenExchangeRegistry.resolveOrNull("google");
                }
            }
            if (client == null) {
                throw new IntegrationException("OAuth for '" + provider + "' is not configured.");
            }

            OAuthToken token = client.exchange(code);
            integrationService.saveTokens(
                    userId, provider, token.accessToken(), token.refreshToken(),
                    token.accountLabel(), token.scopes(), token.expiresAt());

            response.sendRedirect(frontendUrl + "/integrations?status=success&provider=" + provider);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "/integrations?status=error&message=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Connects an integration via an API Key or developer token (e.g. OpenAI).
     */
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
}
