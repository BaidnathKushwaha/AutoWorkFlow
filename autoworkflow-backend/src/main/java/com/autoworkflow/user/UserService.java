package com.autoworkflow.user;

import com.autoworkflow.common.exception.DuplicateResourceException;
import com.autoworkflow.common.exception.ResourceNotFoundException;
import com.autoworkflow.user.dto.ApiKeyResponse;
import com.autoworkflow.user.dto.UpdateProfileRequest;
import com.autoworkflow.user.dto.UserResponse;
import com.autoworkflow.util.ApiKeyGenerator;
import com.autoworkflow.util.EncryptionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EncryptionUtils encryptionUtils;

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    public UserResponse getCurrentUser(UUID userId) {
        return UserResponse.from(getById(userId));
    }

    @Transactional
    public UserResponse updateProfile(
            UUID userId,
            UpdateProfileRequest request
    ) {
        User user = getById(userId);

        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Email already in use: " + request.email()
            );
        }

        user.setName(request.name());
        user.setEmail(request.email());

        return UserResponse.from(
                userRepository.save(user)
        );
    }

    @Transactional
    public ApiKeyResponse generateApiKey(UUID userId) {
        User user = getById(userId);

        String rawKey = ApiKeyGenerator.generate();

        user.setApiKeyEncrypted(
                encryptionUtils.encrypt(rawKey)
        );

        user.setApiKeyLastFour(
                ApiKeyGenerator.lastFour(rawKey)
        );

        userRepository.save(user);

        return new ApiKeyResponse(
                rawKey,
                user.getApiKeyLastFour()
        );
    }

    public ApiKeyResponse revealApiKey(UUID userId) {
        User user = getById(userId);

        if (user.getApiKeyEncrypted() == null) {
            throw new ResourceNotFoundException(
                    "No API key has been generated yet"
            );
        }

        String rawKey =
                encryptionUtils.decrypt(
                        user.getApiKeyEncrypted()
                );

        return new ApiKeyResponse(
                rawKey,
                user.getApiKeyLastFour()
        );
    }

    public User findByDecryptedApiKey(
            String candidateRawKey
    ) {
        return userRepository.findAll()
                .stream()
                .filter(
                        u ->
                                u.getApiKeyEncrypted() != null
                )
                .filter(
                        u ->
                                encryptionUtils
                                        .decrypt(
                                                u.getApiKeyEncrypted()
                                        )
                                        .equals(candidateRawKey)
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Invalid API key"
                                )
                );
    }
}