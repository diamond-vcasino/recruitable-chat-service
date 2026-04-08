package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    @NotNull(message = "roomId is required")
    private UUID roomId;

    private String body;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private UUID parentMessageId;

    // For FILE type messages
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileContentType;

    // Sender display name (passed from frontend, not from DB join)
    private String senderName;
}

