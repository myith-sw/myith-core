package com.myith.core.adapter.in.web;

import com.myith.core.application.auth.AuthService;
import com.myith.core.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<GoogleLoginResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthService.AuthResult result = authService.loginWithGoogle(request.idToken());
        return ResponseEntity.ok(ApiResponse.success(
                new GoogleLoginResponse(result.accessToken(), result.refreshToken(), result.isNewUser())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        AuthService.TokenResult result = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(new RefreshResponse(result.accessToken())));
    }

    record GoogleLoginRequest(@NotBlank String idToken) {}
    record GoogleLoginResponse(String accessToken, String refreshToken, boolean isNewUser) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
    record RefreshResponse(String accessToken) {}
}
