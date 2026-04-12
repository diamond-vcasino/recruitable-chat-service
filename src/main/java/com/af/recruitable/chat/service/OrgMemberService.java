package com.af.recruitable.chat.service;

import com.af.recruitable.chat.dto.OrgMemberResponse;
import com.af.recruitable.chat.dto.PageResponse;
import com.af.recruitable.chat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to fetch organization members from recruitable-api-backend.
 * Calls the /api/v1/profiles REST endpoint with the current user's JWT token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgMemberService {

    private final RestTemplate restTemplate;

    @Value("${app.api-backend.base-url:http://localhost:8080}")
    private String apiBackendUrl;

    /**
     * List all organization members with optional search and pagination.
     *
     * @param search        Search by full name or email (optional)
     * @param page          Zero-based page number
     * @param size          Page size
     * @param jwtToken      Current user's JWT token
     * @param currentUserId Exclude this user from results (marked as isCurrentUser)
     * @return List of org members with pagination info
     */
    public PageResponse<OrgMemberResponse> listOrgMembers(String search, int page, int size, String jwtToken, UUID currentUserId) {
        try {
            // Build query URL
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(apiBackendUrl)
                    .path("/api/v1/profiles")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sortBy", "fullName")
                    .queryParam("sortDirection", "ASC");

            // Search is done client-side post-fetch; API backend would need search param
            // For now, we fetch all and filter, or you can enhance api-backend with search

            String url = uriBuilder.toUriString();

            // Prepare headers with JWT
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Call api-backend
            var response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<PageResponse<ProfileDto>>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getData() != null) {
                PageResponse<ProfileDto> profilePage = response.getBody().getData();

                // Transform to OrgMemberResponse
                List<OrgMemberResponse> members = profilePage.getContent().stream()
                        .map(profile -> OrgMemberResponse.builder()
                                .userId(profile.getUserId())
                                .fullName(profile.getFullName())
                                .email(profile.getEmail())
                                .avatarUrl(profile.getAvatarUrl())
                                .role(profile.getRole())
                                .department(profile.getDepartment())
                                .status(profile.getStatus())
                                .isCurrentUser(profile.getUserId().equals(currentUserId))
                                .build()
                        )
                        // Client-side search filter if provided
                        .filter(member -> search == null || search.isBlank() ||
                                member.getFullName().toLowerCase().contains(search.toLowerCase()) ||
                                member.getEmail().toLowerCase().contains(search.toLowerCase()))
                        .collect(Collectors.toList());

                return PageResponse.<OrgMemberResponse>builder()
                        .content(members)
                        .page(profilePage.getPage())
                        .size(profilePage.getSize())
                        .totalElements(profilePage.getTotalElements())
                        .totalPages(profilePage.getTotalPages())
                        .last(profilePage.isLast())
                        .build();
            }

            log.warn("Failed to fetch org members from api-backend: {}", response.getStatusCode());
            return PageResponse.<OrgMemberResponse>builder()
                    .content(List.of())
                    .page(page)
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

        } catch (Exception e) {
            log.error("Error fetching org members from api-backend", e);
            return PageResponse.<OrgMemberResponse>builder()
                    .content(List.of())
                    .page(page)
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }
    }

    // ── Internal DTOs (mirrors api-backend, simplified) ───────────────────

    private static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public T getData() {
            return data;
        }
    }

    private static class ProfileDto {
        private java.util.UUID userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private String role;
        private String department;
        private String status;

        public java.util.UUID getUserId() { return userId; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getRole() { return role; }
        public String getDepartment() { return department; }
        public String getStatus() { return status; }
    }

    public static class PageResponseImpl {
        private List<?> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;

        public List<?> getContent() { return content; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isLast() { return last; }
    }
}

