package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MessageType;
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
public class ChatMessageResponse {
    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private String senderName;
    private MessageType type;
    private String body;
    private UUID parentMessageId;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileContentType;
    private boolean edited;
    private boolean deleted;
    private Instant createdAt;
    private Instant editedAt;
}

