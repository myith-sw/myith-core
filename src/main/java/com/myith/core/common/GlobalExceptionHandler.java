package com.myith.core.common;

import com.myith.core.application.auth.AuthService;
import com.myith.core.application.auth.GoogleTokenVerifier;
import com.myith.core.application.auth.UserService;
import com.myith.core.application.roadmap.JobQueryService;
import com.myith.core.application.export.ExportService;
import com.myith.core.application.quest.QuestManageService;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.application.upload.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String requestId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "req_unknown";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("VALIDATION_ERROR", "입력값이 올바르지 않습니다.", fieldErrors, requestId()));
    }

    @ExceptionHandler(GoogleTokenVerifier.InvalidGoogleTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGoogleToken(GoogleTokenVerifier.InvalidGoogleTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_ID_TOKEN", "Google ID Token 검증에 실패했습니다.", requestId()));
    }

    @ExceptionHandler(GoogleTokenVerifier.GoogleVerificationException.class)
    public ResponseEntity<ErrorResponse> handleGoogleVerification(GoogleTokenVerifier.GoogleVerificationException e) {
        log.error("Google verification failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Google 인증 서비스에 연결할 수 없습니다.", requestId()));
    }

    @ExceptionHandler(AuthService.InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(AuthService.InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_REFRESH_TOKEN", "유효하지 않은 토큰입니다.", requestId()));
    }

    @ExceptionHandler(UserService.UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserService.UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), requestId()));
    }

    @ExceptionHandler(RoadmapQueryService.RoadmapNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoadmapNotFound(RoadmapQueryService.RoadmapNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), requestId()));
    }

    @ExceptionHandler(RoadmapQueryService.CharacterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCharacterNotFound(RoadmapQueryService.CharacterNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "캐릭터를 찾을 수 없습니다.", requestId()));
    }

    @ExceptionHandler(RoadmapQueryService.RoadmapAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleRoadmapAccessDenied(RoadmapQueryService.RoadmapAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN_RESOURCE", "해당 리소스에 접근할 수 없습니다.", requestId()));
    }

    @ExceptionHandler(QuestManageService.QuestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleQuestNotFound(QuestManageService.QuestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), requestId()));
    }

    @ExceptionHandler(QuestManageService.CannotDeleteNonCustomQuestException.class)
    public ResponseEntity<ErrorResponse> handleCannotDeleteNonCustom(QuestManageService.CannotDeleteNonCustomQuestException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("CUSTOM_QUEST_ONLY", "사용자 정의 퀘스트만 삭제할 수 있습니다.", requestId()));
    }

    @ExceptionHandler(QuestManageService.QuestLockedException.class)
    public ResponseEntity<ErrorResponse> handleQuestLocked(QuestManageService.QuestLockedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("QUEST_LOCKED", "선행 퀘스트를 먼저 완료해주세요.", requestId()));
    }

    @ExceptionHandler(QuestManageService.OptimisticLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(QuestManageService.OptimisticLockConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("VERSION_CONFLICT", "다른 요청과 충돌했습니다. 새로고침 후 다시 시도해주세요.", requestId()));
    }

    @ExceptionHandler(RoadmapCreateService.ExperiencesLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleExperiencesLimit(RoadmapCreateService.ExperiencesLimitExceededException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("EXPERIENCES_LIMIT_EXCEEDED", e.getMessage(), requestId()));
    }

    @ExceptionHandler(RoadmapCreateService.DuplicateSpeciesException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSpecies(RoadmapCreateService.DuplicateSpeciesException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("VERSION_CONFLICT", e.getMessage(), requestId()));
    }

    @ExceptionHandler(ExportService.UnsupportedFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(ExportService.UnsupportedFormatException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_EXPORT_FORMAT", "지원하지 않는 내보내기 형식입니다. md 또는 pdf만 가능합니다.", requestId()));
    }

    @ExceptionHandler(UploadService.InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFile(UploadService.InvalidFileException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("UNSUPPORTED_FILE_TYPE", e.getMessage(), requestId()));
    }

    @ExceptionHandler(JobQueryService.JobProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobProfileNotFound(JobQueryService.JobProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("JOB_PROFILE_NOT_READY", "해당 직무의 프로필이 아직 준비되지 않았습니다.", requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "서버 오류가 발생했습니다.", requestId()));
    }
}
