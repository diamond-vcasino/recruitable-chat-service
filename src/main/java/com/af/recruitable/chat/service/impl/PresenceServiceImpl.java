package com.af.recruitable.chat.service.impl;

import com.af.recruitable.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceServiceImpl implements PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "chat:presence:";
    private static final String TYPING_KEY_PREFIX = "chat:typing:";
    private static final Duration TYPING_TTL = Duration.ofSeconds(4);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void setUserOnline(UUID orgId, UUID userId) {
        String key = PRESENCE_KEY_PREFIX + orgId;
        redisTemplate.opsForSet().add(key, userId.toString());
        log.debug("User {} marked ONLINE in org {}", userId, orgId);
    }

    @Override
    public void setUserOffline(UUID orgId, UUID userId) {
        String key = PRESENCE_KEY_PREFIX + orgId;
        redisTemplate.opsForSet().remove(key, userId.toString());
        log.debug("User {} marked OFFLINE in org {}", userId, orgId);
    }

    @Override
    public Set<UUID> getOnlineUsers(UUID orgId) {
        String key = PRESENCE_KEY_PREFIX + orgId;
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    @Override
    public void publishTyping(UUID orgId, UUID roomId, UUID userId, boolean typing) {
        String key = TYPING_KEY_PREFIX + roomId + ":" + userId;
        if (typing) {
            redisTemplate.opsForValue().set(key, "1", TYPING_TTL);
        } else {
            redisTemplate.delete(key);
        }
    }

    @Override
    public boolean isUserTyping(UUID roomId, UUID userId) {
        String key = TYPING_KEY_PREFIX + roomId + ":" + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

