package com.myith.core.common;

import com.myith.core.application.auth.AuthService;
import com.myith.core.application.auth.GoogleTokenVerifier;
import com.myith.core.application.auth.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(GoogleTokenVerifier.InvalidGoogleTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGoogleToken(GoogleTokenVerifier.InvalidGoogleTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_GOOGLE_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(GoogleTokenVerifier.GoogleVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleGoogleVerification(GoogleTokenVerifier.GoogleVerificationException e) {
        log.error("Google verification failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("GOOGLE_VERIFICATION_FAILED", "Google 인증 서비스에 연결할 수 없습니다."));
    }

    @ExceptionHandler(AuthService.InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(AuthService.InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(UserService.UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserService.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("USER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}