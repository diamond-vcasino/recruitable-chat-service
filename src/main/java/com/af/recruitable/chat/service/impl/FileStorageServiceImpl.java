package com.af.recruitable.chat.service.impl;

import com.af.recruitable.chat.config.S3Config;
import com.af.recruitable.chat.dto.FileUploadResponse;
import com.af.recruitable.chat.exception.ChatException;
import com.af.recruitable.chat.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Config s3Config;

    @Override
    public FileUploadResponse uploadChatFile(MultipartFile file, UUID orgId) {
        if (file == null || file.isEmpty()) {
            throw ChatException.badRequest("File is empty");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String key = "chat/" + orgId + "/" + UUID.randomUUID() + "/" + originalFilename;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Generate presigned download URL
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Config.getDownloadUrlExpiryMinutes()))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(s3Config.getBucketName())
                            .key(key)
                            .build())
                    .build();

            String downloadUrl = s3Presigner.presignGetObject(presignRequest).url().toString();

            log.info("File uploaded to S3: key={}, size={}", key, file.getSize());

            return FileUploadResponse.builder()
                    .fileUrl(downloadUrl)
                    .fileName(originalFilename)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw ChatException.badRequest("Failed to upload file: " + e.getMessage());
        }
    }
}

