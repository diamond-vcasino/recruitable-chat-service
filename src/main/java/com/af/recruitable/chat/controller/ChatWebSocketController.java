package com.af.recruitable.chat.controller;

import com.af.recruitable.chat.dto.*;
import com.af.recruitable.chat.security.JwtAuthenticationToken;
import com.af.recruitable.chat.service.ChatEventPublisher;
import com.af.recruitable.chat.service.ChatService;
import com.af.recruitable.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * WebSocket STOMP controller for real-time chat operations.
 * <p>
 * Clients send to /app/chat.send, /app/chat.typing, /app/chat.read.
 * Messages are broadcast to /topic/org.{orgId}.room.{roomId} via RabbitMQ.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final PresenceService presenceService;
    private final ChatEventPublisher eventPublisher;

    /**
     * Send a message via WebSocket.
     * Client sends to: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, SimpMessageHeaderAccessor headerAccessor) {
        JwtAuthenticationToken auth = getAuth(headerAccessor);
        UUID senderId = auth.getUserId();
        UUID orgId = auth.getOrganizationId();

        // Auto-resolve sender name from JWT if not provided
        if (request.getSenderName() == null || request.getSenderName().isBlank()) {
            request.setSenderName(auth.getEmail() != null ? auth.getEmail() : senderId.toString());
        }

        ChatMessageResponse response = chatService.sendMessage(request, senderId, orgId);

        // Broadcast to the room topic via RabbitMQ
        String destination = "/topic/org." + orgId + ".room." + request.getRoomId();
        eventPublisher.publish(destination, response);

        log.debug("WS message broadcast: room={}, sender={}", request.getRoomId(), senderId);
    }

    /**
     * Typing indicator. Lightweight — no DB persistence.
     * Client sends to: /app/chat.typing
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingEvent event, SimpMessageHeaderAccessor headerAccessor) {
        JwtAuthenticationToken auth = getAuth(headerAccessor);
        UUID userId = auth.getUserId();
        UUID orgId = auth.getOrganizationId();

        event.setUserId(userId);
        // Set userName so receivers can display "X is typing..."
        if (event.getUserName() == null || event.getUserName().isBlank()) {
            event.setUserName(auth.getEmail() != null ? auth.getEmail() : userId.toString());
        }
        presenceService.publishTyping(orgId, event.getRoomId(), userId, event.isTyping());

        // Broadcast typing event via RabbitMQ
        String destination = "/topic/org." + orgId + ".room." + event.getRoomId() + ".typing";
        eventPublisher.publish(destination, event);
    }

    /**
     * Read receipt via WebSocket.
     * Client sends to: /app/chat.read
     */
    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload ReadReceiptRequest request, SimpMessageHeaderAccessor headerAccessor) {
        JwtAuthenticationToken auth = getAuth(headerAccessor);
        UUID userId = auth.getUserId();
        UUID orgId = auth.getOrganizationId();

        chatService.markAsRead(request.getRoomId(), userId, orgId);

        // Broadcast read receipt via RabbitMQ
        String destination = "/topic/org." + orgId + ".room." + request.getRoomId() + ".read";
        eventPublisher.publish(destination,
                WebSocketEventDto.builder()
                        .event("READ_RECEIPT")
                        .userId(userId)
                        .timestamp(System.currentTimeMillis())
                        .build());
    }

    private JwtAuthenticationToken getAuth(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() instanceof JwtAuthenticationToken auth) {
            return auth;
        }
        throw new IllegalStateException("User is not authenticated");
    }
}
