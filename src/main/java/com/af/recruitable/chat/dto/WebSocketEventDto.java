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
@Schema(description = "WebSocket event broadcast payload")
public class WebSocketEventDto {

    @Schema(description = "Event type", example = "ROOM_CREATED")
    private String event;

    @Schema(description = "User involved in the event")
    private UUID userId;

    @Schema(description = "Room involved in the event", nullable = true)
    private UUID roomId;

    @Schema(description = "Message involved in the event", nullable = true)
    private UUID messageId;

    @Schema(description = "Unix timestamp in milliseconds")
    private long timestamp;
}

