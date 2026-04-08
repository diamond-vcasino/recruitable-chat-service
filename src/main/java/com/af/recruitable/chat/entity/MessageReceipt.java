package com.af.recruitable.chat.entity;
import com.af.recruitable.chat.constant.DeliveryStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;
@Document(collection = "message_receipts")
@CompoundIndex(name = "uq_msg_user", def = "{'messageId': 1, 'userId': 1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageReceipt {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private String messageId;
    private String userId;
    private DeliveryStatus status;
    @CreatedDate
    private Instant createdAt;
}
