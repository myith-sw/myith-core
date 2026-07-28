package com.myith.core.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.AiEnhancementResultStore;
import com.myith.core.application.port.OutboxRepository;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.ErrorResponse;
import com.myith.core.common.IdCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Quest", description = "퀘스트·STAR")
@RestController
@RequestMapping("/api/ai-enhancements")
public class AiEnhancementController {

    private final AiEnhancementResultStore resultStore;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final long timeoutSeconds;

    public AiEnhancementController(AiEnhancementResultStore resultStore,
                                   OutboxRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   @Value("${policy.ai-enhancement.timeout-seconds}") long timeoutSeconds) {
        this.resultStore = resultStore;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Operation(
            summary = "AI 보완 결과 조회",
            description = """
                    폴링 플로우:
                    1. POST /api/quests/{questId}/ai-enhancements를 호출하면 202 + { requestId: "aie_..." }를 수신합니다.
                    2. 이 엔드포인트(GET /api/ai-enhancements/{requestId})를 1~2초 간격으로 폴링합니다.
                    3. status가 PROCESSING이면 계속 폴링합니다.
                    4. status가 COMPLETED 또는 FAILED이면 폴링을 종료합니다.
                    클라이언트 측 별도 타임아웃은 불필요합니다. 서버가 60초 경과 시 FAILED(errorCode: AI_TIMEOUT)를 반환합니다.

                    COMPLETED 시 각 필드 활용법:
                    - enhancedStar: 비교 모달 오른쪽(AI 제안)에 표시합니다. PROCESSING이면 null입니다.
                      COMPLETED이면서 enhancedStar가 null일 수 있습니다(사실검증 가드). 이때 feedback과 resumeDraft는 정상입니다.
                    - feedback: 항목별 개선 힌트 목록입니다. 빈 배열이면 힌트 섹션을 숨기세요.
                    - resumeDraft: 자기소개서 초안 영역에 표시합니다.
                    서버는 원문을 수정하지 않습니다. 사용자가 '적용'을 누르면 프론트가 textarea를 채운 뒤 PUT /api/quests/{questId}/star로 저장하세요.

                    FAILED 시에도 enhancedStar(에러 안내 문구)와 feedback(빈 배열)을 포함합니다.
                    프론트는 enhancedStar를 null 체크 없이 렌더링할 수 있습니다.
                    FAILED일 때는 '적용' 버튼을 숨기고 errorCode에 맞는 안내를 표시하세요.
                    errorCode 종류: AI_PROVIDER_TIMEOUT(Worker LLM 호출 실패),
                                  AI_TIMEOUT(서버 대기 60초 초과), INTERNAL_ERROR(내부 오류)."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "status 필드로 상태를 구분합니다. PROCESSING / COMPLETED / FAILED 세 가지입니다.",
            content = @Content(mediaType = "application/json",
                    examples = {
                            @ExampleObject(name = "PROCESSING", summary = "처리 중", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "PROCESSING"
                                      }
                                    }"""),
                            @ExampleObject(name = "COMPLETED", summary = "완료", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "questId": "qst_05",
                                        "status": "COMPLETED",
                                        "enhancedStar": {
                                          "situation": "4인 팀 프로젝트에서 메인 API 엔드포인트의 평균 응답 시간이 3.2초로 측정되어 사용자 이탈이 증가했다.",
                                          "task": "2주 내에 응답 시간을 500ms 이하로 줄이는 성능 개선 방안을 수립·적용해야 했다.",
                                          "action": "JPA N+1 문제를 분석하여 페치 조인과 배치 사이즈 최적화를 적용하고, 반복 조회 데이터에 Redis 캐시를 도입했다.",
                                          "result": "평균 응답 시간이 3.2초에서 180ms로 94% 감소했고, 이탈률이 15% 개선되었다."
                                        },
                                        "feedback": [
                                          {
                                            "field": "action",
                                            "issue": "'캐시 적용'이 추상적입니다",
                                            "suggestion": "어떤 데이터에 어떤 캐시를 적용했는지 구체화하세요"
                                          }
                                        ],
                                        "resumeDraft": "대용량 조회 성능 문제를 캐시 도입으로 해결한 경험이 있습니다. ...",
                                        "createdAt": "2026-07-24T03:25:00Z"
                                      }
                                    }"""),
                            @ExampleObject(name = "FAILED", summary = "실패 (Worker 오류)", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "FAILED",
                                        "enhancedStar": {
                                          "situation": "AI 보완에 실패했습니다.",
                                          "task": "",
                                          "action": "",
                                          "result": ""
                                        },
                                        "feedback": [],
                                        "errorCode": "AI_PROVIDER_TIMEOUT"
                                      }
                                    }"""),
                            @ExampleObject(name = "FAILED_TIMEOUT", summary = "실패 (서버 타임아웃)", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "FAILED",
                                        "enhancedStar": {
                                          "situation": "AI 처리 시간이 초과되었습니다.",
                                          "task": "",
                                          "action": "",
                                          "result": ""
                                        },
                                        "feedback": [],
                                        "errorCode": "AI_TIMEOUT"
                                      }
                                    }""")
                    }))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "requestId 형식이 올바르지 않습니다",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "error": {
                                "code": "INVALID_REQUEST_ID",
                                "message": "requestId 형식이 올바르지 않습니다."
                              }
                            }""")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "존재하지 않는 requestId입니다",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "error": {
                                "code": "NOT_FOUND",
                                "message": "해당 AI 보완 요청을 찾을 수 없습니다."
                              }
                            }""")))
    @GetMapping("/{requestId}")
    public ResponseEntity<?> getResult(
            @AuthenticationPrincipal Long userId,
            @PathVariable String requestId) {

        UUID uuid;
        try {
            uuid = normalizeRequestId(requestId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INVALID_REQUEST_ID", "requestId 형식이 올바르지 않습니다.", requestId()));
        }

        String uuidStr = uuid.toString();
        Optional<String> resultJson = resultStore.find(uuidStr);

        if (resultJson.isPresent()) {
            return handleResult(requestId, resultJson.get());
        }

        // 결과 없음 — outbox에서 경과 시간 판정
        Optional<Instant> createdAt = outboxRepository.findCreatedAtByEventId(uuid);
        if (createdAt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("NOT_FOUND", "해당 AI 보완 요청을 찾을 수 없습니다.", requestId()));
        }

        if (Duration.between(createdAt.get(), Instant.now()).getSeconds() > timeoutSeconds) {
            return ResponseEntity.ok(ApiResponse.of(
                    new AiEnhancementResultResponse(requestId, null, "FAILED",
                            new EnhancedStar("AI 처리 시간이 초과되었습니다.", "", "", ""),
                            List.of(), null, "AI_TIMEOUT", null)));
        }

        return ResponseEntity.ok(ApiResponse.of(
                new AiEnhancementResultResponse(requestId, null, "PROCESSING",
                        null, null, null, null, null)));
    }

    /** "aie_" 접두어가 있으면 벗겨 순수 UUID로 만든다. 저장 키와 형식을 맞춘다. */
    private static UUID normalizeRequestId(String raw) {
        String s = raw.startsWith("aie_") ? raw.substring(4) : raw;
        return UUID.fromString(s);
    }

    private String requestId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "req_unknown";
    }

    private ResponseEntity<ApiResponse<AiEnhancementResultResponse>> handleResult(String requestId, String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String status = node.has("status") ? node.get("status").asText() : "COMPLETED";

            if ("FAILED".equals(status)) {
                String errorCode = node.has("errorCode") ? node.get("errorCode").asText() : null;
                return ResponseEntity.ok(ApiResponse.of(
                        new AiEnhancementResultResponse(requestId, null, "FAILED",
                                new EnhancedStar("AI 보완에 실패했습니다.", "", "", ""),
                                List.of(), null, errorCode, null)));
            }

            EnhancedStar enhancedStar = null;
            if (node.has("enhancedStar") && !node.get("enhancedStar").isNull()) {
                JsonNode es = node.get("enhancedStar");
                enhancedStar = new EnhancedStar(
                        es.has("situation") ? es.get("situation").asText() : null,
                        es.has("task") ? es.get("task").asText() : null,
                        es.has("action") ? es.get("action").asText() : null,
                        es.has("result") ? es.get("result").asText() : null
                );
            }

            List<FeedbackEntry> feedback = null;
            if (node.has("feedback") && !node.get("feedback").isNull()) {
                feedback = objectMapper.convertValue(node.get("feedback"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, FeedbackEntry.class));
            }

            String questId = node.has("questId") && !node.get("questId").isNull()
                    ? IdCodec.encode(node.get("questId").asLong(), "qst_")
                    : null;
            String resumeDraft = node.has("resumeDraft") ? node.get("resumeDraft").asText() : null;
            String createdAt = node.has("createdAt") ? node.get("createdAt").asText() : null;

            return ResponseEntity.ok(ApiResponse.of(
                    new AiEnhancementResultResponse(requestId, questId, "COMPLETED",
                            enhancedStar, feedback, resumeDraft, null, createdAt)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.of(
                    new AiEnhancementResultResponse(requestId, null, "FAILED",
                            new EnhancedStar("AI 보완 중 오류가 발생했습니다.", "", "", ""),
                            List.of(), null, "INTERNAL_ERROR", null)));
        }
    }

    // -- Response DTOs --

    @Schema(name = "AiEnhancementResultResponse")
    record AiEnhancementResultResponse(
            @Schema(description = "요청 ID입니다. 'aie_' 접두사 + UUID 형식입니다(예: aie_550e8400-e29b-41d4-a716-446655440000). POST /api/quests/{questId}/ai-enhancements의 202 응답에서 받은 값을 그대로 사용하세요.", example = "aie_01J3ABC") String requestId,
            @Schema(description = "퀘스트 ID입니다. COMPLETED 상태일 때만 포함됩니다", nullable = true, example = "qst_05") String questId,
            @Schema(description = "처리 상태입니다. PROCESSING이면 폴링을 계속하고, COMPLETED 또는 FAILED이면 종료하세요", example = "COMPLETED",
                    allowableValues = {"PROCESSING", "COMPLETED", "FAILED"}) String status,
            @Schema(description = "화면 4-2 AI 비교 모달 오른쪽에 표시할 보완된 STAR입니다. COMPLETED일 때만 포함되며, PROCESSING이면 null입니다", nullable = true) EnhancedStar enhancedStar,
            @Schema(description = "화면 4-2 항목별 개선 힌트 목록입니다. COMPLETED일 때만 포함됩니다. 빈 배열이면 힌트 섹션을 숨기세요") List<FeedbackEntry> feedback,
            @Schema(description = "화면 4-2·5 자기소개서 초안 영역에 표시할 텍스트입니다. COMPLETED일 때만 포함됩니다",
                    nullable = true, example = "대용량 조회 성능 문제를 캐시 도입으로 해결한 경험이 있습니다. ...") String resumeDraft,
            @Schema(description = "FAILED 상태일 때 오류 코드입니다. 가능한 값: AI_PROVIDER_TIMEOUT(Worker의 LLM 호출이 시간 초과됨), AI_TIMEOUT(서버 측 대기 시간 초과, 기본 60초), INTERNAL_ERROR(결과 파싱 등 내부 오류). 해당 코드에 맞는 안내 문구를 표시하세요.",
                    nullable = true, example = "AI_PROVIDER_TIMEOUT",
                    allowableValues = {"AI_PROVIDER_TIMEOUT", "AI_TIMEOUT", "INTERNAL_ERROR"}) String errorCode,
            @Schema(description = "AI 보완 결과 생성 시각", example = "2026-07-24T03:25:00Z") String createdAt
    ) {}

    @Schema(name = "EnhancedStar")
    record EnhancedStar(
            @Schema(description = "보완된 상황(S)") String situation,
            @Schema(description = "보완된 과제(T)") String task,
            @Schema(description = "보완된 행동(A)") String action,
            @Schema(description = "보완된 결과(R)") String result
    ) {}

    @Schema(name = "FeedbackEntry")
    record FeedbackEntry(
            @Schema(description = "피드백 대상 STAR 필드입니다. 해당 입력칸에 힌트를 표시하세요", example = "action",
                    allowableValues = {"situation", "task", "action", "result"}) String field,
            @Schema(description = "지적 사항입니다", example = "'캐시 적용'이 추상적입니다") String issue,
            @Schema(description = "개선 제안입니다", example = "어떤 데이터에 어떤 캐시를 적용했는지 구체화하세요") String suggestion
    ) {}
}
