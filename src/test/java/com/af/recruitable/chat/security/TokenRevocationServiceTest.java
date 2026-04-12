package com.af.recruitable.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests that the chat-service correctly rejects tokens that have been
 * blacklisted in Redis by api-backend's logout flow.
 *
 * <p>Verifies that {@link TokenRevocationService} reads the same Redis key
 * patterns that api-backend's {@code TokenBlacklistServiceImpl} writes.</p>
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-64-characters-long-for-hmac-sha512-compat!";
    private static final String HMAC_ALGORITHM = "HmacSHA512";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenRevocationService revocationService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        revocationService = new TokenRevocationService(redisTemplate);
        signingKey = new SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    private Claims buildClaims(String jti) {
        String token = Jwts.builder()
                .id(jti)
                .subject(UUID.randomUUID().toString())
                .issuer("recruitable-api")
                .audience().add("recruitable-client").and()
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(signingKey)
                .compact();

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Nested
    @DisplayName("Revocation detection")
    class RevocationDetection {

        @Test
        @DisplayName("Non-revoked token returns false")
        void nonRevokedToken() {
            String jti = UUID.randomUUID().toString();
            Claims claims = buildClaims(jti);

            when(redisTemplate.hasKey("token:blacklist:" + jti)).thenReturn(false);
            when(redisTemplate.hasKey("invalidated_token:" + jti)).thenReturn(false);

            assertFalse(revocationService.isTokenRevoked(claims));
        }

        @Test
        @DisplayName("Token blacklisted via plain-string key is detected")
        void plainStringKeyBlacklisted() {
            String jti = UUID.randomUUID().toString();
            Claims claims = buildClaims(jti);

            // api-backend writes: token:blacklist:<jti>
            when(redisTemplate.hasKey("token:blacklist:" + jti)).thenReturn(true);

            assertTrue(revocationService.isTokenRevoked(claims));
        }

        @Test
        @DisplayName("Token blacklisted via Spring Data Redis hash is detected")
        void redisHashBlacklisted() {
            String jti = UUID.randomUUID().toString();
            Claims claims = buildClaims(jti);

            when(redisTemplate.hasKey("token:blacklist:" + jti)).thenReturn(false);
            // api-backend writes: invalidated_token:<jti> (via @RedisHash)
            when(redisTemplate.hasKey("invalidated_token:" + jti)).thenReturn(true);

            assertTrue(revocationService.isTokenRevoked(claims));
        }

        @Test
        @DisplayName("Token without JTI is not revoked (cannot be individually tracked)")
        void noJtiNotRevoked() {
            // Build a token WITHOUT a JTI
            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer("recruitable-api")
                    .audience().add("recruitable-client").and()
                    .claim("type", "access")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .signWith(signingKey)
                    .compact();

            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            assertFalse(revocationService.isTokenRevoked(claims));
        }
    }

    @Nested
    @DisplayName("Fail-open on Redis unavailability")
    class FailOpen {

        @Test
        @DisplayName("Redis exception results in fail-open (token allowed)")
        void redisUnavailableFailsOpen() {
            String jti = UUID.randomUUID().toString();
            Claims claims = buildClaims(jti);

            when(redisTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            // Should NOT throw — fail-open policy
            assertFalse(revocationService.isTokenRevoked(claims));
        }
    }
}

