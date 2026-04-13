package com.af.recruitable.chat.controller;

import com.af.recruitable.chat.constant.RoomType;
import com.af.recruitable.chat.dto.*;
import com.af.recruitable.chat.security.OrgMembershipVerifier;
import com.af.recruitable.chat.security.SecurityUtils;
import com.af.recruitable.chat.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Chat room & message REST endpoints")
public class ChatRestController {

    private static final String ORG_TOPIC_PREFIX = "/topic/org.";
    private static final String ROOMS_SUFFIX = ".rooms";
    private static final String ROOM_SEGMENT = ".room.";
    private static final String MEMBERS_SUFFIX = ".members";
    private static final String READ_SUFFIX = ".read";
    private static final String EDIT_SUFFIX = ".edit";

    private final ChatService chatService;
    private final FileStorageService fileStorageService;
    private final PresenceService presenceService;
    private final ChatEventPublisher eventPublisher;
    private final OrgMembershipVerifier orgMembershipVerifier;
    private final OrgMemberService orgMemberService;

    // ── Organization Members (for user selection in chat UI) ──────────────────────

    @GetMapping("/org-members")
    @Operation(summary = "List organization members",
            description = "List all org members for user selection (DM, group add). Supports search and pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated list of org members")
    public ResponseEntity<PageResponse<OrgMemberResponse>> listOrgMembers(
            @Parameter(description = "Search by name or email") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();

        PageResponse<OrgMemberResponse> members = orgMemberService.listOrgMembers(search, page, size, jwtToken, userId);
        log.info("Listed {} org members for user {}", members.getContent().size(), userId);
        return ResponseEntity.ok(members);
    }

    // ── Rooms ────────────────────────────────────────────────────────────────────

