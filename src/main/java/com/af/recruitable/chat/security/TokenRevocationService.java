package com.af.recruitable.chat.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Checks the Redis-backed token blacklist maintained by {@code recruitable-api-backend}.
 *
 * <p>When a user logs out, the API backend writes the access token's JTI
 * to Redis under two keys (legacy plain-string and Spring Data Redis hash).
 * This service checks both so that logged-out tokens are rejected immediately
 * in the chat service as well.</p>
 *
 * <h3>Key format contract (from api-backend's {@code TokenBlacklistServiceImpl}):</h3>
 * <ul>
 *   <li>Plain-string key: {@code token:blacklist:<jti>} with 15-minute TTL</li>
 *   <li>Spring Data Redis hash: {@code invalidated_token:<jti>} with {@code @TimeToLive}</li>
 * </ul>
 *
 * <p><strong>Fail-open policy:</strong> if Redis is unavailable, the token is
 * allowed through (it is still validated by JWT signature). This mirrors the
 * api-backend's behavior.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final RedisTemplate<String, String> redisTemplate;

    /** Must match the prefix in api-backend's TokenBlacklistServiceImpl. */
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /** Must match the @RedisHash value in api-backend's InvalidatedToken entity. */
    private static final String REDIS_HASH_PREFIX = "invalidated_token:";

    /**
     * Check whether a JWT has been revoked (blacklisted) in Redis.
     *
     * @param claims the parsed JWT claims (must contain a JTI)
     * @return {@code true} if the token is revoked
     */
    public boolean isTokenRevoked(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isEmpty()) {
            // Tokens without JTI cannot be individually revoked —
            // they are still protected by expiry + signature.
            return false;
        }
        try {
            // Check the plain-string key (legacy format)
            Boolean plainKey = redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
            if (Boolean.TRUE.equals(plainKey)) {
                log.warn("Revoked token used in chat-service: jti={}", jti);
                return true;
            }

            // Check the Spring Data Redis hash entry
            Boolean hashKey = redisTemplate.hasKey(REDIS_HASH_PREFIX + jti);
            if (Boolean.TRUE.equals(hashKey)) {
                log.warn("Revoked token used in chat-service (hash): jti={}", jti);
                return true;
            }

            return false;
        } catch (Exception ex) {
            log.warn("Redis unavailable when checking token revocation for jti={} — "
                    + "allowing request (fail-open). Error: {}", jti, ex.getMessage());
            return false;
        }
    }
}

