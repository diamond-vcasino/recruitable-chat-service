package com.af.recruitable.chat.service.impl;

import com.af.recruitable.chat.constant.MemberRole;
import com.af.recruitable.chat.constant.MessageType;
import com.af.recruitable.chat.constant.RoomType;
import com.af.recruitable.chat.dto.*;
import com.af.recruitable.chat.entity.ChatMessage;
import com.af.recruitable.chat.entity.ChatRoom;
import com.af.recruitable.chat.entity.ChatRoomMember;
import com.af.recruitable.chat.exception.ChatException;
import com.af.recruitable.chat.repository.ChatMessageRepository;
import com.af.recruitable.chat.repository.ChatRoomRepository;
import com.af.recruitable.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;

    // ── Room Operations ─────────────────────────────────────────────────────────

    @Override
    public ChatRoomResponse createRoom(CreateRoomRequest request, UUID userId, UUID orgId, boolean requesterIsAdmin) {
        RoomType effectiveType = resolveRoomType(request);

        return switch (effectiveType) {
            case PRIVATE -> getOrCreatePrivateRoom(validatePrivateTargetUser(request), userId, orgId);
            case GROUP -> createGroupRoom(request, userId, orgId);
            case PUBLIC -> createPublicRoom(request, userId, orgId, requesterIsAdmin);
        };
    }

    /**
     * Auto-correct room type when the payload doesn't match the declared type.
     * PRIVATE rooms are strictly 1-on-1 (exactly 1 target in memberUserIds).
     * If PRIVATE is sent with multiple members or a name, it's actually a GROUP room.
     */
    private RoomType resolveRoomType(CreateRoomRequest request) {
        if (request.getType() == RoomType.PRIVATE) {
            int memberCount = request.getMemberUserIds() != null ? request.getMemberUserIds().size() : 0;
            boolean hasName = request.getName() != null && !request.getName().isBlank();

            if (memberCount > 1 || (memberCount != 1 && hasName)) {
                log.info("Auto-corrected room type PRIVATE → GROUP (name={}, memberCount={})",
                        request.getName(), memberCount);
                return RoomType.GROUP;
            }
        }
        return request.getType();
    }

    @Override
    public ChatRoomResponse getOrCreatePrivateRoom(UUID otherUserId, UUID userId, UUID orgId) {
        return toRoomResponse(getOrCreatePrivateRoomEntity(otherUserId, userId, orgId), userId);
    }

    private ChatRoom getOrCreatePrivateRoomEntity(UUID otherUserId, UUID userId, UUID orgId) {
        if (otherUserId == null) {
            throw ChatException.badRequest("targetUserId is required");
        }
        if (otherUserId.equals(userId)) {
            throw ChatException.badRequest("Cannot create a private room with yourself");
        }

        String orgIdStr = orgId.toString();
        String userIdStr = userId.toString();
        String otherIdStr = otherUserId.toString();

        return roomRepository.findPrivateRoom(orgIdStr, userIdStr, otherIdStr)
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .organizationId(orgIdStr)
                            .type(RoomType.PRIVATE)
                            .build();

                    addMemberToRoom(room, userId, MemberRole.MEMBER);
                    addMemberToRoom(room, otherUserId, MemberRole.MEMBER);
                    room = roomRepository.save(room);

                    log.info("Private room created: id={}, org={}, users=[{}, {}]",
                            room.getId(), orgId, userId, otherUserId);
                    return room;
                });
    }

    private UUID validatePrivateTargetUser(CreateRoomRequest request) {
        if (request.getMemberUserIds() == null || request.getMemberUserIds().size() != 1) {
            throw ChatException.badRequest("PRIVATE rooms require exactly one other member");
        }
        return request.getMemberUserIds().get(0);
    }

    private ChatRoomResponse createGroupRoom(CreateRoomRequest request, UUID userId, UUID orgId) {
        String roomName = normalizeRequired(request.getName(), "GROUP rooms require a name");

        ChatRoom room = ChatRoom.builder()
                .organizationId(orgId.toString())
                .type(RoomType.GROUP)
                .name(roomName)
                .description(normalizeOptional(request.getDescription()))
                .build();

        addMemberToRoom(room, userId, MemberRole.OWNER);
        if (request.getMemberUserIds() != null) {
            for (UUID memberId : request.getMemberUserIds()) {
                if (!memberId.equals(userId) && !isMember(room, memberId)) {
                    addMemberToRoom(room, memberId, MemberRole.MEMBER);
                }
            }
        }

        room = roomRepository.save(room);
        log.info("Group room created: id={}, org={}, name={}", room.getId(), orgId, roomName);
        return toRoomResponse(room, userId);
    }

    private ChatRoomResponse createPublicRoom(CreateRoomRequest request, UUID userId, UUID orgId, boolean requesterIsAdmin) {
        if (!requesterIsAdmin) {
            throw ChatException.forbidden("Only ADMIN users can create PUBLIC rooms");
        }

        String roomName = normalizeRequired(request.getName(), "PUBLIC rooms require a name");
        ChatRoom room = ChatRoom.builder()
                .organizationId(orgId.toString())
                .type(RoomType.PUBLIC)
                .name(roomName)
                .description(normalizeOptional(request.getDescription()))
                .build();
        addMemberToRoom(room, userId, MemberRole.OWNER);
        room = roomRepository.save(room);

        log.info("Public room created: id={}, org={}, name={}, creator={}", room.getId(), orgId, roomName, userId);
        return toRoomResponse(room, userId);
    }

    @Override
    public List<ChatRoomResponse> getRooms(UUID userId, UUID orgId) {
        return roomRepository.findAccessibleRoomsByUserAndOrg(userId.toString(), orgId.toString()).stream()
                .map(room -> toRoomResponse(room, userId))
                .toList();
    }

    @Override
    public ChatRoomResponse addMember(UUID roomId, UUID memberUserId, UUID requesterId, UUID orgId, boolean requesterIsAdmin) {
        ChatRoom room = getOrgRoom(roomId, orgId);
        if (room.getType() != RoomType.GROUP) {
            throw ChatException.badRequest("Members can only be added to GROUP rooms");
        }
        assertCanManageGroupRoom(room, requesterId, requesterIsAdmin);
        if (isMember(room, memberUserId)) {
            throw ChatException.conflict("User is already a member of this room");
        }

        addMemberToRoom(room, memberUserId, MemberRole.MEMBER);
        room = roomRepository.save(room);
        log.info("Member added: room={}, user={}, requester={}", roomId, memberUserId, requesterId);
        return toRoomResponse(room, requesterId);
    }

    @Override
    public void removeMember(UUID roomId, UUID memberUserId, UUID requesterId, UUID orgId, boolean requesterIsAdmin) {
        ChatRoom room = getOrgRoom(roomId, orgId);
        if (room.getType() != RoomType.GROUP) {
            throw ChatException.badRequest("Members can only be removed from GROUP rooms");
        }
        assertCanManageGroupRoom(room, requesterId, requesterIsAdmin);

        ChatRoomMember member = findMember(room, memberUserId)
                .orElseThrow(() -> ChatException.notFound("User is not a member of this room"));

        if (member.getRole() == MemberRole.OWNER
                && room.getMembers().stream().filter(m -> m.getRole() == MemberRole.OWNER).count() <= 1) {
            throw ChatException.badRequest("Cannot remove the last OWNER from the room");
        }

        room.getMembers().removeIf(m -> memberUserId.toString().equals(m.getUserId()));
        roomRepository.save(room);
        log.info("Member removed: room={}, user={}, requester={}", roomId, memberUserId, requesterId);
    }

    // ── Message Operations ──────────────────────────────────────────────────────

    @Override
    public PageResponse<ChatMessageResponse> getMessages(UUID roomId, UUID userId, UUID orgId, int page, int size) {
        ChatRoom room = getOrgRoom(roomId, orgId);
        assertCanAccessRoom(room, userId);

        Page<ChatMessage> messagePage = messageRepository
                .findByRoomIdAndDeletedFalseOrderByCreatedAtDesc(roomId.toString(), PageRequest.of(page, size));

        // Reverse: DB returns newest-first (DESC) for pagination, but chat UI
        // needs oldest-first (ASC) within each page so newest messages appear at bottom.
        List<ChatMessageResponse> chronological = new ArrayList<>(
                messagePage.map(this::toMessageResponse).getContent());
        Collections.reverse(chronological);

        return PageResponse.<ChatMessageResponse>builder()
                .content(chronological)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .first(messagePage.isFirst())
                .last(messagePage.isLast())
                .build();
    }

    @Override
    public ChatMessageResponse sendMessage(SendMessageRequest request, UUID senderId, UUID orgId) {
        if (request.getRoomId() == null) {
            throw ChatException.badRequest("roomId is required");
        }
        validateMessagePayload(request.getType(), request.getBody(), request.getFileUrl());

        ChatRoom room = getOrgRoom(request.getRoomId(), orgId);
        assertCanAccessRoom(room, senderId);

        ChatMessage message = ChatMessage.builder()
                .roomId(room.getId())
                .organizationId(room.getOrganizationId())
                .senderId(senderId.toString())
                .senderName(normalizeOptional(request.getSenderName()))
                .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                .body(normalizeOptional(request.getBody()))
                .parentMessageId(request.getParentMessageId() != null ? request.getParentMessageId().toString() : null)
                .fileUrl(normalizeOptional(request.getFileUrl()))
                .fileName(normalizeOptional(request.getFileName()))
                .fileSize(request.getFileSize())
                .fileContentType(normalizeOptional(request.getFileContentType()))
                .createdAt(Instant.now())   // Always set explicitly — do not rely solely on @CreatedDate auditing
                .build();

        message = messageRepository.save(message);
        room.setUpdatedAt(Instant.now());
        roomRepository.save(room);

        log.debug("Message sent: id={}, room={}, sender={}", message.getId(), request.getRoomId(), senderId);
        return toMessageResponse(message);
    }

    @Override
    public ChatMessageResponse sendPrivateMessage(SendPrivateMessageRequest request, UUID senderId, UUID orgId) {
        validateMessagePayload(request.getType(), request.getBody(), request.getFileUrl());
        ChatRoom room = getOrCreatePrivateRoomEntity(request.getTargetUserId(), senderId, orgId);
        return sendMessage(SendMessageRequest.builder()
                .roomId(UUID.fromString(room.getId()))
                .body(request.getBody())
                .type(request.getType())
                .parentMessageId(request.getParentMessageId())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .fileContentType(request.getFileContentType())
                .senderName(request.getSenderName())
                .build(), senderId, orgId);
    }

    @Override
    public ChatMessageResponse editMessage(UUID messageId, EditMessageRequest request, UUID userId, UUID orgId) {
        ChatMessage message = messageRepository.findById(messageId.toString())
                .orElseThrow(() -> ChatException.notFound("Message not found"));

        if (!message.getOrganizationId().equals(orgId.toString())) {
            throw ChatException.forbidden("Message does not belong to your organization");
        }
        if (!message.getSenderId().equals(userId.toString())) {
            throw ChatException.forbidden("You can only edit your own messages");
        }
        if (message.isDeleted()) {
            throw ChatException.badRequest("Cannot edit a deleted message");
        }

        message.setBody(normalizeRequired(request.getBody(), "body is required"));
        message.setEdited(true);
        message.setEditedAt(Instant.now());
        message = messageRepository.save(message);

        log.debug("Message edited: id={}", messageId);
        return toMessageResponse(message);
    }

    @Override
    public UUID deleteMessage(UUID messageId, UUID userId, UUID orgId) {
        ChatMessage message = messageRepository.findById(messageId.toString())
                .orElseThrow(() -> ChatException.notFound("Message not found"));

        if (!message.getOrganizationId().equals(orgId.toString())) {
            throw ChatException.forbidden("Message does not belong to your organization");
        }
        if (!message.getSenderId().equals(userId.toString())) {
            throw ChatException.forbidden("You can only delete your own messages");
        }

        UUID roomId = UUID.fromString(message.getRoomId());
        message.setDeleted(true);
        message.setBody(null);
        messageRepository.save(message);

        log.debug("Message deleted (soft): id={}", messageId);
        return roomId;
    }

    @Override
    public void markAsRead(UUID roomId, UUID userId, UUID orgId) {
        ChatRoom room = getOrgRoom(roomId, orgId);
        assertCanAccessRoom(room, userId);

        ChatRoomMember member = getOrCreateTrackedMembership(room, userId);
        member.setLastReadAt(Instant.now());
        roomRepository.save(room);
        log.debug("Marked as read: room={}, user={}", roomId, userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ChatRoom getOrgRoom(UUID roomId, UUID orgId) {
        ChatRoom room = roomRepository.findById(roomId.toString())
                .orElseThrow(() -> ChatException.notFound("Room not found: " + roomId));
        if (!room.getOrganizationId().equals(orgId.toString())) {
            throw ChatException.forbidden("Room does not belong to your organization");
        }
        return room;
    }

    private boolean isMember(ChatRoom room, UUID userId) {
        return room.getMembers().stream()
                .anyMatch(m -> userId.toString().equals(m.getUserId()));
    }

    private Optional<ChatRoomMember> findMember(ChatRoom room, UUID userId) {
        return room.getMembers().stream()
                .filter(m -> userId.toString().equals(m.getUserId()))
                .findFirst();
    }

    private void assertCanAccessRoom(ChatRoom room, UUID userId) {
        if (room.getType() == RoomType.PUBLIC) {
            return;
        }
        if (!isMember(room, userId)) {
            throw ChatException.forbidden("You are not allowed to access this room");
        }
    }

    private void assertCanManageGroupRoom(ChatRoom room, UUID requesterId, boolean requesterIsAdmin) {
        if (requesterIsAdmin) {
            return;
        }
        ChatRoomMember requester = findMember(room, requesterId)
                .orElseThrow(() -> ChatException.forbidden("You are not a member of this room"));
        if (requester.getRole() != MemberRole.OWNER && requester.getRole() != MemberRole.ADMIN) {
            throw ChatException.forbidden("Only room OWNER, room ADMIN, or org ADMIN can manage room members");
        }
    }

    private ChatRoomMember getOrCreateTrackedMembership(ChatRoom room, UUID userId) {
        Optional<ChatRoomMember> existingMember = findMember(room, userId);
        if (existingMember.isPresent()) {
            return existingMember.get();
        }
        if (room.getType() != RoomType.PUBLIC) {
            throw ChatException.forbidden("You are not a member of this room");
        }
        return addMemberToRoom(room, userId, MemberRole.MEMBER);
    }

    private ChatRoomMember addMemberToRoom(ChatRoom room, UUID userId, MemberRole role) {
        ChatRoomMember member = ChatRoomMember.builder()
                .userId(userId.toString())
                .role(role)
                .build();
        room.getMembers().add(member);
        return member;
    }

    private void validateMessagePayload(MessageType type, String body, String fileUrl) {
        MessageType effectiveType = type != null ? type : MessageType.TEXT;
        String normalizedBody = normalizeOptional(body);
        String normalizedFileUrl = normalizeOptional(fileUrl);

        if (effectiveType == MessageType.FILE && normalizedFileUrl == null) {
            throw ChatException.badRequest("FILE messages require fileUrl");
        }
        if (effectiveType != MessageType.FILE && normalizedBody == null) {
            throw ChatException.badRequest("Message body is required");
        }
    }

    private long countUnread(ChatRoom room, UUID currentUserId) {
        try {
            String roomId = room.getId();
            String userIdStr = currentUserId.toString();
            Optional<ChatRoomMember> member = findMember(room, currentUserId);
            if (member.isPresent() && member.get().getLastReadAt() != null) {
                return messageRepository.countByRoomIdAndDeletedFalseAndSenderIdNotAndCreatedAtAfter(
                        roomId, userIdStr, member.get().getLastReadAt());
            }
            return messageRepository.countByRoomIdAndDeletedFalseAndSenderIdNot(roomId, userIdStr);
        } catch (Exception e) {
            log.warn("Failed to count unread for room={}, user={}: {}", room.getId(), currentUserId, e.getMessage());
            return 0;
        }
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, UUID currentUserId) {
        List<RoomMemberResponse> members = room.getMembers().stream()
                .map(m -> RoomMemberResponse.builder()
                        .userId(UUID.fromString(m.getUserId()))
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .lastReadAt(m.getLastReadAt())
                        .build())
                .toList();

        ChatMessageResponse lastMessage = null;
        Page<ChatMessage> lastPage = messageRepository
                .findByRoomIdAndDeletedFalseOrderByCreatedAtDesc(room.getId(), PageRequest.of(0, 1));
        if (!lastPage.isEmpty()) {
            lastMessage = toMessageResponse(lastPage.getContent().get(0));
        }

        return ChatRoomResponse.builder()
                .id(UUID.fromString(room.getId()))
                .organizationId(UUID.fromString(room.getOrganizationId()))
                .type(room.getType())
                .name(room.getName())
                .description(room.getDescription())
                .avatarUrl(room.getAvatarUrl())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .members(members)
                .unreadCount(countUnread(room, currentUserId))
                .lastMessage(lastMessage)
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        // Fallback to Instant.EPOCH for legacy messages that have no createdAt stored
        // in MongoDB (prevents null-pointer crashes in frontend sort/display code).
        Instant createdAt = msg.getCreatedAt() != null ? msg.getCreatedAt() : Instant.EPOCH;
        return ChatMessageResponse.builder()
                .id(UUID.fromString(msg.getId()))
                .roomId(UUID.fromString(msg.getRoomId()))
                .senderId(UUID.fromString(msg.getSenderId()))
                .senderName(msg.getSenderName())
                .type(msg.getType())
                .body(msg.getBody())
                .parentMessageId(msg.getParentMessageId() != null ? UUID.fromString(msg.getParentMessageId()) : null)
                .fileUrl(msg.getFileUrl())
                .fileName(msg.getFileName())
                .fileSize(msg.getFileSize())
                .fileContentType(msg.getFileContentType())
                .edited(msg.isEdited())
                .deleted(msg.isDeleted())
                .createdAt(createdAt)
                .editedAt(msg.getEditedAt())
                .build();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw ChatException.badRequest(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
