package com.af.recruitable.chat.service;

import com.af.recruitable.chat.dto.OrgMemberResponse;
import com.af.recruitable.chat.dto.PageResponse;
import com.af.recruitable.chat.exception.ChatException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service to fetch organization members from recruitable-api-backend.
 * Calls the /api/v1/profiles REST endpoint with the current user's JWT token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgMemberService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(apiBackendUrl)
                    .path("/api/v1/profiles")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .queryParam("sortBy", "fullName")
                    .queryParam("sortDirection", "ASC");

            String url = uriBuilder.toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            var response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ChatException("Failed to fetch org members from api-backend", HttpStatus.BAD_GATEWAY);
            }

            if (response.getBody() == null || response.getBody().isBlank()) {
                return emptyPage(page, size);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            // api-backend wraps payload under `content`; keep `data` fallback for compatibility.
            JsonNode pageNode = root.hasNonNull("content")
                    ? root.get("content")
                    : (root.hasNonNull("data") ? root.get("data") : root);

            BackendPageResponse<ProfileDto> profilePage = objectMapper.convertValue(
                    pageNode,
                    new TypeReference<BackendPageResponse<ProfileDto>>() {}
            );

            List<ProfileDto> profiles = profilePage.getContent() == null
                    ? List.of()
                    : profilePage.getContent();

            String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

            List<OrgMemberResponse> members = profiles.stream()
                    .filter(Objects::nonNull)
                    .map(profile -> toOrgMemberResponse(profile, currentUserId))
                    .filter(member -> normalizedSearch.isBlank()
                            || (member.getFullName() != null && member.getFullName().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                            || (member.getEmail() != null && member.getEmail().toLowerCase(Locale.ROOT).contains(normalizedSearch)))
                    .toList();

            return PageResponse.<OrgMemberResponse>builder()
                    .content(members)
                    .page(Optional.ofNullable(profilePage.getPage()).map(PageMetadata::getPageNumber).orElse(page))
                    .size(Optional.ofNullable(profilePage.getPage()).map(PageMetadata::getPageSize).orElse(size))
                    .totalElements(Optional.ofNullable(profilePage.getPage()).map(PageMetadata::getTotalElements).orElse((long) members.size()))
                    .totalPages(Optional.ofNullable(profilePage.getPage()).map(PageMetadata::getTotalPages).orElse(0))
                    .first(Optional.ofNullable(profilePage.getPage()).map(m -> m.getPageNumber() <= 0).orElse(page == 0))
                    .last(Optional.ofNullable(profilePage.getPage()).map(m -> m.getPageNumber() >= Math.max(m.getTotalPages() - 1, 0)).orElse(true))
                    .build();

        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching org members from api-backend", e);
            throw new ChatException("Unable to fetch organization members from user service", HttpStatus.BAD_GATEWAY);
        }
    }

    public List<OrgMemberResponse> listOrgMembersByIds(Set<UUID> userIds, String jwtToken, UUID currentUserId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> unresolved = new HashSet<>(userIds);
        List<OrgMemberResponse> onlineMembers = new ArrayList<>();

        int page = 0;
        int size = Math.min(Math.max(userIds.size() * 2, 100), 500);

        while (!unresolved.isEmpty()) {
            PageResponse<OrgMemberResponse> memberPage = listOrgMembers(null, page, size, jwtToken, currentUserId);
            if (memberPage.getContent() == null || memberPage.getContent().isEmpty()) {
                break;
            }

            for (OrgMemberResponse member : memberPage.getContent()) {
                if (member.getUserId() != null && unresolved.contains(member.getUserId())) {
                    onlineMembers.add(member);
                    unresolved.remove(member.getUserId());
                }
            }

            if (memberPage.isLast()) {
                break;
            }
            page++;
        }

        return onlineMembers.stream()
                .sorted(Comparator.comparing(
                        m -> m.getFullName() != null ? m.getFullName() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private PageResponse<OrgMemberResponse> emptyPage(int page, int size) {
        return PageResponse.<OrgMemberResponse>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .first(page == 0)
                .last(true)
                .build();
    }

    private OrgMemberResponse toOrgMemberResponse(ProfileDto profile, UUID currentUserId) {
        UUID memberId = profile.getUserId();
        String fullName = profile.resolveDisplayName();
        String email = profile.getEmail() == null ? "" : profile.getEmail();

        return OrgMemberResponse.builder()
                .userId(memberId)
                .fullName(fullName)
                .email(email)
                .avatarUrl(profile.resolveAvatarUrl())
                .role(profile.getRole())
                .department(profile.getDepartment())
                .status(profile.getStatus())
                .isCurrentUser(memberId != null && memberId.equals(currentUserId))
                .build();
    }

    // ── Internal DTOs (mirrors api-backend, simplified) ───────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BackendPageResponse<T> {
        private List<T> content;
        private PageMetadata page;

        public List<T> getContent() { return content; }
        public void setContent(List<T> content) { this.content = content; }
        public PageMetadata getPage() { return page; }
        public void setPage(PageMetadata page) { this.page = page; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PageMetadata {
        @JsonProperty("page_number")
        private int pageNumber;
        @JsonProperty("page_size")
        private int pageSize;
        @JsonProperty("total_elements")
        private long totalElements;
        @JsonProperty("total_pages")
        private int totalPages;

        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProfileDto {
        private java.util.UUID userId;
        private String fullName;
        private String firstName;
        private String lastName;
        private String email;
        private String avatarUrl;
        private String profileImageUrl;
        private String role;
        private String department;
        private String status;

        public java.util.UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getProfileImageUrl() { return profileImageUrl; }
        public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String resolveDisplayName() {
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
            List<String> parts = new ArrayList<>();
            if (firstName != null && !firstName.isBlank()) {
                parts.add(firstName.trim());
            }
            if (lastName != null && !lastName.isBlank()) {
                parts.add(lastName.trim());
            }
            if (!parts.isEmpty()) {
                return String.join(" ", parts);
            }
            return email != null && !email.isBlank() ? email : "Unknown user";
        }

        public String resolveAvatarUrl() {
            return avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl : profileImageUrl;
        }
    }
}