    @PostMapping("/rooms")
    @Operation(summary = "Create a chat room",
            description = """
                    Create a PRIVATE, GROUP, or PUBLIC room.
                    - **PRIVATE**: pass exactly 1 user ID in `member_user_ids`. Returns existing room if one already exists.
                    - **GROUP**: pass a `name` and 0+ user IDs in `member_user_ids`. Creator is auto-added as OWNER.
                    - **PUBLIC**: pass a `name`. Only ADMIN users can create PUBLIC rooms. All org members can access them.
                    """)
    @ApiResponse(responseCode = "201", description = "Room created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "403", description = "Not authorized (e.g. non-admin creating PUBLIC room)")
    public ResponseEntity<ChatRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();

        ChatRoomResponse room = chatService.createRoom(request, userId, orgId, SecurityUtils.isCurrentUserAdmin());
        enrichSingleRoom(room, jwtToken, userId);
        broadcastRoomEvent(orgId, "ROOM_CREATED", userId, room.getId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @PostMapping("/users/{targetUserId}/rooms/private")
    @Operation(summary = "Get or create a private room with a user",
            description = "Resolves the 1-on-1 room with the target user, creating it if needed. " +
                    "Use this when you want to open a DM conversation without sending a message yet.")
    @ApiResponse(responseCode = "200", description = "Private room (existing or newly created)")
    public ResponseEntity<ChatRoomResponse> getOrCreatePrivateRoom(
            @Parameter(description = "The other user's ID") @PathVariable UUID targetUserId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();

        ChatRoomResponse room = chatService.getOrCreatePrivateRoom(targetUserId, userId, orgId);
        enrichSingleRoom(room, jwtToken, userId);
        broadcastRoomEvent(orgId, "ROOM_UPSERTED", userId, room.getId(), null);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/rooms")
    @Operation(summary = "List my rooms",
            description = "Returns all rooms the current user can access: rooms they belong to + org-wide PUBLIC rooms. " +
                    "Each room includes enriched member profiles (name, email, avatar), unread count, and last message. " +
                    "PRIVATE rooms auto-set their name to the other user's display name.")
    @ApiResponse(responseCode = "200", description = "List of rooms")
    public ResponseEntity<List<ChatRoomResponse>> getRooms() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();

        List<ChatRoomResponse> rooms = chatService.getRooms(userId, orgId);
        enrichRoomMembers(rooms, jwtToken, userId);
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/rooms/{roomId}/members/{userId}")
    @Operation(summary = "Add member to room",
            description = "Add a user to a GROUP room. Allowed for room OWNER, room ADMIN, or org ADMIN.")
    @ApiResponse(responseCode = "200", description = "Updated room with new member")
    @ApiResponse(responseCode = "400", description = "Not a GROUP room")
    @ApiResponse(responseCode = "403", description = "Not authorized")
    @ApiResponse(responseCode = "409", description = "User already a member")
    public ResponseEntity<ChatRoomResponse> addMember(
            @Parameter(description = "Room ID") @PathVariable UUID roomId,
            @Parameter(description = "User ID to add") @PathVariable UUID userId) {
        UUID requesterId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();

        orgMembershipVerifier.verifyRoomOrgMembership(roomId, orgId);
        ChatRoomResponse room = chatService.addMember(roomId, userId, requesterId, orgId, SecurityUtils.isCurrentUserAdmin());
        enrichSingleRoom(room, jwtToken, requesterId);
        broadcastRoomEvent(orgId, "MEMBER_ADDED", userId, roomId, null);
        eventPublisher.publish(roomMembersTopic(orgId, roomId), WebSocketEventDto.builder()
                .event("MEMBER_ADDED")
                .userId(userId)
                .roomId(roomId)
                .timestamp(System.currentTimeMillis())
                .build());
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{roomId}/members/{userId}")
    @Operation(summary = "Remove member from room",
            description = "Remove a user from a GROUP room. Allowed for room OWNER, room ADMIN, or org ADMIN. " +
                    "Cannot remove the last OWNER.")
    @ApiResponse(responseCode = "204", description = "Member removed")
    @ApiResponse(responseCode = "400", description = "Not a GROUP room or last owner")
    @ApiResponse(responseCode = "403", description = "Not authorized")
    @ApiResponse(responseCode = "404", description = "User not a member")
    public ResponseEntity<Void> removeMember(
            @Parameter(description = "Room ID") @PathVariable UUID roomId,
            @Parameter(description = "User ID to remove") @PathVariable UUID userId) {
        UUID requesterId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        orgMembershipVerifier.verifyRoomOrgMembership(roomId, orgId);
        chatService.removeMember(roomId, userId, requesterId, orgId, SecurityUtils.isCurrentUserAdmin());
        broadcastRoomEvent(orgId, "MEMBER_REMOVED", userId, roomId, null);
        eventPublisher.publish(roomMembersTopic(orgId, roomId), WebSocketEventDto.builder()
                .event("MEMBER_REMOVED")
                .userId(userId)
                .roomId(roomId)
                .timestamp(System.currentTimeMillis())
                .build());
        return ResponseEntity.noContent().build();
    }

    // ── Messages ─────────────────────────────────────────────────────────────────

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Get message history",
            description = "Paginated message history for a room. Within each page, messages are in chronological order " +
                    "(oldest first, newest last — ready for chat UI display). " +
                    "Use `page=0` for latest messages, increment page to load older messages.")
    @ApiResponse(responseCode = "200", description = "Paginated messages")
    @ApiResponse(responseCode = "403", description = "Not a member of this room")
    public ResponseEntity<PageResponse<ChatMessageResponse>> getMessages(
            @Parameter(description = "Room ID") @PathVariable UUID roomId,
            @Parameter(description = "Page number (0-based, 0 = newest)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        orgMembershipVerifier.verifyRoomAccess(roomId, userId, orgId);
        return ResponseEntity.ok(chatService.getMessages(roomId, userId, orgId, page, size));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Send a message to a room",
            description = """
                    Send a TEXT or FILE message to an existing room.
                    - For **TEXT**: send `{ "body": "Hello!" }`
                    - For **FILE**: first upload via `POST /files/upload`, then send `{ "type": "FILE", "file_url": "...", "file_name": "...", "file_size": 1234, "file_content_type": "image/png" }`
                    - For **replies**: include `parent_message_id`
                    
                    The sender name is auto-resolved from your JWT — no need to send it.
                    """)
    @ApiResponse(responseCode = "201", description = "Message sent")
    @ApiResponse(responseCode = "400", description = "Invalid message payload")
    @ApiResponse(responseCode = "403", description = "Not a member of this room")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Parameter(description = "Room ID") @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        orgMembershipVerifier.verifyRoomAccess(roomId, userId, orgId);
        request.setRoomId(roomId);
        ensureSenderName(request);

        ChatMessageResponse response = chatService.sendMessage(request, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, roomId), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/users/{targetUserId}/messages")
    @Operation(summary = "Send a direct private message",
            description = """
                    Creates or reuses the PRIVATE room with the target user and sends the message in one call.
                    Use this as a shortcut instead of calling `POST /users/{id}/rooms/private` + `POST /rooms/{id}/messages` separately.
                    
                    Returns the sent message (check `room_id` to know which room it went to).
                    
                    **Important**: `targetUserId` must be the *other* user's ID, not your own.
                    Once a room exists, prefer `POST /rooms/{roomId}/messages` for subsequent messages.
                    """)
    @ApiResponse(responseCode = "201", description = "Message sent")
    @ApiResponse(responseCode = "400", description = "Cannot DM yourself or invalid payload")
    public ResponseEntity<ChatMessageResponse> sendPrivateMessage(
            @Parameter(description = "Target user ID") @PathVariable UUID targetUserId,
            @Valid @RequestBody SendPrivateMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();

        if (targetUserId.equals(userId)) {
            throw new com.af.recruitable.chat.exception.ChatException(
                    "Cannot send a private message to yourself. " +
                    "If you are replying in an existing conversation, use POST /api/v1/chat/rooms/{roomId}/messages instead.",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        request.setTargetUserId(targetUserId);
        ensureSenderNamePrivate(request);

        ChatMessageResponse response = chatService.sendPrivateMessage(request, userId, orgId);
        broadcastRoomEvent(orgId, "ROOM_UPSERTED", userId, response.getRoomId(), null);
        eventPublisher.publish(roomTopic(orgId, response.getRoomId()), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message",
            description = "Edit the body of your own message. Only the original sender can edit.")
    @ApiResponse(responseCode = "200", description = "Message edited")
    @ApiResponse(responseCode = "403", description = "Not the sender")
    @ApiResponse(responseCode = "404", description = "Message not found")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @Parameter(description = "Message ID") @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ChatMessageResponse response = chatService.editMessage(messageId, request, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, response.getRoomId()) + EDIT_SUFFIX, response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete a message (soft)",
            description = "Soft-deletes a message (body is cleared, `deleted=true`). Only the original sender can delete.")
    @ApiResponse(responseCode = "204", description = "Message deleted")
    @ApiResponse(responseCode = "403", description = "Not the sender")
    @ApiResponse(responseCode = "404", description = "Message not found")
    public ResponseEntity<Void> deleteMessage(
            @Parameter(description = "Message ID") @PathVariable UUID messageId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        UUID roomId = chatService.deleteMessage(messageId, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, roomId), WebSocketEventDto.builder()
                .event("MESSAGE_DELETED")
                .userId(userId)
                .roomId(roomId)
                .messageId(messageId)
                .timestamp(System.currentTimeMillis())
                .build());
        return ResponseEntity.noContent().build();
    }

    // ── Read Receipts ────────────────────────────────────────────────────────────

    @PostMapping("/rooms/{roomId}/read")
    @Operation(summary = "Mark room as read",
            description = "Updates last_read_at for the current user in this room. " +
                    "Call this when the user opens/views a room to clear the unread count.")
    @ApiResponse(responseCode = "200", description = "Marked as read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "Room ID") @PathVariable UUID roomId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        chatService.markAsRead(roomId, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, roomId) + READ_SUFFIX,
                WebSocketEventDto.builder()
                        .event("READ_RECEIPT")
                        .userId(userId)
                        .roomId(roomId)
                        .timestamp(System.currentTimeMillis())
                        .build());
        return ResponseEntity.ok().build();
    }

    // ── File Upload ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file for chat",
            description = """
                    Uploads a file to S3 and returns the download URL + metadata.
                    After uploading, send the file as a message using `POST /rooms/{roomId}/messages` with:
                    ```json
                    {
                      "type": "FILE",
                      "file_url": "<returned file_url>",
                      "file_name": "<returned file_name>",
                      "file_size": <returned file_size>,
                      "file_content_type": "<returned content_type>"
                    }
                    ```
                    """)
    @ApiResponse(responseCode = "200", description = "File uploaded successfully")
    @ApiResponse(responseCode = "400", description = "File is empty")
    @ApiResponse(responseCode = "413", description = "File too large")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        FileUploadResponse response = fileStorageService.uploadChatFile(file, orgId);
        return ResponseEntity.ok(response);
    }

    // ── Presence ─────────────────────────────────────────────────────────────────

    @GetMapping("/users/online")
    @Operation(summary = "Get online user IDs",
            description = "Returns the set of currently online user IDs in the current organization. " +
                    "Use these IDs to highlight online status in the UI. " +
                    "For full profile details, cross-reference with room members or call GET /org-members.")
    @ApiResponse(responseCode = "200", description = "Set of online user IDs")
    public ResponseEntity<Set<UUID>> getOnlineUserIds() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        Set<UUID> onlineIds = presenceService.getOnlineUsers(orgId);
        return ResponseEntity.ok(onlineIds);
    }

    @GetMapping("/users/online/details")
    @Operation(summary = "Get online users with profile details",
            description = "Returns online users with full profile information (name, email, avatar). " +
                    "Heavier than GET /users/online — use only when you need profile data.")
    @ApiResponse(responseCode = "200", description = "List of online users with profiles")
    public ResponseEntity<List<OrgMemberResponse>> getOnlineUsersDetails() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        String jwtToken = SecurityUtils.getCurrentJwtToken();
        Set<UUID> onlineIds = presenceService.getOnlineUsers(orgId);
        List<OrgMemberResponse> onlineUsers = orgMemberService.listOrgMembersByIds(onlineIds, jwtToken, userId);
        return ResponseEntity.ok(onlineUsers);
    }

