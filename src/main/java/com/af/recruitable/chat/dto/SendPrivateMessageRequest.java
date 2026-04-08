package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendPrivateMessageRequest {
    // Set from URL path variable in controller (/users/{targetUserId}/messages)
    private UUID targetUserId;

    private String body;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private UUID parentMessageId;

    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileContentType;

    private String senderName;
}

