package com.af.recruitable.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "File upload result with download URL and metadata")
public class FileUploadResponse {

    @Schema(description = "Presigned download URL for the file")
    private String fileUrl;

    @Schema(description = "Original file name", example = "report.pdf")
    private String fileName;

    @Schema(description = "File size in bytes", example = "102400")
    private long fileSize;

    @Schema(description = "File MIME type", example = "application/pdf")
    private String contentType;
}

