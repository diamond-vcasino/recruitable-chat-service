package com.af.recruitable.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEventDto {
    private String event;
    private UUID userId;
    private UUID roomId;
    private UUID messageId;
    private long timestamp;
}

