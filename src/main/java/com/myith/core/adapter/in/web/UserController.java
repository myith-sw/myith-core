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

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 프로필 정보를 반환한다.")
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

    @Operation(summary = "내 정보 수정", description = "닉네임 또는 프로필 이미지를 변경한다. 모든 필드는 optional이다.")
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
            @RequestBody UpdateMeRequest request) {
        User user = userService.updateMe(userId, request.nickname(), request.profileImageUrl());
        return ResponseEntity.ok(ApiResponse.of(UserResponse.from(user)));
    }

    @Operation(summary = "회원 탈퇴",
            description = "soft delete + PII 익명화. email→deleted_{id}@myith.local, google_id→null, nickname→탈퇴한 사용자, profile_image_url→null.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        userService.deleteMe(userId);
        return ResponseEntity.noContent().build();
    }

    @Schema(name = "UpdateMeRequest")
    record UpdateMeRequest(
            @Schema(description = "변경할 닉네임. trim 후 1~20자", example = "새이름")
            @Size(min = 1, max = 20)
            String nickname,
            @Schema(description = "변경할 프로필 이미지 URL", example = "https://example.com/avatar.png")
            String profileImageUrl
    ) {}

    @Schema(name = "UserResponse")
    record UserResponse(
            @Schema(description = "사용자 ID", example = "usr_01J3ABC")
            String id,
            @Schema(description = "이메일", example = "sungyoon@example.com")
            String email,
            @Schema(description = "계정 닉네임. 캐릭터 닉네임과 별개", example = "이성윤")
            String nickname,
            @Schema(description = "프로필 이미지 URL", example = "null")
            String profileImageUrl,
            @Schema(description = "가입일", example = "2026-07-01T00:00:00Z")
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
