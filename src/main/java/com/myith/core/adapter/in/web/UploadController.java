package com.myith.core.adapter.in.web;

import com.myith.core.application.port.FileStoragePort;
import com.myith.core.application.upload.UploadService;
import com.myith.core.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<FileStoragePort.PresignResult>> presign(
            @Valid @RequestBody PresignRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                uploadService.presign(request.fileName(), request.contentType())));
    }

    record PresignRequest(@NotBlank String fileName, @NotBlank String contentType) {}
}