    // ── Room Member Enrichment ───────────────────────────────────────────────────

    /**
     * Enrich room member responses with profile data (name, email, avatar)
     * by batch-fetching from the org member service.
     * Also sets PRIVATE room names to the other user's display name.
     */
    private void enrichRoomMembers(List<ChatRoomResponse> rooms, String jwtToken, UUID currentUserId) {
        Set<UUID> allMemberIds = rooms.stream()
                .flatMap(r -> r.getMembers().stream())
                .map(RoomMemberResponse::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (allMemberIds.isEmpty()) return;

        try {
            List<OrgMemberResponse> profiles = orgMemberService.listOrgMembersByIds(allMemberIds, jwtToken, currentUserId);
            Map<UUID, OrgMemberResponse> profileMap = profiles.stream()
                    .filter(p -> p.getUserId() != null)
                    .collect(Collectors.toMap(OrgMemberResponse::getUserId, Function.identity(), (a, b) -> a));

            for (ChatRoomResponse room : rooms) {
                for (RoomMemberResponse member : room.getMembers()) {
                    OrgMemberResponse profile = profileMap.get(member.getUserId());
                    if (profile != null) {
                        member.setFullName(profile.getFullName());
                        member.setEmail(profile.getEmail());
                        member.setAvatarUrl(profile.getAvatarUrl());
                    }
                }
                // For PRIVATE rooms, auto-set room name to the other user's display name
                if (room.getType() == RoomType.PRIVATE && (room.getName() == null || room.getName().isBlank())) {
                    room.getMembers().stream()
                            .filter(m -> !m.getUserId().equals(currentUserId))
                            .findFirst()
                            .ifPresent(other -> {
                                room.setName(other.getFullName() != null ? other.getFullName() : "Direct Message");
                                room.setAvatarUrl(other.getAvatarUrl());
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich room members with profiles: {}", e.getMessage());
        }
    }

    private void enrichSingleRoom(ChatRoomResponse room, String jwtToken, UUID currentUserId) {
        enrichRoomMembers(List.of(room), jwtToken, currentUserId);
    }

    // ── Sender Name Resolution ───────────────────────────────────────────────────

    private void ensureSenderName(SendMessageRequest request) {
        if (request.getSenderName() == null || request.getSenderName().isBlank()) {
            try {
                request.setSenderName(SecurityUtils.getCurrentEmail());
            } catch (Exception e) {
                request.setSenderName(SecurityUtils.getCurrentUserId().toString());
            }
        }
    }

    private void ensureSenderNamePrivate(SendPrivateMessageRequest request) {
        if (request.getSenderName() == null || request.getSenderName().isBlank()) {
            try {
                request.setSenderName(SecurityUtils.getCurrentEmail());
            } catch (Exception e) {
                request.setSenderName(SecurityUtils.getCurrentUserId().toString());
            }
        }
    }

    // ── WebSocket Topic Helpers ──────────────────────────────────────────────────

    private void broadcastRoomEvent(UUID orgId, String eventName, UUID actorUserId, UUID roomId, UUID messageId) {
        eventPublisher.publish(orgRoomsTopic(orgId), WebSocketEventDto.builder()
                .event(eventName)
                .userId(actorUserId)
                .roomId(roomId)
                .messageId(messageId)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private String orgRoomsTopic(UUID orgId) {
        return ORG_TOPIC_PREFIX + orgId + ROOMS_SUFFIX;
    }

    private String roomTopic(UUID orgId, UUID roomId) {
        return ORG_TOPIC_PREFIX + orgId + ROOM_SEGMENT + roomId;
    }

    private String roomMembersTopic(UUID orgId, UUID roomId) {
        return roomTopic(orgId, roomId) + MEMBERS_SUFFIX;
    }
}
