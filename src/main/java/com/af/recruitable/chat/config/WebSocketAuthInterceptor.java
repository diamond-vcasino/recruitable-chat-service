package com.af.recruitable.chat.config;
import com.af.recruitable.chat.constant.RoomType;
import com.af.recruitable.chat.entity.ChatRoom;
import com.af.recruitable.chat.repository.ChatRoomRepository;
import com.af.recruitable.chat.security.JwtAuthenticationToken;
import com.af.recruitable.chat.security.JwtTokenValidator;
import com.af.recruitable.chat.security.TokenRevocationService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String TOPIC_ORG_PREFIX = "/topic/org.";
    private static final String ROOM_MARKER = ".room.";

    private final JwtTokenValidator jwtTokenValidator;
    private final TokenRevocationService tokenRevocationService;
    private final ChatRoomRepository roomRepository;

    @Value("${app.websocket.auth-required:true}")
    private boolean authRequired;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if ((token == null || token.isBlank()) && !authRequired) {
            authenticateFromHeaders(accessor);
            return;
        }
        if (token == null || token.isBlank()) {
            log.warn("WS CONNECT rejected: missing or empty Authorization token");
            throw new IllegalArgumentException("Missing or empty Authorization token");
        }
        if (token.chars().filter(c -> c == '.').count() != 2) {
            log.warn("WS CONNECT rejected: token is not a valid JWT format (length={})", token.length());
            throw new IllegalArgumentException("Invalid token format");
        }

        try {
            Claims claims = jwtTokenValidator.validateToken(token);
            if (!"access".equals(claims.get("type", String.class))) {
                throw new IllegalArgumentException("Only access tokens allowed");
            }
            // ── Revocation check (shared Redis blacklist with api-backend) ──
            if (tokenRevocationService.isTokenRevoked(claims)) {
                throw new IllegalArgumentException("Token has been revoked");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String orgIdStr = claims.get("organizationId", String.class);
            if (orgIdStr == null || orgIdStr.isBlank()) {
                throw new IllegalArgumentException("organizationId required");
            }
            UUID orgId = UUID.fromString(orgIdStr);
            @SuppressWarnings("unchecked") List<String> roles = claims.get("roles", List.class);
            @SuppressWarnings("unchecked") List<String> permissions = claims.get("permissions", List.class);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
            }
            if (permissions != null) {
                permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            }
            accessor.setUser(new JwtAuthenticationToken(userId, token, authorities, email,
                    roles != null ? roles : List.of(), permissions != null ? permissions : List.of(), orgId));
            log.debug("WS CONNECT authenticated: user={}, org={}", userId, orgId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WS CONNECT rejected: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid token: " + e.getMessage());
        }
    }

    private void authenticateFromHeaders(StompHeaderAccessor accessor) {
        UUID userId = parseUuidHeader(accessor, "x-user-id", "user-id", "userId");
        UUID orgId = parseUuidHeader(accessor, "x-org-id", "x-organization-id", "organization-id", "organizationId");
        if (userId == null || orgId == null) {
            throw new IllegalArgumentException("Missing required x-user-id/x-org-id headers");
        }
        accessor.setUser(new JwtAuthenticationToken(
                userId,
                "anonymous",
                List.of(),
                "anonymous@chat.local",
                List.of("MEMBER"),
                List.of(),
                orgId));
        log.info("WS CONNECT authenticated in header mode: user={}, org={}", userId, orgId);
    }

    private UUID parseUuidHeader(StompHeaderAccessor accessor, String... headerNames) {
        for (String headerName : headerNames) {
            String value = accessor.getFirstNativeHeader(headerName);
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value.trim());
                } catch (IllegalArgumentException ignored) {
                    // try next candidate header name
                }
            }
        }
        return null;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(TOPIC_ORG_PREFIX)) {
            return;
        }

        JwtAuthenticationToken auth = requireAuth(accessor);
        validateDestinationOrg(destination, auth);

        UUID roomId = extractRoomId(destination);
        if (roomId != null) {
            validateRoomAccess(roomId, auth);
        }
    }

    private JwtAuthenticationToken requireAuth(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof JwtAuthenticationToken auth) {
            return auth;
        }
        throw new IllegalArgumentException("Not authenticated");
    }

    private void validateDestinationOrg(String destination, JwtAuthenticationToken auth) {
        String destOrgId = destination.substring(TOPIC_ORG_PREFIX.length());
        int dot = destOrgId.indexOf('.');
        if (dot > 0) {
            destOrgId = destOrgId.substring(0, dot);
        }
        if (!auth.getOrganizationId().toString().equals(destOrgId)) {
            throw new IllegalArgumentException("Cannot subscribe to another org's channel");
        }
    }

    private UUID extractRoomId(String destination) {
        int markerIndex = destination.indexOf(ROOM_MARKER);
        if (markerIndex < 0) {
            return null;
        }

        String remaining = destination.substring(markerIndex + ROOM_MARKER.length());
        int suffixIndex = remaining.indexOf('.');
        String roomId = suffixIndex >= 0 ? remaining.substring(0, suffixIndex) : remaining;
        try {
            return UUID.fromString(roomId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void validateRoomAccess(UUID roomId, JwtAuthenticationToken auth) {
        ChatRoom room = roomRepository.findById(roomId.toString())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (!room.getOrganizationId().equals(auth.getOrganizationId().toString())) {
            throw new IllegalArgumentException("Cannot subscribe to another org's room");
        }
        if (room.getType() != RoomType.PUBLIC) {
            boolean isMember = room.getMembers().stream()
                    .anyMatch(m -> auth.getUserId().toString().equals(m.getUserId()));
            if (!isMember) {
                throw new IllegalArgumentException("Cannot subscribe to a room you cannot access");
            }
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String h = accessor.getFirstNativeHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        String t = accessor.getFirstNativeHeader("token");
        if (t != null && !t.isBlank()) {
            return t.trim();
        }
        String at = accessor.getFirstNativeHeader("rct_at");
        if (at != null && !at.isBlank()) {
            return at.trim();
        }
        Object fromSession = accessor.getSessionAttributes() != null
                ? accessor.getSessionAttributes().get(WebSocketHandshakeAuthInterceptor.SESSION_JWT_TOKEN_ATTR)
                : null;
        return fromSession instanceof String sessionToken && !sessionToken.isBlank()
                ? sessionToken.trim()
                : null;
    }
}
