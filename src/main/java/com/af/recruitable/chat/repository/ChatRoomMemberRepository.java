package com.af.recruitable.chat.repository;

/**
 * ChatRoomMember is now embedded inside ChatRoom documents.
 * This interface is retained as an empty placeholder — all member
 * operations go through ChatRoomRepository / ChatRoom.getMembers().
 */
public interface ChatRoomMemberRepository {
    // No longer a separate collection — members are embedded in ChatRoom.
}
