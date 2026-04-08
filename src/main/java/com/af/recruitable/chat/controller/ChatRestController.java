package com.af.recruitable.chat.controller;

import com.af.recruitable.chat.dto.*;
import com.af.recruitable.chat.security.SecurityUtils;
import com.af.recruitable.chat.service.ChatEventPublisher;
import com.af.recruitable.chat.service.ChatService;
import com.af.recruitable.chat.service.FileStorageService;
import com.af.recruitable.chat.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    // ── Rooms ────────────────────────────────────────────────────────────────────

    @PostMapping("/rooms")
    @Operation(summary = "Create a chat room", description = "Create a PRIVATE, GROUP, or ADMIN-only PUBLIC room")
    public ResponseEntity<ChatRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ChatRoomResponse room = chatService.createRoom(request, userId, orgId, SecurityUtils.isCurrentUserAdmin());
        broadcastRoomEvent(orgId, "ROOM_CREATED", userId, room.getId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @PostMapping("/users/{targetUserId}/rooms/private")
    @Operation(summary = "Get or create a private room with a user", description = "Resolves the 1-on-1 room with the target user, creating it if needed")
    public ResponseEntity<ChatRoomResponse> getOrCreatePrivateRoom(@PathVariable UUID targetUserId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ChatRoomResponse room = chatService.getOrCreatePrivateRoom(targetUserId, userId, orgId);
        broadcastRoomEvent(orgId, "ROOM_UPSERTED", userId, room.getId(), null);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/rooms")
    @Operation(summary = "List my rooms", description = "List all rooms I can access, including org-wide PUBLIC rooms")
    public ResponseEntity<List<ChatRoomResponse>> getRooms() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(chatService.getRooms(userId, orgId));
    }

    @PostMapping("/rooms/{roomId}/members/{userId}")
    @Operation(summary = "Add member to room", description = "Add a user to a GROUP room. Allowed for room OWNER/room ADMIN/org ADMIN.")
    public ResponseEntity<ChatRoomResponse> addMember(@PathVariable UUID roomId, @PathVariable UUID userId) {
        UUID requesterId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ChatRoomResponse room = chatService.addMember(roomId, userId, requesterId, orgId, SecurityUtils.isCurrentUserAdmin());
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
    @Operation(summary = "Remove member from room", description = "Remove a user from a GROUP room. Allowed for room OWNER/room ADMIN/org ADMIN.")
    public ResponseEntity<Void> removeMember(@PathVariable UUID roomId, @PathVariable UUID userId) {
        UUID requesterId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
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
    @Operation(summary = "Get message history", description = "Paginated message history for a room")
    public ResponseEntity<PageResponse<ChatMessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(chatService.getMessages(roomId, userId, orgId, page, size));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Send a message via REST", description = "Send a message to an existing room")
    public ResponseEntity<ChatMessageResponse> sendMessage(@PathVariable UUID roomId, @Valid @RequestBody SendMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        request.setRoomId(roomId);

        ChatMessageResponse response = chatService.sendMessage(request, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, roomId), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/users/{targetUserId}/messages")
    @Operation(summary = "Send a direct private message to a user", description = "Creates or reuses the PRIVATE room with the target user and sends the message")
    public ResponseEntity<ChatMessageResponse> sendPrivateMessage(
            @PathVariable UUID targetUserId,
            @Valid @RequestBody SendPrivateMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        request.setTargetUserId(targetUserId);

        ChatMessageResponse response = chatService.sendPrivateMessage(request, userId, orgId);
        broadcastRoomEvent(orgId, "ROOM_UPSERTED", userId, response.getRoomId(), null);
        eventPublisher.publish(roomTopic(orgId, response.getRoomId()), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message")
    public ResponseEntity<ChatMessageResponse> editMessage(@PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ChatMessageResponse response = chatService.editMessage(messageId, request, userId, orgId);
        eventPublisher.publish(roomTopic(orgId, response.getRoomId()) + EDIT_SUFFIX, response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete a message (soft)")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID messageId) {
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
    @Operation(summary = "Mark room as read", description = "Updates last_read_at for the current user in this room")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID roomId) {
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
    @Operation(summary = "Upload a file for chat", description = "Uploads to S3 and returns the file URL")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        FileUploadResponse response = fileStorageService.uploadChatFile(file, orgId);
        return ResponseEntity.ok(response);
    }

    // ── Presence ─────────────────────────────────────────────────────────────────

    @GetMapping("/users/online")
    @Operation(summary = "Get online users", description = "List all online users in the current organization")
    public ResponseEntity<Set<UUID>> getOnlineUsers() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(presenceService.getOnlineUsers(orgId));
    }

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
