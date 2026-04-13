package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Room member with profile details")
public class RoomMemberResponse {

    @Schema(description = "User ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID userId;

    @Schema(description = "Display name", example = "Jane Doe")
    private String fullName;

    @Schema(description = "Email address", example = "jane@example.com")
    private String email;

    @Schema(description = "Avatar URL", nullable = true)
    private String avatarUrl;

    @Schema(description = "Role in the room", example = "MEMBER")
    private MemberRole role;

    @Schema(description = "When the user joined the room")
    private Instant joinedAt;

    @Schema(description = "Last time the user marked this room as read", nullable = true)
    private Instant lastReadAt;
}

