package com.myith.core.adapter.in.web;

import com.myith.core.application.auth.AuthService;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증·사용자")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "구글 로그인",
            description = """
                    Google ID Token을 서버가 로컬 검증합니다(캐싱된 공개키로 JWT 서명 검증).
                    최초 로그인 시 자동 가입하고 users.nickname을 구글 이름으로 초기화합니다.
                    응답의 isNewUser가 true이면 온보딩 화면(알 선택 화면 1-1)으로 이동하고,
                    false이면 홈 화면(화면 1-2)으로 이동합니다.
                    발급된 accessToken은 이후 모든 API 요청의 Authorization: Bearer 헤더에 담아 보냅니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.abc",
                                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.xyz",
                                        "isNewUser": true
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_ID_TOKEN — Google ID Token 검증 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "INVALID_ID_TOKEN",
                                        "message": "Google ID Token 검증에 실패했습니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @SecurityRequirements
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthService.AuthResult result = authService.loginWithGoogle(request.idToken());
        return ResponseEntity.ok(ApiResponse.of(
                new GoogleLoginResponse(result.accessToken(), result.refreshToken(), result.isNewUser())));
    }

    @Operation(
            summary = "토큰 갱신",
            description = """
                    accessToken 만료 시(401 + code: TOKEN_EXPIRED) 이 엔드포인트를 1회 호출하고 원 요청을 재시도합니다.
                    재시도 후에도 401이 반환되면 로그인 화면으로 이동합니다.
                    refreshToken은 요청 바디로 전달합니다(httpOnly 쿠키 방식이 아닙니다).
                    성공 응답에는 accessToken만 포함되며, refreshToken은 갱신되지 않습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "갱신 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.new"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN — refreshToken 무효",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "INVALID_REFRESH_TOKEN",
                                        "message": "유효하지 않은 토큰입니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        AuthService.TokenResult result = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(new RefreshResponse(result.accessToken())));
    }

    @Schema(name = "GoogleLoginRequest")
    record GoogleLoginRequest(
            @Schema(description = "Google에서 받은 ID Token", example = "eyJhbGciOiJSUzI1NiIs...")
            @NotBlank String idToken
    ) {}

    @Schema(name = "GoogleLoginResponse")
    record GoogleLoginResponse(
            @Schema(description = "모든 API 요청의 Authorization: Bearer {accessToken} 헤더에 담아 보냅니다. 만료 시 /api/auth/refresh로 갱신합니다.",
                    example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.abc")
            String accessToken,
            @Schema(description = "accessToken 만료 시 /api/auth/refresh 요청 바디에 담아 보냅니다. 안전한 저장소(Keychain, SecureStorage 등)에 보관합니다.",
                    example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.xyz")
            String refreshToken,
            @Schema(description = "true이면 신규 가입이므로 온보딩(알 선택 화면 1-1)으로 이동합니다. false이면 홈(화면 1-2)으로 이동합니다.", example = "true")
            boolean isNewUser
    ) {}

    @Schema(name = "RefreshRequest")
    record RefreshRequest(
            @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.xyz")
            @NotBlank String refreshToken
    ) {}

    @Schema(name = "RefreshResponse")
    record RefreshResponse(
            @Schema(description = "새로 발급된 액세스 토큰입니다. 기존 토큰을 이 값으로 교체한 뒤 원 요청을 재시도합니다.", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMDFKM0FCQyJ9.new")
            String accessToken
    ) {}
}
