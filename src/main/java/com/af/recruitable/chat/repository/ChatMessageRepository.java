package com.af.recruitable.chat.repository;

import com.af.recruitable.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    Page<ChatMessage> findByRoomIdAndDeletedFalseOrderByCreatedAtDesc(String roomId, Pageable pageable);

    long countByRoomIdAndDeletedFalseAndSenderIdNot(String roomId, String senderId);

    long countByRoomIdAndDeletedFalseAndSenderIdNotAndCreatedAtAfter(
            String roomId, String senderId, Instant createdAt);
}
