package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MessageType;
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
@Schema(description = "Request to send a direct private message to another user")
public class SendPrivateMessageRequest {

    @Schema(description = "Target user ID — set automatically from URL path, do NOT send in body", hidden = true)
    private UUID targetUserId;

    @Schema(description = "Message text body", example = "Hey, can we talk?")
    private String body;

    @Builder.Default
    @Schema(description = "Message type", example = "TEXT", defaultValue = "TEXT")
    private MessageType type = MessageType.TEXT;

    @Schema(description = "Parent message ID for replies/threads", nullable = true)
    private UUID parentMessageId;

    @Schema(description = "File download URL (for FILE messages)", nullable = true)
    private String fileUrl;

    @Schema(description = "Original file name", nullable = true)
    private String fileName;

    @Schema(description = "File size in bytes", nullable = true)
    private Long fileSize;

    @Schema(description = "File MIME type", nullable = true)
    private String fileContentType;

    @Schema(description = "Sender display name — auto-resolved from JWT if omitted", hidden = true)
    private String senderName;
}

