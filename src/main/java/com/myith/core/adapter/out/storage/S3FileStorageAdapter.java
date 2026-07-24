package com.myith.core.adapter.out.storage;

import com.myith.core.application.port.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

@Component
public class S3FileStorageAdapter implements FileStoragePort {

    private final S3Presigner presigner;
    private final String bucket;
    private final int expirySeconds;

    public S3FileStorageAdapter(S3Presigner presigner,
                                @Value("${aws.s3.bucket}") String bucket,
                                @Value("${aws.s3.presign-expiry}") int expirySeconds) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.expirySeconds = expirySeconds;
    }

    @Override
    public PresignResult generatePresignedUploadUrl(String fileName, String contentType) {
        String fileKey = "uploads/" + UUID.randomUUID() + "/" + fileName;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(objectRequest)
                .build();

        String url = presigner.presignPutObject(presignRequest).url().toString();
        return new PresignResult(url, fileKey, expirySeconds);
    }
}
