package com.autoworkflow.integration.slack;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.integration.IntegrationService;
import com.autoworkflow.security.CurrentUserProvider;
import com.autoworkflow.integration.slack.PostSlackMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/slack")
@RequiredArgsConstructor
public class SlackController {

    private final SlackMessageClient slackMessageClient;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SlackMessageResponse> postMessage(@Valid @RequestBody PostSlackMessageRequest request) {
        String accessToken = integrationService.getDecryptedAccessToken(
                currentUserProvider.getCurrentUserId(), "slack");

        SlackMessageResponse message = slackMessageClient.postMessage(accessToken, request.channel(), request.text());

        return ApiResponse.success(message, "Message posted to " + request.channel());
    }
}
