package com.autoworkflow.assistant;

import com.autoworkflow.assistant.dto.*;
import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(
                assistantService.chat(
                        currentUserProvider.getCurrentUserId(),
                        request
                )
        );
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSummaryResponse>> conversations() {
        return ApiResponse.success(
                assistantService.listConversations(
                        currentUserProvider.getCurrentUserId()
                )
        );
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> history(
            @PathVariable UUID id
    ) {
        return ApiResponse.success(
                assistantService.getHistory(
                        currentUserProvider.getCurrentUserId(),
                        id
                )
        );
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable UUID id
    ) {
        assistantService.deleteConversation(
                currentUserProvider.getCurrentUserId(),
                id
        );
        return ApiResponse.success(null);
    }
}
