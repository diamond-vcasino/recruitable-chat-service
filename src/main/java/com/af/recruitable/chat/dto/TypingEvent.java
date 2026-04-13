package com.af.recruitable.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Typing indicator event (sent via WebSocket /app/chat.typing)")
public class TypingEvent {

    @Schema(description = "Room ID where user is typing", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID roomId;

    @Schema(description = "User ID — auto-set by server from JWT", hidden = true)
    private UUID userId;

    @Schema(description = "User display name — auto-set by server from JWT", hidden = true)
    private String userName;

    @Schema(description = "true = started typing, false = stopped typing", example = "true")
    private boolean typing;
}

