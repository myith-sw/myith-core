package com.myith.core.application.upload;

import com.myith.core.application.port.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadService {

    private final FileStoragePort fileStoragePort;
    private final long maxSizeBytes;
    private final List<String> allowedExtensions;

    public UploadService(FileStoragePort fileStoragePort,
                         @Value("${policy.upload.max-size-bytes}") long maxSizeBytes,
                         @Value("${policy.upload.allowed-extensions}") List<String> allowedExtensions) {
        this.fileStoragePort = fileStoragePort;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = allowedExtensions;
    }

    public FileStoragePort.PresignResult presign(String fileName, String contentType) {
        validateExtension(fileName);
        return fileStoragePort.generatePresignedUploadUrl(fileName, contentType);
    }

    private void validateExtension(String fileName) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!allowedExtensions.contains(ext)) {
            throw new InvalidFileException("허용되지 않는 확장자입니다: " + ext + ". 허용: " + allowedExtensions);
        }
    }

    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) { super(message); }
    }
}
