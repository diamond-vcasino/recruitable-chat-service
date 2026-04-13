package com.af.recruitable.chat.security;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
@UtilityClass
public class SecurityUtils {
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getUserId();
        }
        throw new SecurityException("User is not authenticated");
    }
    public UUID getCurrentOrganizationId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getOrganizationId();
        }
        throw new SecurityException("User is not authenticated");
    }

    public List<String> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getRoles();
        }
        throw new SecurityException("User is not authenticated");
    }

    public boolean hasCurrentRole(String role) {
        String expected = role.toUpperCase(Locale.ROOT);
        return getCurrentRoles().stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.toUpperCase(Locale.ROOT))
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .anyMatch(expected::equals);
    }

    public boolean isCurrentUserAdmin() {
        return hasCurrentRole("ADMIN");
    }

    public String getCurrentJwtToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new SecurityException("User is not authenticated");
    }

    public String getCurrentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getEmail();
        }
        throw new SecurityException("User is not authenticated");
    }
}
