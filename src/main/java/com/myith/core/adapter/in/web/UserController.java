package com.myith.core.adapter.in.web;

import com.myith.core.application.auth.UserService;
import com.myith.core.common.ApiResponse;
import com.myith.core.domain.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Long userId) {
        User user = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal Long userId,
            @RequestBody UpdateMeRequest request) {
        User user = userService.updateMe(userId, request.nickname(), request.profileImageUrl());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(@AuthenticationPrincipal Long userId) {
        userService.deleteMe(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    record UpdateMeRequest(String nickname, String profileImageUrl) {}
    record UserResponse(Long userId, String email, String nickname, String profileImageUrl) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl());
        }
    }
}
