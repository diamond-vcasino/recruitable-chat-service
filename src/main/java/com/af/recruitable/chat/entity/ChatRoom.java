package com.af.recruitable.chat.entity;
import com.af.recruitable.chat.constant.RoomType;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Document(collection = "chat_rooms")
@CompoundIndex(name = "idx_org_id", def = "{'organizationId': 1}")
@CompoundIndex(name = "idx_members_userId", def = "{'members.userId': 1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRoom {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String organizationId;
    private RoomType type;
    private String name;
    private String description;
    private String avatarUrl;
    @Builder.Default
    private List<ChatRoomMember> members = new ArrayList<>();
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
