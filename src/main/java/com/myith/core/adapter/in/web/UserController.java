package com.myith.core.adapter.in.web;

import com.myith.core.application.auth.UserService;
import com.myith.core.common.ApiResponse;
import com.myith.core.domain.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증·사용자")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "내 정보 조회", description = """
            현재 로그인한 사용자의 프로필 정보를 반환합니다.
            설정 화면 진입 시 호출하여 닉네임·프로필 이미지를 초기값으로 채웁니다.
            profileImageUrl이 null이면 기본 아바타 이미지를 표시합니다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "id": "usr_01J3ABC",
                                "email": "sungyoon@example.com",
                                "nickname": "이성윤",
                                "profileImageUrl": null,
                                "createdAt": "2026-07-01T00:00:00Z"
                              }
                            }""")))
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Long userId) {
        User user = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.of(UserResponse.from(user)));
    }

    @Operation(summary = "내 정보 수정", description = """
            닉네임 또는 프로필 이미지 URL을 변경합니다. 모든 필드는 선택 사항입니다.
            변경하지 않을 필드는 요청 바디에서 생략하거나 null로 보내면 기존 값이 유지됩니다.
            profileImageUrl은 /api/uploads/presign으로 발급받은 fileKey를 S3에 업로드한 후 접근 URL을 전달합니다.
            응답으로 수정된 전체 프로필이 반환되므로 별도 조회 없이 UI를 갱신할 수 있습니다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "id": "usr_01J3ABC",
                                        "email": "sungyoon@example.com",
                                        "nickname": "새이름",
                                        "profileImageUrl": null,
                                        "createdAt": "2026-07-01T00:00:00Z"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VALIDATION_ERROR — nickname trim 후 1~20자",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "입력값이 올바르지 않습니다.",
                                        "fieldErrors": { "nickname": "닉네임은 1~20자여야 합니다." },
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateMeRequest request) {
        User user = userService.updateMe(userId, request.nickname(), request.profileImageUrl());
        return ResponseEntity.ok(ApiResponse.of(UserResponse.from(user)));
    }

    @Operation(summary = "회원 탈퇴",
            description = """
                    soft delete + PII 익명화를 수행합니다.
                    email → deleted_{id}@myith.local, google_id → null, nickname → 탈퇴한 사용자, profile_image_url → null 로 처리됩니다.
                    성공 시 204를 반환하며 바디는 없습니다. 이후 해당 계정으로 재로그인하면 신규 가입으로 처리됩니다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 — 유효하지 않은 토큰")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        userService.deleteMe(userId);
        return ResponseEntity.noContent().build();
    }

    @Schema(name = "UpdateMeRequest")
    record UpdateMeRequest(
            @Schema(description = "변경할 계정 닉네임입니다. trim 후 1~20자여야 합니다. 생략하면 기존 값이 유지됩니다.", example = "새이름")
            @Size(min = 1, max = 20)
            String nickname,
            @Schema(description = "변경할 프로필 이미지 URL입니다. S3 업로드 후 공개 접근 가능한 URL을 전달합니다. null을 명시하면 기본 아바타로 초기화됩니다.", example = "https://example.com/avatar.png")
            String profileImageUrl
    ) {}

    @Schema(name = "UserResponse")
    record UserResponse(
            @Schema(description = "사용자 ID입니다. 'usr_' 접두사를 포함합니다.", example = "usr_01J3ABC")
            String id,
            @Schema(description = "이메일 주소입니다.", example = "sungyoon@example.com")
            String email,
            @Schema(description = "계정 닉네임입니다. 캐릭터 닉네임(character.nickname)과 별개입니다. 구글 가입 시 구글 이름으로 초기화됩니다.", example = "이성윤")
            String nickname,
            @Schema(description = "프로필 이미지 URL입니다. null이면 기본 아바타를 표시합니다.", nullable = true, example = "https://example.com/avatar.png")
            String profileImageUrl,
            @Schema(description = "가입 일시(ISO 8601 UTC)입니다.", example = "2026-07-01T00:00:00Z")
            String createdAt
    ) {
        static UserResponse from(User user) {
            return new UserResponse(
                    "usr_" + user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
            );
        }
    }
}
