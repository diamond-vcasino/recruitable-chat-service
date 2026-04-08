package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.RoomType;
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
public class ChatRoomResponse {
    private UUID id;
    private UUID organizationId;
    private RoomType type;
    private String name;
    private String description;
    private String avatarUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RoomMemberResponse> members;
    private long unreadCount;
    private ChatMessageResponse lastMessage;
}

