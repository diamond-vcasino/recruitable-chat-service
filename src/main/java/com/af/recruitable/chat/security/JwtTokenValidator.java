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

/**
 * Validates JWTs issued by the main recruitable-api auth service.
 * <p>
 * The signing key is derived from the shared secret configured via
 * {@code app.jwt.secret}. We use the raw UTF-8 bytes directly for
 * compatibility with the auth service signing behavior.
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey signingKey;
    private final JwtProperties jwtProperties;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = buildKey(jwtProperties.getSecret());
        log.info("JWT validator initialised – key {} bits, issuer={}, audience={}",
                signingKey.getEncoded().length * 8,
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
     * Build an HMAC key from raw secret bytes so verification behavior
     * matches the token issuer as closely as possible.
     */
    private static SecretKey buildKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + raw.length + " bytes / " + raw.length * 8
                            + " bits). Minimum 32 bytes (256 bits) required.");
        }
        if (raw.length < 64) {
            log.warn("JWT secret is {} bytes ({} bits). HS512 is expected to use 64+ bytes. "
                    + "Using raw key bytes for compatibility; align auth/chat secrets to 64+ bytes.",
                raw.length, raw.length * 8);
        }
        return new SecretKeySpec(raw, "HmacSHA512");
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
