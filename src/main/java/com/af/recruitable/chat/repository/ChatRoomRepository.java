package com.af.recruitable.chat.repository;
import com.af.recruitable.chat.entity.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    /**
     * Find rooms the user is a member of within an organization.
     */
    @Query("{'organizationId': ?1, 'members.userId': ?0}")
    List<ChatRoom> findRoomsByUserAndOrg(String userId, String orgId);

    /**
     * Find rooms the user can access: rooms they belong to OR PUBLIC rooms in the org.
     */
    @Query(value = "{'organizationId': ?1, '$or': [{'type': 'PUBLIC'}, {'members.userId': ?0}]}",
           sort = "{'updatedAt': -1}")
    List<ChatRoom> findAccessibleRoomsByUserAndOrg(String userId, String orgId);

    /**
     * Find a PRIVATE room between two users in the same org.
     */
    @Query("{'organizationId': ?0, 'type': 'PRIVATE', 'members.userId': {'$all': [?1, ?2]}, '$expr': {'$eq': [{'$size': '$members'}, 2]}}")
    Optional<ChatRoom> findPrivateRoom(String orgId, String userA, String userB);
}
