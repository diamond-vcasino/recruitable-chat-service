package com.af.recruitable.chat.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenValidator jwtTokenValidator;
    private final TokenRevocationService tokenRevocationService;
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                Claims claims = jwtTokenValidator.validateToken(token);
                String tokenType = claims.get("type", String.class);
                if ("access".equals(tokenType)) {
                    // ── Revocation check (shared Redis blacklist with api-backend) ──
                    if (tokenRevocationService.isTokenRevoked(claims)) {
                        log.warn("Revoked access token used on chat REST: jti={}", claims.getId());
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UUID userId = UUID.fromString(claims.getSubject());
                    String email = claims.get("email", String.class);
                    String orgIdStr = claims.get("organizationId", String.class);
                    UUID organizationId = (orgIdStr != null && !orgIdStr.isBlank())
                            ? UUID.fromString(orgIdStr) : null;
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    @SuppressWarnings("unchecked")
                    List<String> permissions = claims.get("permissions", List.class);
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (roles != null) roles.forEach(r ->
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
                    if (permissions != null) permissions.forEach(p ->
                            authorities.add(new SimpleGrantedAuthority(p)));
                    JwtAuthenticationToken auth = new JwtAuthenticationToken(
                            userId, token, authorities, email,
                            roles != null ? roles : List.of(),
                            permissions != null ? permissions : List.of(),
                            organizationId);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception ex) {
            log.error("Error processing JWT", ex);
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
    /**
     * Extract JWT from (in priority order):
     * 1. Authorization: Bearer header
     * 2. rct_at cookie (primary frontend cookie)
     * 3. access_token cookie (fallback)
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization header
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }

        // 2. rct_at cookie (same name used by WebSocket handshake interceptor)
        String cookieToken = findCookieValue(request.getCookies(), "rct_at");
        if (cookieToken != null) {
            return cookieToken;
        }

        // 3. access_token cookie (fallback)
        return findCookieValue(request.getCookies(), "access_token");
    }

    private String findCookieValue(Cookie[] cookies, String name) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/ws")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }
}
