package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.RoomType;
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
public class CreateRoomRequest {
    @NotNull(message = "Room type is required")
    private RoomType type;

    /**
     * Required for GROUP and PUBLIC rooms. Ignored for PRIVATE rooms (auto-generated).
     */
    private String name;

    private String description;

    /**
     * Optional members to seed on creation.
     * PRIVATE rooms require exactly 1 other user.
     * GROUP rooms may include zero or more users.
     * PUBLIC rooms ignore this list because all org users can access them.
     */
    private List<UUID> memberUserIds;
}

