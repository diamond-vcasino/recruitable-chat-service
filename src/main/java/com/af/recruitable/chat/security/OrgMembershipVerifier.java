package com.af.recruitable.chat.security;

import com.af.recruitable.chat.entity.ChatRoom;
import com.af.recruitable.chat.exception.ChatException;
import com.af.recruitable.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Verifies organization membership for privileged chat operations.
 *
 * <p>The JWT's {@code organizationId} claim is trusted for routing, but for
 * <strong>write operations</strong> (create room, send message, manage members)
 * we cross-check the room's {@code organizationId} stored in MongoDB against
 * the JWT claim. This prevents cross-org access even if a token is somehow
 * tampered with or stale.</p>
 *
 * <h3>Why not call api-backend's database?</h3>
 * <p>The chat-service is intentionally decoupled from PostgreSQL. Instead,
 * we enforce org boundaries using the room's own org metadata in MongoDB.
 * Every room stores its {@code organizationId}, and every member entry
 * is scoped to a room — so verifying the room's org is sufficient.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgMembershipVerifier {

    private final ChatRoomRepository chatRoomRepository;

    /**
     * Verify that the given room belongs to the user's organization.
     *
     * @param roomId the room to check
     * @param orgId  the organization from the JWT
     * @throws ChatException if the room doesn't exist or belongs to another org
     */
    public void verifyRoomOrgMembership(UUID roomId, UUID orgId) {
        ChatRoom room = chatRoomRepository.findById(roomId.toString())
                .orElseThrow(() -> ChatException.notFound("Room not found: " + roomId));

        if (!room.getOrganizationId().equals(orgId.toString())) {
            log.warn("Org mismatch: user org={} tried to access room={} (room org={})",
                    orgId, roomId, room.getOrganizationId());
            throw ChatException.forbidden("You do not have access to this room");
        }
    }

    /**
     * Verify that the given room belongs to the user's organization
     * AND the user is a member of the room (for non-PUBLIC rooms).
     *
     * @param roomId the room to check
     * @param userId the user from the JWT
     * @param orgId  the organization from the JWT
     * @throws ChatException if access is denied
     */
    public void verifyRoomAccess(UUID roomId, UUID userId, UUID orgId) {
        ChatRoom room = chatRoomRepository.findById(roomId.toString())
                .orElseThrow(() -> ChatException.notFound("Room not found: " + roomId));

        if (!room.getOrganizationId().equals(orgId.toString())) {
            log.warn("Org mismatch: user org={} tried to access room={} (room org={})",
                    orgId, roomId, room.getOrganizationId());
            throw ChatException.forbidden("You do not have access to this room");
        }

        // PUBLIC rooms are accessible to all org members
        if (room.getType() != null && "PUBLIC".equals(room.getType().name())) {
            return;
        }

        // For PRIVATE/GROUP rooms, verify membership
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> userId.toString().equals(m.getUserId()));
        if (!isMember) {
            log.warn("Membership denied: user={} is not a member of room={} (org={})",
                    userId, roomId, orgId);
            throw ChatException.forbidden("You are not a member of this room");
        }
    }
}

