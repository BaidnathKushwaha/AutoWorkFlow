package com.autoworkflow.security;

import com.autoworkflow.security.user.CustomUserDetails;
import com.autoworkflow.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Convenience accessor for the authenticated user's id inside controllers/services,
 * so every module (workflow, execution, integration, assistant...) can scope
 * queries to the current user without re-deriving this each time.
 */
@Component
public class CurrentUserProvider {

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new UnauthorizedException("No authenticated user in context");
        }
        return details.getId();
    }

    public CustomUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new UnauthorizedException("No authenticated user in context");
        }
        return details;
    }
}
