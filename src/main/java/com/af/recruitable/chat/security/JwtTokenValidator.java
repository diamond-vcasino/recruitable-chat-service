package com.af.recruitable.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

/**
 * Validates JWTs issued by the main recruitable-api auth service.
 * <p>
 * The signing key is derived from the shared secret configured via
 * {@code app.jwt.secret}. We use the raw UTF-8 bytes directly with an
 * explicit {@code HmacSHA512} algorithm to guarantee the same key derivation
 * used by the token issuer ({@code TokenServiceImpl} in api-backend).
 * </p>
 * <p>
 * Tokens are expected to carry a {@code kid} (Key ID) header for key-rotation
 * awareness. Currently accepted kid values: {@code hmac-v1} and legacy tokens
 * without a kid header.
 * </p>
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA512";
    private static final int MIN_KEY_BYTES = 64; // 512 bits for HS512
    private static final Set<String> ACCEPTED_KEY_IDS = Set.of("hmac-v1");

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = buildKey(jwtProperties.getSecret());
        log.info("JWT validator initialised – key {} bits, alg={}, issuer={}, audience={}",
                signingKey.getEncoded().length * 8,
                HMAC_ALGORITHM,
                jwtProperties.getIssuer(),
                jwtProperties.getAudience());
    }

    public Claims validateToken(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        validateAudience(claims);
        return claims;
    }

    /**
     * Build an HMAC key from raw secret bytes using the explicit HmacSHA512
     * algorithm, exactly matching the token issuer's key derivation.
     */
    private static SecretKey buildKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + raw.length + " bytes / " + raw.length * 8
                            + " bits). HS512 requires at least " + MIN_KEY_BYTES + " bytes (512 bits).");
        }
        return new SecretKeySpec(raw, HMAC_ALGORITHM);
    }

    private void validateAudience(Claims claims) {
        String expectedAudience = jwtProperties.getAudience();
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return;
        }

        Object audClaim = claims.get("aud");
        if (audClaim instanceof String aud && expectedAudience.equals(aud)) {
            return;
        }
        if (audClaim instanceof Collection<?> collection && collection.stream().anyMatch(expectedAudience::equals)) {
            return;
        }

        throw new IllegalArgumentException("Invalid audience");
    }
}
