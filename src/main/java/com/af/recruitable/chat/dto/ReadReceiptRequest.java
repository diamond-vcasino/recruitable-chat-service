package com.af.recruitable.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptRequest {
    @NotNull(message = "roomId is required")
    private UUID roomId;

    /**
     * Optional: specific message ID. If null, marks all messages in the room as read.
     */
    private UUID messageId;
}

