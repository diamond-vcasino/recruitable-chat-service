package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MessageType;
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
@Schema(description = "Chat message")
public class ChatMessageResponse {

    @Schema(description = "Message ID")
    private UUID id;

    @Schema(description = "Room this message belongs to")
    private UUID roomId;

    @Schema(description = "User ID of the sender")
    private UUID senderId;

    @Schema(description = "Display name of the sender (email fallback)", example = "Jane Doe")
    private String senderName;

    @Schema(description = "Message type: TEXT, FILE, or SYSTEM")
    private MessageType type;

    @Schema(description = "Message body text (null for deleted messages)", example = "Hello team!")
    private String body;

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

    @Schema(description = "Whether this message has been edited")
    private boolean edited;

    @Schema(description = "Whether this message has been soft-deleted")
    private boolean deleted;

    @Schema(description = "When the message was created")
    private Instant createdAt;

    @Schema(description = "When the message was last edited", nullable = true)
    private Instant editedAt;
}

