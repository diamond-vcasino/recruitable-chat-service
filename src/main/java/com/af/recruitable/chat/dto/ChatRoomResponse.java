package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chat room with members, last message, and unread count")
public class ChatRoomResponse {

    @Schema(description = "Room ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Organization ID this room belongs to")
    private UUID organizationId;

    @Schema(description = "Room type: PRIVATE (1-on-1), GROUP, or PUBLIC (org-wide)")
    private RoomType type;

    @Schema(description = "Room name (auto-set to other user's name for PRIVATE rooms)", example = "Engineering Team")
    private String name;

    @Schema(description = "Room description", nullable = true)
    private String description;

    @Schema(description = "Room avatar URL", nullable = true)
    private String avatarUrl;

    @Schema(description = "When the room was created")
    private Instant createdAt;

    @Schema(description = "When the room was last updated (e.g. last message)")
    private Instant updatedAt;

    @Schema(description = "List of room members with profile details")
    private List<RoomMemberResponse> members;

    @Schema(description = "Number of unread messages for the current user", example = "3")
    private long unreadCount;

    @Schema(description = "Most recent message in the room", nullable = true)
    private ChatMessageResponse lastMessage;
}

