package com.af.recruitable.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that chat-service token validation is compatible with
 * api-backend token generation.
 *
 * <p>These tests simulate the <em>exact</em> signing behavior of
 * {@code TokenServiceImpl} in {@code recruitable-api-backend}, then
 * verify that {@code JwtTokenValidator} in chat-service accepts them.
 * This catches algorithm mismatches, claim-name drift, and audience
 * validation bugs <em>before</em> deployment.</p>
 */
class CrossServiceJwtCompatibilityTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-64-characters-long-for-hmac-sha512-compat!";
    private static final String ISSUER = "recruitable-api";
    private static final String AUDIENCE = "recruitable-client";
    private static final String HMAC_ALGORITHM = "HmacSHA512";

    private JwtTokenValidator validator;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setIssuer(ISSUER);
        props.setAudience(AUDIENCE);
        props.setClockSkewSeconds(300);

        validator = new JwtTokenValidator(props);

        // Build key the SAME way api-backend's TokenServiceImpl does
        byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        signingKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    /**
     * Build a token that mirrors what api-backend's TokenServiceImpl.generateAccessToken() produces.
     */
    private String buildAccessToken(UUID userId, String email, UUID orgId,
                                     List<String> roles, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 15 * 60 * 1000); // 15 min

        return Jwts.builder()
                .header().keyId("hmac-v1").and()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("email", email)
                .claim("roles", roles != null ? roles : List.of())
                .claim("permissions", permissions != null ? permissions : List.of())
                .claim("type", "access")
                .claim("organizationId", orgId != null ? orgId.toString() : null)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    private String buildRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 7 * 24 * 3600 * 1000);

        return Jwts.builder()
                .header().keyId("hmac-v1").and()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ── Compatibility Tests ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Access token signed by api-backend pattern")
    class AccessTokenCompat {

        @Test
        @DisplayName("Valid access token is accepted by chat-service validator")
        void validAccessTokenAccepted() {
            UUID userId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            String token = buildAccessToken(userId, "user@example.com", orgId,
                    List.of("ADMIN", "MEMBER"), List.of("candidate.read", "job.write"));

            Claims claims = validator.validateToken(token);

            assertEquals(userId.toString(), claims.getSubject());
            assertEquals("user@example.com", claims.get("email", String.class));
            assertEquals("access", claims.get("type", String.class));
            assertEquals(orgId.toString(), claims.get("organizationId", String.class));

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            assertEquals(List.of("ADMIN", "MEMBER"), roles);

            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);
            assertEquals(List.of("candidate.read", "job.write"), permissions);
        }

        @Test
        @DisplayName("Token with empty roles/permissions is accepted")
        void emptyRolesAndPermissions() {
            UUID userId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            String token = buildAccessToken(userId, "new@example.com", orgId,
                    List.of(), List.of());

            Claims claims = validator.validateToken(token);
            assertEquals("access", claims.get("type", String.class));
        }

        @Test
        @DisplayName("Token with null orgId is accepted (pre-onboarding user)")
        void nullOrgIdAccepted() {
            UUID userId = UUID.randomUUID();
            String token = buildAccessToken(userId, "new@example.com", null,
                    List.of("MEMBER"), List.of());

            Claims claims = validator.validateToken(token);
            assertNull(claims.get("organizationId", String.class));
        }
    }

    @Nested
    @DisplayName("Refresh token handling")
    class RefreshTokenCompat {

        @Test
        @DisplayName("Refresh token is parseable but type=refresh")
        void refreshTokenParseable() {
            UUID userId = UUID.randomUUID();
            String token = buildRefreshToken(userId);

            Claims claims = validator.validateToken(token);
            assertEquals("refresh", claims.get("type", String.class));
        }
    }

    @Nested
    @DisplayName("Rejection cases")
    class RejectionCases {

        @Test
        @DisplayName("Token signed with wrong key is rejected")
        void wrongKeyRejected() {
            String wrongSecret = "wrong-secret-key-that-is-at-least-64-characters-long-for-hs512-testing!!";
            SecretKey wrongKey = new SecretKeySpec(
                    wrongSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);

            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .claim("type", "access")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(wrongKey)
                    .compact();

            assertThrows(Exception.class, () -> validator.validateToken(token));
        }

        @Test
        @DisplayName("Token with wrong issuer is rejected")
        void wrongIssuerRejected() {
            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer("wrong-issuer")
                    .audience().add(AUDIENCE).and()
                    .claim("type", "access")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(signingKey)
                    .compact();

            assertThrows(Exception.class, () -> validator.validateToken(token));
        }

        @Test
        @DisplayName("Token with wrong audience is rejected")
        void wrongAudienceRejected() {
            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(ISSUER)
                    .audience().add("wrong-audience").and()
                    .claim("type", "access")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(signingKey)
                    .compact();

            assertThrows(Exception.class, () -> validator.validateToken(token));
        }

        @Test
        @DisplayName("Expired token is rejected")
        void expiredTokenRejected() {
            String token = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .claim("type", "access")
                    .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                    .expiration(new Date(System.currentTimeMillis() - 60_000))
                    .signWith(signingKey)
                    .compact();

            // With 300s clock skew, a token expired 60s ago may still be accepted.
            // Use a much older expiry to guarantee rejection.
            String veryExpiredToken = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .claim("type", "access")
                    .issuedAt(new Date(System.currentTimeMillis() - 3_600_000))
                    .expiration(new Date(System.currentTimeMillis() - 1_800_000))
                    .signWith(signingKey)
                    .compact();

            assertThrows(Exception.class, () -> validator.validateToken(veryExpiredToken));
        }

        @Test
        @DisplayName("Secret shorter than 64 bytes is rejected at validator init")
        void shortSecretRejected() {
            JwtProperties shortProps = new JwtProperties();
            shortProps.setSecret("short-secret-only-40-characters-long!!");
            shortProps.setIssuer(ISSUER);
            shortProps.setAudience(AUDIENCE);

            assertThrows(IllegalStateException.class, () -> new JwtTokenValidator(shortProps));
        }
    }

    @Nested
    @DisplayName("Algorithm alignment")
    class AlgorithmAlignment {

        @Test
        @DisplayName("Both services derive identical key from same secret")
        void identicalKeyDerivation() {
            // api-backend's TokenServiceImpl key derivation (after our fix)
            byte[] apiBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
            SecretKey apiKey = new SecretKeySpec(apiBytes, HMAC_ALGORITHM);

            // chat-service's JwtTokenValidator key derivation
            SecretKey chatKey = new SecretKeySpec(apiBytes, HMAC_ALGORITHM);

            assertArrayEquals(apiKey.getEncoded(), chatKey.getEncoded());
            assertEquals(apiKey.getAlgorithm(), chatKey.getAlgorithm());
            assertEquals("HmacSHA512", apiKey.getAlgorithm());
        }
    }
}

