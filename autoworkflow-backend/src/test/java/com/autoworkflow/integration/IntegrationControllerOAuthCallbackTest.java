package com.autoworkflow.integration;

import com.autoworkflow.common.enums.IntegrationStatus;
import com.autoworkflow.integration.oauth.OAuthStateContext;
import com.autoworkflow.integration.oauth.OAuthStateService;
import com.autoworkflow.integration.oauth.OAuthToken;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeClient;
import com.autoworkflow.integration.oauth.OAuthTokenExchangeRegistry;
import com.autoworkflow.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationControllerOAuthCallbackTest {
    private IntegrationService integrationService;
    private OAuthAuthorizationService authorizationService;
    private OAuthTokenExchangeRegistry registry;
    private OAuthStateService stateService;
    private CurrentUserProvider currentUserProvider;
    private IntegrationController controller;
    private OAuthTokenExchangeClient gmailClient;
    private OAuthTokenExchangeClient sheetsClient;

    @BeforeEach
    void setUp() {
        integrationService = mock(IntegrationService.class);
        authorizationService = mock(OAuthAuthorizationService.class);
        registry = mock(OAuthTokenExchangeRegistry.class);
        stateService = mock(OAuthStateService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new IntegrationController(integrationService, authorizationService, registry, stateService, currentUserProvider);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");

        gmailClient = mock(OAuthTokenExchangeClient.class);
        sheetsClient = mock(OAuthTokenExchangeClient.class);
    }

    @Test
    void googleCallbackRestoresGmailProviderAndSavesIntegrationAsGmail() throws Exception {
        UUID userId = UUID.randomUUID();
        OAuthToken token = new OAuthToken("access", "refresh", Instant.now().plusSeconds(3600),
                "Gmail Account", List.of("gmail.modify"));
        Integration saved = Integration.builder().userId(userId).provider("gmail")
                .accountLabel("Gmail Account").status(IntegrationStatus.HEALTHY).scopes(token.scopes())
                .lastCheckedAt(Instant.now()).build();

        when(stateService.consume("state")).thenReturn(new OAuthStateContext(userId, "gmail"));
        when(registry.resolveOrNull("gmail")).thenReturn(gmailClient);
        when(gmailClient.exchange("code")).thenReturn(token);
        when(integrationService.saveTokens(eq(userId), eq("gmail"), eq("access"), eq("refresh"),
                eq("Gmail Account"), eq(token.scopes()), eq(token.expiresAt()))).thenReturn(saved);

        HttpServletResponse response = mock(HttpServletResponse.class);
        controller.oauthCallbackGet("google", "code", "state", null, response);

        verify(integrationService).saveTokens(eq(userId), eq("gmail"), anyString(), anyString(),
                eq("Gmail Account"), eq(token.scopes()), eq(token.expiresAt()));
        verify(response).sendRedirect("http://localhost:5173/integrations?status=success&provider=gmail");
    }

    @Test
    void googleCallbackRestoresGoogleSheetsProviderAndSavesIntegrationAsGoogleSheets() throws Exception {
        UUID userId = UUID.randomUUID();
        OAuthToken token = new OAuthToken("access", "refresh", Instant.now().plusSeconds(3600),
                "Google Workspace Account", List.of("spreadsheets"));
        Integration saved = Integration.builder().userId(userId).provider("google_sheets")
                .accountLabel("Google Workspace Account").status(IntegrationStatus.HEALTHY).scopes(token.scopes())
                .lastCheckedAt(Instant.now()).build();

        when(stateService.consume("state")).thenReturn(new OAuthStateContext(userId, "google_sheets"));
        when(registry.resolveOrNull("google_sheets")).thenReturn(sheetsClient);
        when(sheetsClient.exchange("code")).thenReturn(token);
        when(integrationService.saveTokens(eq(userId), eq("google_sheets"), eq("access"), eq("refresh"),
                eq("Google Workspace Account"), eq(token.scopes()), eq(token.expiresAt()))).thenReturn(saved);

        HttpServletResponse response = mock(HttpServletResponse.class);
        controller.oauthCallbackGet("google", "code", "state", null, response);

        verify(integrationService).saveTokens(eq(userId), eq("google_sheets"), anyString(), anyString(),
                eq("Google Workspace Account"), eq(token.scopes()), eq(token.expiresAt()));
        verify(response).sendRedirect("http://localhost:5173/integrations?status=success&provider=google_sheets");
    }

    @Test
    void googleCallbackDoesNotTrustRouteProviderForIntegrationStorage() throws Exception {
        UUID userId = UUID.randomUUID();
        OAuthToken token = new OAuthToken("access", null, Instant.now().plusSeconds(3600),
                "Gmail Account", List.of("gmail.modify"));
        when(stateService.consume("state")).thenReturn(new OAuthStateContext(userId, "gmail"));
        when(registry.resolveOrNull("gmail")).thenReturn(gmailClient);
        when(gmailClient.exchange("code")).thenReturn(token);
        when(integrationService.saveTokens(eq(userId), eq("gmail"), anyString(), isNull(),
                eq("Gmail Account"), eq(token.scopes()), eq(token.expiresAt())))
                .thenReturn(Integration.builder().userId(userId).provider("gmail")
                        .accountLabel("Gmail Account").status(IntegrationStatus.HEALTHY).scopes(token.scopes()).build());

        HttpServletResponse response = mock(HttpServletResponse.class);
        controller.oauthCallbackGet("google", "code", "state", null, response);

        verify(integrationService, never()).saveTokens(eq(userId), eq("google"), any(), any(), any(), any(), any());
        verify(response).sendRedirect("http://localhost:5173/integrations?status=success&provider=gmail");
    }

    @Test
    void googleCallbackDoesNotSaveIntegrationWhenTokenExchangeFails() throws Exception {
        UUID userId = UUID.randomUUID();
        when(stateService.consume("state")).thenReturn(new OAuthStateContext(userId, "gmail"));
        when(registry.resolveOrNull("gmail")).thenReturn(gmailClient);
        when(gmailClient.exchange("code")).thenThrow(new RuntimeException("provider failure"));

        HttpServletResponse response = mock(HttpServletResponse.class);
        controller.oauthCallbackGet("google", "code", "state", null, response);

        verify(integrationService, never()).saveTokens(any(), anyString(), any(), any(), any(), any(), any());
        verify(response).sendRedirect("http://localhost:5173/integrations?status=error&message=Google+authorization+could+not+be+completed.+Please+reconnect+and+try+again.");
    }
}
