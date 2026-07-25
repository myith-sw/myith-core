package com.myith.core.adapter.in.web;

import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Quest", description = "퀘스트·STAR")
@RestController
@RequestMapping("/api/ai-enhancements")
public class AiEnhancementController {

    @Operation(
            summary = "AI 보완 결과 조회",
            description = """
                    POST /api/quests/{questId}/ai-enhancements에서 202 수신 후 프론트가 짧은 간격으로 폴링한다.
                    enhancedStar → 비교 모달 오른쪽 / feedback → 항목별 힌트 / resumeDraft → 자기소개서 초안 영역.
                    서버는 원문을 수정하지 않는다. 사용자가 '적용'을 누르면 프론트가 textarea를 채우고 PUT /star로 저장한다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 중",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AiEnhancementResultResponse.class),
                            examples = @ExampleObject(name = "PROCESSING", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "PROCESSING"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AiEnhancementResultResponse.class),
                            examples = @ExampleObject(name = "COMPLETED", value = """
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
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AiEnhancementResultResponse.class),
                            examples = @ExampleObject(name = "FAILED", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "FAILED",
                                        "errorCode": "AI_PROVIDER_TIMEOUT"
                                      }
                                    }""")))
    })
    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<AiEnhancementResultResponse>> getResult(
            @AuthenticationPrincipal Long userId,
            @PathVariable String requestId) {
        // 미구현 — Worker 결과를 조회하여 반환
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ── Response DTOs ──

    @Schema(name = "AiEnhancementResultResponse")
    record AiEnhancementResultResponse(
            @Schema(description = "요청 ID", example = "aie_01J3ABC") String requestId,
            @Schema(description = "퀘스트 ID. COMPLETED 시에만 포함", example = "qst_05") String questId,
            @Schema(description = "처리 상태", example = "COMPLETED",
                    allowableValues = {"PROCESSING", "COMPLETED", "FAILED"}) String status,
            @Schema(description = "화면 4-2 AI 비교 모달 오른쪽. COMPLETED 시에만 포함. status가 PROCESSING이면 null", nullable = true) EnhancedStar enhancedStar,
            @Schema(description = "화면 4-2 항목별 개선 힌트. COMPLETED 시에만 포함") List<FeedbackEntry> feedback,
            @Schema(description = "화면 4-2·5 자기소개서 초안 영역. COMPLETED 시에만 포함",
                    example = "대용량 조회 성능 문제를 캐시 도입으로 해결한 경험이 있습니다. ...") String resumeDraft,
            @Schema(description = "FAILED 시 오류 코드", example = "AI_PROVIDER_TIMEOUT") String errorCode,
            @Schema(description = "생성 시각", example = "2026-07-24T03:25:00Z") String createdAt
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
            @Schema(description = "피드백 대상 STAR 필드", example = "action",
                    allowableValues = {"situation", "task", "action", "result"}) String field,
            @Schema(description = "지적 사항", example = "'캐시 적용'이 추상적입니다") String issue,
            @Schema(description = "개선 제안", example = "어떤 데이터에 어떤 캐시를 적용했는지 구체화하세요") String suggestion
    ) {}
}
