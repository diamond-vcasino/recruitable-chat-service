package com.af.recruitable.chat.entity;

import com.af.recruitable.chat.constant.MessageType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "chat_messages")
@CompoundIndex(name = "idx_room_created", def = "{'roomId': 1, 'createdAt': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String roomId;

    /** Denormalized from ChatRoom for access-control checks without extra lookup */
    private String organizationId;

    private String senderId;

    private MessageType type;

    private String body;

    private String parentMessageId;

    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileContentType;

    @Builder.Default
    private boolean edited = false;

    @Builder.Default
    private boolean deleted = false;

    /**
     * Explicitly defaulted to Instant.now() so the timestamp is ALWAYS present
     * even if Spring Data MongoDB auditing is not active in the current environment.
     * @CreatedDate will honour the pre-set value and will not override it.
     */
    @Builder.Default
    @CreatedDate
    private Instant createdAt = Instant.now();

    private Instant editedAt;
}
