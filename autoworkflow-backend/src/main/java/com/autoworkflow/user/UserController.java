package com.autoworkflow.user;

import com.autoworkflow.common.response.ApiResponse;
import com.autoworkflow.security.CurrentUserProvider;
import com.autoworkflow.user.dto.AiPreferenceResponse;
import com.autoworkflow.user.dto.AiPreferenceUpdateRequest;
import com.autoworkflow.user.dto.ApiKeyResponse;
import com.autoworkflow.user.dto.UpdateProfileRequest;
import com.autoworkflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AiPreferenceService aiPreferenceService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(
                userService.getCurrentUser(
                        currentUserProvider.getCurrentUserId()
                )
        );
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.success(
                userService.updateProfile(
                        currentUserProvider.getCurrentUserId(),
                        request
                ),
                "Profile updated successfully"
        );
    }

    @PostMapping("/me/api-key")
    public ApiResponse<ApiKeyResponse> generateApiKey() {
        return ApiResponse.success(
                userService.generateApiKey(
                        currentUserProvider.getCurrentUserId()
                ),
                "New API key generated. Store it securely; it will not be shown again in full via list endpoints."
        );
    }

    @GetMapping("/me/api-key/reveal")
    public ApiResponse<ApiKeyResponse> revealApiKey() {
        return ApiResponse.success(
                userService.revealApiKey(
                        currentUserProvider.getCurrentUserId()
                )
        );
    }

    @GetMapping("/me/ai-preferences")
    public ApiResponse<AiPreferenceResponse> getAiPreferences() {
        return ApiResponse.success(
                aiPreferenceService.get(
                        currentUserProvider.getCurrentUserId()
                )
        );
    }

    @PutMapping("/me/ai-preferences")
    public ApiResponse<AiPreferenceResponse> updateAiPreferences(
            @Valid @RequestBody AiPreferenceUpdateRequest request
    ) {
        return ApiResponse.success(
                aiPreferenceService.update(
                        currentUserProvider.getCurrentUserId(),
                        request
                ),
                "AI preferences updated successfully"
        );
    }
}