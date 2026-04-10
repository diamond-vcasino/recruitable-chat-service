package com.af.recruitable.chat.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures JWT from HTTP handshake (Authorization header or cookies)
 * and stores it in WebSocket session attributes for STOMP CONNECT auth.
 */
@Component
@Slf4j
public class WebSocketHandshakeAuthInterceptor implements HandshakeInterceptor {

    public static final String SESSION_JWT_TOKEN_ATTR = "jwtToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();

            String token = extractBearer(httpReq.getHeader("Authorization"));
            if (token == null) {
                token = findCookieValue(httpReq.getCookies(), "rct_at");
            }
            if (token == null) {
                token = findCookieValue(httpReq.getCookies(), "access_token");
            }

            if (token != null && !token.isBlank()) {
                attributes.put(SESSION_JWT_TOKEN_ATTR, token.trim());
                log.debug("WS handshake token captured from HTTP request");
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String extractBearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private String findCookieValue(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

