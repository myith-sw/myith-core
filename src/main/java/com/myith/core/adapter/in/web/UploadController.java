package com.myith.core.adapter.in.web;

import com.myith.core.application.port.FileStoragePort;
import com.myith.core.application.upload.UploadService;
import com.myith.core.common.ApiResponse;
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
                    로드맵 생성 화면에서 포트폴리오·이미지 파일을 첨부할 때 호출합니다.

                    파일 업로드 플로우:
                    1. 이 API를 호출해 uploadUrl과 fileKey를 받습니다.
                    2. uploadUrl로 S3에 직접 PUT 요청을 보내 파일을 업로드합니다.
                       (Content-Type 헤더를 요청한 contentType 값과 동일하게 설정해야 합니다.)
                    3. fileKey를 POST /api/roadmaps의 experiences[].fileKey 필드에 담아 로드맵 생성을 호출합니다.
                    서버는 파일 본문을 직접 받지 않습니다.

                    허용 타입: application/pdf, image/png, image/jpeg. 파일 크기 상한 10MB.
                    Presigned URL의 유효 시간은 expiresIn(초)입니다. 만료 전에 PUT 업로드를 완료하세요."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "uploadUrl": "https://myith-uploads.s3.ap-northeast-2.amazonaws.com/...",
                                        "fileKey": "portfolio/usr_01J3ABC/9f2c1d.pdf",
                                        "expiresIn": 900
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UNSUPPORTED_FILE_TYPE / FILE_TOO_LARGE",
                    content = @Content(mediaType = "application/json",
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
            @Schema(description = "업로드할 파일 이름입니다. 확장자를 포함해야 합니다.", example = "portfolio.pdf")
            @NotBlank String fileName,
            @Schema(description = "MIME 타입입니다. application/pdf, image/png, image/jpeg만 허용됩니다.", example = "application/pdf")
            @NotBlank String contentType
    ) {}

    @Schema(name = "PresignResponse")
    record PresignResponse(
            @Schema(description = "S3에 파일을 PUT 업로드할 Presigned URL입니다. Authorization 헤더 없이 직접 요청하세요.",
                    example = "https://myith-uploads.s3.ap-northeast-2.amazonaws.com/...")
            String uploadUrl,
            @Schema(description = "로드맵 생성 시 experiences[].fileKey 필드에 전달할 파일 키입니다. 안전하게 보관하세요.", example = "portfolio/usr_01J3ABC/9f2c1d.pdf")
            String fileKey,
            @Schema(description = "uploadUrl의 만료 시간(초)입니다. 이 시간 안에 S3 PUT 업로드를 완료해야 합니다.", example = "900")
            int expiresIn
    ) {}
}
