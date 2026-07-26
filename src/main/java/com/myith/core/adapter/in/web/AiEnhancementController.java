package com.myith.core.adapter.in.web;

import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
                    화면 4-2에서 POST /api/quests/{questId}/ai-enhancements로 202를 수신한 뒤, 1~2초 간격으로 폴링하여 결과를 확인하세요.
                    status가 PROCESSING이면 계속 폴링하고, COMPLETED 또는 FAILED이면 폴링을 종료하세요.
                    COMPLETED 시 각 필드 활용법은 다음과 같습니다.
                    - enhancedStar: 비교 모달 오른쪽(AI 제안)에 표시합니다. PROCESSING이면 null입니다.
                    - feedback: 항목별 개선 힌트 목록입니다. 빈 배열이면 힌트 섹션을 숨기세요.
                    - resumeDraft: 자기소개서 초안 영역에 표시합니다.
                    서버는 원문을 수정하지 않습니다. 사용자가 '적용'을 누르면 프론트가 textarea를 채운 뒤 PUT /star로 저장하세요.
                    FAILED 시 errorCode를 참고해 적절한 안내 문구를 표시하세요."""
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
                            @ExampleObject(name = "FAILED", summary = "실패", value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC",
                                        "status": "FAILED",
                                        "errorCode": "AI_PROVIDER_TIMEOUT"
                                      }
                                    }""")
                    }))
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
            @Schema(description = "퀘스트 ID입니다. COMPLETED 상태일 때만 포함됩니다", nullable = true, example = "qst_05") String questId,
            @Schema(description = "처리 상태입니다. PROCESSING이면 폴링을 계속하고, COMPLETED 또는 FAILED이면 종료하세요", example = "COMPLETED",
                    allowableValues = {"PROCESSING", "COMPLETED", "FAILED"}) String status,
            @Schema(description = "화면 4-2 AI 비교 모달 오른쪽에 표시할 보완된 STAR입니다. COMPLETED일 때만 포함되며, PROCESSING이면 null입니다", nullable = true) EnhancedStar enhancedStar,
            @Schema(description = "화면 4-2 항목별 개선 힌트 목록입니다. COMPLETED일 때만 포함됩니다. 빈 배열이면 힌트 섹션을 숨기세요") List<FeedbackEntry> feedback,
            @Schema(description = "화면 4-2·5 자기소개서 초안 영역에 표시할 텍스트입니다. COMPLETED일 때만 포함됩니다",
                    nullable = true, example = "대용량 조회 성능 문제를 캐시 도입으로 해결한 경험이 있습니다. ...") String resumeDraft,
            @Schema(description = "FAILED 상태일 때 오류 코드입니다. 해당 코드에 맞는 안내 문구를 표시하세요", nullable = true, example = "AI_PROVIDER_TIMEOUT") String errorCode,
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
