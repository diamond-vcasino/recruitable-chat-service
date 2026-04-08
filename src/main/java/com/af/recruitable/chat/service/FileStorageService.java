package com.af.recruitable.chat.service;

import com.af.recruitable.chat.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileStorageService {

    /**
     * Upload a chat file to S3. Returns the file metadata + presigned download URL.
     */
    FileUploadResponse uploadChatFile(MultipartFile file, UUID orgId);
}

