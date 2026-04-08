package com.af.recruitable.chat.entity;
import com.af.recruitable.chat.constant.MemberRole;
import lombok.*;
import java.time.Instant;
/**
 * Embedded document within ChatRoom.members — no separate collection.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRoomMember {
    private String userId;
    private MemberRole role;
    private Instant lastReadAt;
    @Builder.Default
    private Instant joinedAt = Instant.now();
}
