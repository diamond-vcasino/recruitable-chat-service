package com.af.recruitable.chat.dto;

import com.af.recruitable.chat.constant.MemberRole;
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
public class RoomMemberResponse {
    private UUID userId;
    private MemberRole role;
    private Instant joinedAt;
    private Instant lastReadAt;
}

