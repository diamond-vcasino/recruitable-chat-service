package com.af.recruitable.chat.security;

import com.af.recruitable.chat.constant.RoomType;
import com.af.recruitable.chat.entity.ChatRoom;
import com.af.recruitable.chat.entity.ChatRoomMember;
import com.af.recruitable.chat.exception.ChatException;
import com.af.recruitable.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests that org membership verification correctly prevents cross-org
 * access to chat rooms. This validates the security boundary that was
 * previously only enforced by trusting the JWT claim.
 */
@ExtendWith(MockitoExtension.class)
class OrgMembershipVerifierTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private OrgMembershipVerifier verifier;

    private static final UUID ORG_A = UUID.randomUUID();
    private static final UUID ORG_B = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_2 = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    private ChatRoom buildRoom(UUID orgId, RoomType type, UUID... memberIds) {
        List<ChatRoomMember> members = new ArrayList<>();
        for (UUID memberId : memberIds) {
            ChatRoomMember m = new ChatRoomMember();
            m.setUserId(memberId.toString());
            members.add(m);
        }
        return ChatRoom.builder()
                .id(ROOM_ID.toString())
                .organizationId(orgId.toString())
                .type(type)
                .members(members)
                .build();
    }

    @Nested
    @DisplayName("verifyRoomOrgMembership")
    class RoomOrgMembership {

        @Test
        @DisplayName("Same org passes")
        void sameOrgPasses() {
            ChatRoom room = buildRoom(ORG_A, RoomType.GROUP, USER_1);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            assertDoesNotThrow(() -> verifier.verifyRoomOrgMembership(ROOM_ID, ORG_A));
        }

        @Test
        @DisplayName("Different org is rejected")
        void differentOrgRejected() {
            ChatRoom room = buildRoom(ORG_A, RoomType.GROUP, USER_1);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            ChatException ex = assertThrows(ChatException.class,
                    () -> verifier.verifyRoomOrgMembership(ROOM_ID, ORG_B));
            assertEquals(403, ex.getStatus().value());
        }

        @Test
        @DisplayName("Non-existent room throws 404")
        void roomNotFound() {
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.empty());

            ChatException ex = assertThrows(ChatException.class,
                    () -> verifier.verifyRoomOrgMembership(ROOM_ID, ORG_A));
            assertEquals(404, ex.getStatus().value());
        }
    }

    @Nested
    @DisplayName("verifyRoomAccess (org + membership)")
    class RoomAccess {

        @Test
        @DisplayName("PUBLIC room: any org member can access")
        void publicRoomAnyOrgMember() {
            ChatRoom room = buildRoom(ORG_A, RoomType.PUBLIC);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            assertDoesNotThrow(() -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_A));
        }

        @Test
        @DisplayName("PUBLIC room: different org is rejected")
        void publicRoomDifferentOrg() {
            ChatRoom room = buildRoom(ORG_A, RoomType.PUBLIC);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            ChatException ex = assertThrows(ChatException.class,
                    () -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_B));
            assertEquals(403, ex.getStatus().value());
        }

        @Test
        @DisplayName("PRIVATE room: member can access")
        void privateRoomMemberAccess() {
            ChatRoom room = buildRoom(ORG_A, RoomType.PRIVATE, USER_1, USER_2);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            assertDoesNotThrow(() -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_A));
        }

        @Test
        @DisplayName("PRIVATE room: non-member is rejected")
        void privateRoomNonMemberRejected() {
            ChatRoom room = buildRoom(ORG_A, RoomType.PRIVATE, USER_2);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            ChatException ex = assertThrows(ChatException.class,
                    () -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_A));
            assertEquals(403, ex.getStatus().value());
        }

        @Test
        @DisplayName("GROUP room: member can access")
        void groupRoomMemberAccess() {
            ChatRoom room = buildRoom(ORG_A, RoomType.GROUP, USER_1, USER_2);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            assertDoesNotThrow(() -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_A));
        }

        @Test
        @DisplayName("GROUP room: non-member is rejected")
        void groupRoomNonMemberRejected() {
            ChatRoom room = buildRoom(ORG_A, RoomType.GROUP, USER_2);
            when(chatRoomRepository.findById(ROOM_ID.toString())).thenReturn(Optional.of(room));

            ChatException ex = assertThrows(ChatException.class,
                    () -> verifier.verifyRoomAccess(ROOM_ID, USER_1, ORG_A));
            assertEquals(403, ex.getStatus().value());
        }
    }
}

