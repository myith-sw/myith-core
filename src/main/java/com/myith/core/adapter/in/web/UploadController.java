package com.myith.core.adapter.in.web;

import com.myith.core.application.port.FileStoragePort;
import com.myith.core.application.upload.UploadService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Upload", description = "파일 업로드")
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(
            summary = "S3 Presigned URL 발급",
            description = """
                    프론트가 uploadUrl로 S3에 직접 PUT 업로드하고, fileKey만 로드맵 생성에 넘긴다.
                    서버는 파일 본문을 받지 않는다.
                    허용 타입: application/pdf, image/png, image/jpeg. 상한 10MB."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UNSUPPORTED_FILE_TYPE / FILE_TOO_LARGE",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "UNSUPPORTED_FILE_TYPE",
                                        "message": "허용되지 않는 파일 형식입니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignResponse>> presign(
            @Valid @RequestBody PresignRequest request) {
        FileStoragePort.PresignResult result = uploadService.presign(request.fileName(), request.contentType());
        return ResponseEntity.ok(ApiResponse.of(new PresignResponse(
                result.uploadUrl(), result.fileKey(), result.expiresIn())));
    }

    @Schema(name = "PresignRequest")
    record PresignRequest(
            @Schema(description = "업로드할 파일 이름", example = "portfolio.pdf")
            @NotBlank String fileName,
            @Schema(description = "MIME 타입. application/pdf, image/png, image/jpeg만 허용", example = "application/pdf")
            @NotBlank String contentType
    ) {}

    @Schema(name = "PresignResponse")
    record PresignResponse(
            @Schema(description = "프론트가 PUT 요청을 보낼 S3 Presigned URL",
                    example = "https://myith-uploads.s3.ap-northeast-2.amazonaws.com/...")
            String uploadUrl,
            @Schema(description = "로드맵 생성 시 전달할 파일 키", example = "portfolio/usr_01J3ABC/9f2c1d.pdf")
            String fileKey,
            @Schema(description = "URL 만료 시간(초)", example = "900")
            int expiresIn
    ) {}
}
