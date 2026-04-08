package com.af.recruitable.chat.repository;
import com.af.recruitable.chat.entity.MessageReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageReceiptRepository extends MongoRepository<MessageReceipt, String> {
}
