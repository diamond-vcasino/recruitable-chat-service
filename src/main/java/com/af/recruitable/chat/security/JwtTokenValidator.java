package com.af.recruitable.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Validates JWTs issued by the main recruitable-api auth service.
 * <p>
 * The signing key is derived from the shared secret configured via
 * {@code app.jwt.secret}. The key bytes are automatically padded to
 * 64 bytes (512 bits) so that JJWT's {@code Keys.hmacShaKeyFor()}
 * always selects HS512 without a key-size mismatch error.
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
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Ensures the raw secret bytes are at least 64 bytes (512 bits) so
     * {@link Keys#hmacShaKeyFor(byte[])} selects HS512 without error.
     * If the configured secret is shorter, we zero-pad it (safe for
     * HMAC – the padding is deterministic and reproducible).
     */
    private static SecretKey buildKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "JWT secret is too short (" + raw.length + " bytes / " + raw.length * 8
                            + " bits). Minimum 32 bytes (256 bits) required.");
        }
        if (raw.length < 64) {
            log.warn("JWT secret is {} bytes ({} bits) – padding to 64 bytes (512 bits) for HS512 compatibility. "
                            + "Consider using a secret that is at least 64 characters long.",
                    raw.length, raw.length * 8);
            raw = Arrays.copyOf(raw, 64);          // zero-pads to 64 bytes
        }
        return Keys.hmacShaKeyFor(raw);
    }
}
