package com.myith.core.application.port;

public interface FileStoragePort {
    PresignResult generatePresignedUploadUrl(String fileName, String contentType);

    record PresignResult(String uploadUrl, String fileKey, int expiresIn) {}
}
