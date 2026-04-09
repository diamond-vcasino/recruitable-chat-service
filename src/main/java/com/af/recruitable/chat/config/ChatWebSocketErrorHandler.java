 package com.af.recruitable.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * Custom STOMP error handler that enriches ERROR frames with a machine-readable
 * {@code X-Error-Code} header so that the frontend can distinguish between:
 * <ul>
 *   <li>{@code TOKEN_EXPIRED}   – JWT has expired → client should refresh and reconnect</li>
 *   <li>{@code AUTH_FAILED}     – Token is missing / invalid / wrong type</li>
 *   <li>{@code INTERNAL_ERROR}  – Unexpected server error</li>
 * </ul>
 *
 * Registered via {@link WebSocketErrorHandlerConfigurer}.
 */
@Component
@Slf4j
public class ChatWebSocketErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = unwrap(ex);
        String causeMsg = cause.getMessage() != null ? cause.getMessage() : "";

        String errorCode;
        String userMessage;

        if (isTokenExpired(causeMsg)) {
            errorCode = "TOKEN_EXPIRED";
            userMessage = "Your session has expired. Please refresh your token and reconnect.";
            log.warn("STOMP ERROR → TOKEN_EXPIRED: {}", causeMsg);
        } else if (isAuthFailed(causeMsg)) {
            errorCode = "AUTH_FAILED";
            userMessage = "Authentication failed: " + causeMsg;
            log.warn("STOMP ERROR → AUTH_FAILED: {}", causeMsg);
        } else {
            errorCode = "INTERNAL_ERROR";
            userMessage = "An unexpected error occurred. Please try again.";
            log.error("STOMP ERROR → INTERNAL_ERROR: {}", causeMsg, cause);
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(userMessage);
        accessor.addNativeHeader("X-Error-Code", errorCode);
        accessor.addNativeHeader("X-Error-Message", causeMsg);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                userMessage.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Throwable unwrap(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isTokenExpired(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("expired") || lower.contains("jwt expired")
                || lower.contains("token expired");
    }

    private static boolean isAuthFailed(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("missing") || lower.contains("invalid token")
                || lower.contains("not authenticated") || lower.contains("only access tokens")
                || lower.contains("organizationid required");
    }
}

