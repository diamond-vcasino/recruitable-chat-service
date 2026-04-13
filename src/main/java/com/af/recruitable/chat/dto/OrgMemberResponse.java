package com.af.recruitable.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Simplified org member info for chat UI (search, DM, group add).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Organization member summary for chat user selection")
public class OrgMemberResponse {

    @Schema(description = "User ID (use for API calls)", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID userId;

    @Schema(description = "Full display name", example = "Jane Doe")
    private String fullName;

    @Schema(description = "Email address", example = "jane@example.com")
    private String email;

    @Schema(description = "Avatar URL", example = "https://cdn.example.com/avatars/jane.png", nullable = true)
    private String avatarUrl;

    @Schema(description = "User's role in the organization", example = "RECRUITER")
    private String role;

    @Schema(description = "Department", example = "Engineering", nullable = true)
    private String department;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @JsonProperty("is_current_user")
    @Schema(description = "Whether this is the current user (true = exclude from selectable list)", example = "false")
    private boolean isCurrentUser;
}

