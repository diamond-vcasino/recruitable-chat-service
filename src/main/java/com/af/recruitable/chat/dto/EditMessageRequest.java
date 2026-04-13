package com.af.recruitable.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to edit an existing message")
public class EditMessageRequest {

    @NotBlank(message = "body is required")
    @Schema(description = "New message body text", example = "Updated message content", requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;
}

