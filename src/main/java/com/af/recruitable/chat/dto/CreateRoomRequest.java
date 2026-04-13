package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a chat room")
public class CreateRoomRequest {

    @NotNull(message = "Room type is required")
    @Schema(description = "Room type: PRIVATE, GROUP, or PUBLIC", example = "GROUP", requiredMode = Schema.RequiredMode.REQUIRED)
    private RoomType type;

    @Schema(description = "Room name (required for GROUP/PUBLIC, ignored for PRIVATE)", example = "Engineering Team")
    private String name;

    @Schema(description = "Room description", example = "Channel for engineering discussions", nullable = true)
    private String description;

    @Schema(description = "Member user IDs to add on creation. PRIVATE requires exactly 1 ID. GROUP accepts 0+. PUBLIC ignores this.",
            example = "[\"7c9e6679-7425-40de-944b-e07fc1f90ae7\", \"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> memberUserIds;
}

