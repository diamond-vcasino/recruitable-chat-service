package com.af.recruitable.chat.service;

import com.af.recruitable.chat.dto.*;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    /**
     * Create a new chat room (PRIVATE or GROUP).
     */
    ChatRoomResponse createRoom(CreateRoomRequest request, UUID userId, UUID orgId, boolean requesterIsAdmin);

    /**
     * Resolve an existing PRIVATE room with another user or create it if absent.
     */
    ChatRoomResponse getOrCreatePrivateRoom(UUID otherUserId, UUID userId, UUID orgId);

    /**
     * List all rooms the user is a member of.
     */
    List<ChatRoomResponse> getRooms(UUID userId, UUID orgId);

    /**
     * Get paginated message history for a room. Enforces org ownership.
     */
    PageResponse<ChatMessageResponse> getMessages(UUID roomId, UUID userId, UUID orgId, int page, int size);

    /**
     * Send a message to a room. Returns the saved message DTO.
     */
    ChatMessageResponse sendMessage(SendMessageRequest request, UUID senderId, UUID orgId);

    /**
     * Send a direct private message to another user, creating/reusing the PRIVATE room.
     */
    ChatMessageResponse sendPrivateMessage(SendPrivateMessageRequest request, UUID senderId, UUID orgId);

    /**
     * Edit an existing message (only the original sender can edit).
     */
    ChatMessageResponse editMessage(UUID messageId, EditMessageRequest request, UUID userId, UUID orgId);

    /**
     * Soft-delete a message (only the original sender can delete).
     * Returns the room ID so callers can broadcast the event.
     */
    UUID deleteMessage(UUID messageId, UUID userId, UUID orgId);

    /**
     * Mark all messages in a room as read up to now.
     */
    void markAsRead(UUID roomId, UUID userId, UUID orgId);

    /**
     * Add a member to a GROUP room.
     */
    ChatRoomResponse addMember(UUID roomId, UUID memberUserId, UUID requesterId, UUID orgId, boolean requesterIsAdmin);

    /**
     * Remove a member from a GROUP room.
     */
    void removeMember(UUID roomId, UUID memberUserId, UUID requesterId, UUID orgId, boolean requesterIsAdmin);
}

