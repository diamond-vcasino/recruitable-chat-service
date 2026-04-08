package com.af.recruitable.chat.service;

import java.util.Set;
import java.util.UUID;

public interface PresenceService {

    /**
     * Mark a user as online within their organization.
     */
    void setUserOnline(UUID orgId, UUID userId);

    /**
     * Mark a user as offline within their organization.
     */
    void setUserOffline(UUID orgId, UUID userId);

    /**
     * Get all currently online user IDs in an organization.
     */
    Set<UUID> getOnlineUsers(UUID orgId);

    /**
     * Publish a typing indicator (stored briefly in Redis with TTL).
     */
    void publishTyping(UUID orgId, UUID roomId, UUID userId, boolean typing);

    /**
     * Check if a user is currently typing in a room.
     */
    boolean isUserTyping(UUID roomId, UUID userId);
}

