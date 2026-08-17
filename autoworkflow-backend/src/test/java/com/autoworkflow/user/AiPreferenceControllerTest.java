package com.autoworkflow.user;

import com.autoworkflow.common.exception.GlobalExceptionHandler;
import com.autoworkflow.common.exception.InvalidAiPreferenceException;
import com.autoworkflow.security.CurrentUserProvider;
import com.autoworkflow.user.dto.AiPreferenceResponse;
import com.autoworkflow.user.dto.AiPreferenceUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiPreferenceControllerTest {

    private MockMvc mockMvc;

    private AiPreferenceService aiPreferenceService;
    private UserService userService;
    private CurrentUserProvider currentUserProvider;

    private UUID userId;

    @BeforeEach
    void setUp() {
        aiPreferenceService = mock(AiPreferenceService.class);
        userService = mock(UserService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        userId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUserId())
                .thenReturn(userId);

        UserController controller =
                new UserController(
                        userService,
                        aiPreferenceService,
                        currentUserProvider
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();
    }

    @Test
    void specificWithoutProvider_returns400() throws Exception {
        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenThrow(
                new InvalidAiPreferenceException(
                        "AI provider is required for SPECIFIC mode"
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "mode": "SPECIFIC",
                              "provider": null,
                              "model": "google/gemini-2.5-flash"
                            }
                            """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void specificWithoutModel_returns400() throws Exception {
        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenThrow(
                new InvalidAiPreferenceException(
                        "AI model is required for SPECIFIC mode"
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "mode": "SPECIFIC",
                              "provider": "openrouter",
                              "model": null
                            }
                            """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProvider_returns400() throws Exception {
        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenThrow(
                new InvalidAiPreferenceException(
                        "Unsupported AI provider: unknown"
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "mode": "SPECIFIC",
                              "provider": "unknown",
                              "model": "unknown"
                            }
                            """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedModel_returns400() throws Exception {
        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenThrow(
                new InvalidAiPreferenceException(
                        "Unsupported AI model"
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "mode": "SPECIFIC",
                              "provider": "openrouter",
                              "model": "openrouter/free"
                            }
                            """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMode_returns400() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "provider": "openrouter",
                              "model": "google/gemini-2.5-flash"
                            }
                            """)
                )
                .andExpect(status().isBadRequest());
    }
    @Test
    void autoPayload_isAcceptedAndPassedToService()
            throws Exception {

        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenReturn(
                new AiPreferenceResponse(
                        AiMode.AUTO,
                        null,
                        null,
                        List.of()
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {
                          "mode": "AUTO",
                          "provider": null,
                          "model": null
                        }
                        """)
                )
                .andExpect(status().isOk());

        ArgumentCaptor<AiPreferenceUpdateRequest> captor =
                ArgumentCaptor.forClass(
                        AiPreferenceUpdateRequest.class
                );

        verify(aiPreferenceService)
                .update(
                        eq(userId),
                        captor.capture()
                );

        assertThat(captor.getValue().mode())
                .isEqualTo(AiMode.AUTO);

        assertThat(captor.getValue().provider())
                .isNull();

        assertThat(captor.getValue().model())
                .isNull();
    }
    @Test
    void specificPayload_isAcceptedAndPassedToService()
            throws Exception {

        when(aiPreferenceService.update(
                eq(userId),
                any(AiPreferenceUpdateRequest.class)
        )).thenReturn(
                new AiPreferenceResponse(
                        AiMode.SPECIFIC,
                        "openrouter",
                        "google/gemini-2.5-flash",
                        List.of()
                )
        );

        mockMvc.perform(
                        put("/api/users/me/ai-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {
                          "mode": "SPECIFIC",
                          "provider": "openrouter",
                          "model": "google/gemini-2.5-flash"
                        }
                        """)
                )
                .andExpect(status().isOk());

        ArgumentCaptor<AiPreferenceUpdateRequest> captor =
                ArgumentCaptor.forClass(
                        AiPreferenceUpdateRequest.class
                );

        verify(aiPreferenceService)
                .update(
                        eq(userId),
                        captor.capture()
                );

        assertThat(captor.getValue().mode())
                .isEqualTo(AiMode.SPECIFIC);

        assertThat(captor.getValue().provider())
                .isEqualTo("openrouter");

        assertThat(captor.getValue().model())
                .isEqualTo(
                        "google/gemini-2.5-flash"
                );
    }
}