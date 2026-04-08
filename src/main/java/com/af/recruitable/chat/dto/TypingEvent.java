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
public class TypingEvent {
    private UUID roomId;
    private UUID userId;
    private String userName;
    private boolean typing;
}

