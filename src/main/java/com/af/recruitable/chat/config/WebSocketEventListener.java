package com.af.recruitable.chat.config;
import com.af.recruitable.chat.dto.WebSocketEventDto;
import com.af.recruitable.chat.security.JwtAuthenticationToken;
import com.af.recruitable.chat.service.ChatEventPublisher;
import com.af.recruitable.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.UUID;
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {
    private final PresenceService presenceService;
    private final ChatEventPublisher eventPublisher;
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        if (event.getUser() instanceof JwtAuthenticationToken auth) {
            UUID userId = auth.getUserId();
            UUID orgId = auth.getOrganizationId();
            if (orgId != null) {
                presenceService.setUserOnline(orgId, userId);
                broadcast(orgId, userId, "USER_ONLINE");
                log.info("WS connected: user={}, org={}", userId, orgId);
            }
        }
    }
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof JwtAuthenticationToken auth) {
            UUID userId = auth.getUserId();
            UUID orgId = auth.getOrganizationId();
            if (orgId != null) {
                presenceService.setUserOffline(orgId, userId);
                broadcast(orgId, userId, "USER_OFFLINE");
                log.info("WS disconnected: user={}, org={}", userId, orgId);
            }
        }
    }
    private void broadcast(UUID orgId, UUID userId, String eventType) {
        eventPublisher.publish("/topic/org." + orgId + ".presence",
                WebSocketEventDto.builder().event(eventType).userId(userId)
                        .timestamp(System.currentTimeMillis()).build());
    }
}
