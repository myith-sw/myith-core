package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestDetailService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.ErrorResponse;
import com.myith.core.common.IdCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quest", description = "퀘스트·STAR")
@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestDetailService questDetailService;

    public QuestController(QuestDetailService questDetailService) {
        this.questDetailService = questDetailService;
    }

    // ────────────────── GET /api/quests/{questId} ──────────────────

    @Operation(
            summary = "퀘스트 상세 조회",
            description = """
                    화면 4-2(퀘스트 상세 + STAR).
                    ncsUnit은 source: CUSTOM이면 null이다.
                    certifications는 연계 자격 전부를 내린다. 없으면 빈 배열(프론트가 '해당 없음' 표시).
                    star가 null이면 입력칸을 빈 값으로 시작한다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{questId}")
    public ResponseEntity<ApiResponse<QuestDetailResponse>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId) {
        QuestDetailService.QuestDetailDto dto = questDetailService.getDetail(userId, IdCodec.decode(questId));
        QuestDetailResponse response = new QuestDetailResponse(
                "qst_" + dto.questId(), "rmp_01J3ABC", dto.level(),
                "cs", dto.axisName(), dto.title(), dto.status(), "ACTIVITY", 1, 0,
                dto.completionCriteria(),
                dto.ncsUnit() != null
                        ? new NcsUnitResponse(dto.ncsUnit().code(), dto.ncsUnit().name(), dto.ncsUnit().description())
                        : null,
                dto.certifications() != null
                        ? dto.certifications().stream().map(c -> new CertResponse(c.name())).toList()
                        : List.of(),
                dto.star() != null
                        ? new StarResponse(dto.star().situation(), dto.star().task(), dto.star().action(), dto.star().result())
                        : null,
                null, null
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ────────────────── PUT /api/quests/{questId}/star ──────────────────

    @Operation(
            summary = "STAR 기록 저장 (임시 저장)",
            description = """
                    각 필드 trim 후 0~2000자. 임시 저장이므로 빈 값을 허용한다.
                    최초 저장 시 퀘스트 상태가 OPEN → PENDING으로 바뀐다.
                    프론트는 textarea blur 또는 debounce로 호출한다.
                    AI 제안을 적용해 저장한 경우 source: "ai-assisted" + aiEnhancementId를 채운다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "QUEST_LOCKED — 잠긴 퀘스트에 쓰기 시도",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "QUEST_LOCKED",
                                        "message": "선행 퀘스트를 먼저 완료해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PutMapping("/{questId}/star")
    public ResponseEntity<ApiResponse<SaveStarResponse>> saveStar(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId,
            @RequestBody SaveStarRequest request) {
        questDetailService.saveStar(userId, IdCodec.decode(questId),
                request.star().situation(), request.star().task(),
                request.star().action(), request.star().result());
        return ResponseEntity.ok(ApiResponse.of(new SaveStarResponse(
                questId, request.star(),
                request.source() != null ? request.source() : "manual",
                "PENDING", null
        )));
    }

    // ────────────────── PATCH /api/quests/{questId}/complete ──────────────────

    @Operation(
            summary = "퀘스트 완료 토글",
            description = """
                    STAR 본문은 여기서 보내지 않는다. PUT /star로 이미 저장된 값을 쓴다.
                    응답에 radar를 함께 내려 프론트가 레이더를 재조회 없이 갱신하게 한다.
                    409 VERSION_CONFLICT 응답에는 최신 version을 포함해 프론트가 재시도할 수 있게 한다.""",
            parameters = @Parameter(name = "Idempotency-Key", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                    description = "멱등성 키 (optional)", required = false,
                    schema = @Schema(type = "string"))
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "VERSION_CONFLICT / QUEST_LOCKED",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "VERSION_CONFLICT", value = """
                                            {
                                              "error": {
                                                "code": "VERSION_CONFLICT",
                                                "message": "다른 요청과 충돌했습니다. 새로고침 후 다시 시도해주세요.",
                                                "requestId": "req_01J3ABC"
                                              }
                                            }"""),
                                    @ExampleObject(name = "QUEST_LOCKED", value = """
                                            {
                                              "error": {
                                                "code": "QUEST_LOCKED",
                                                "message": "선행 퀘스트를 먼저 완료해주세요.",
                                                "requestId": "req_01J3ABC"
                                              }
                                            }""")
                            }))
    })
    @PatchMapping("/{questId}/complete")
    public ResponseEntity<ApiResponse<CompleteResponse>> toggleComplete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CompleteRequest request) {
        questDetailService.toggleComplete(userId, IdCodec.decode(questId), request.completed(), request.version());
        // 실제 구현에서는 스냅샷에서 characterChanges, radar 등을 가져와 반환한다.
        // 문서화 목적이므로 구조만 정의한다.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ────────────────── POST /api/quests/{questId}/ai-enhancements ──────────────────

    @Operation(
            summary = "STAR AI 보완 요청",
            description = """
                    STAR 원문을 AI에게 보내 보완 제안·피드백·자기소개서 초안을 받는다.
                    서버는 원문을 수정하지 않는다. 사용자가 '적용'을 누르면 프론트가 textarea를 채우고 PUT /star로 저장한다.
                    202 수신 후 프론트가 GET /api/ai-enhancements/{requestId}를 짧은 간격으로 폴링한다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "비동기 접수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "QUEST_LOCKED",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "QUEST_LOCKED",
                                        "message": "선행 퀘스트를 먼저 완료해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AI_INPUT_TOO_LONG / AI_SAFETY_REJECTED",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "AI_INPUT_TOO_LONG",
                                        "message": "입력이 너무 깁니다. 각 항목을 2000자 이내로 작성해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI_RATE_LIMITED",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "AI_RATE_LIMITED",
                                        "message": "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{questId}/ai-enhancements")
    public ResponseEntity<ApiResponse<AiEnhancementAcceptedResponse>> requestAiEnhancement(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId,
            @Valid @RequestBody AiEnhancementRequest request) {
        // 미구현 — Outbox 발행 후 requestId를 반환
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ── Request / Response DTOs ──

    @Schema(name = "QuestDetailResponse")
    record QuestDetailResponse(
            @Schema(description = "퀘스트 ID", example = "qst_05") String questId,
            @Schema(description = "로드맵 ID", example = "rmp_01J3ABC") String roadmapId,
            @Schema(description = "레벨", example = "5") int level,
            @Schema(description = "역량 축 코드", example = "cs") String axisCode,
            @Schema(description = "역량 축 이름", example = "CS·자료구조") String axisName,
            @Schema(description = "퀘스트 제목", example = "CS 면접 질문을 정리한다") String title,
            @Schema(description = "화면 4-2 상태", example = "OPEN",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"}) String status,
            @Schema(description = "퀘스트 종류", example = "ACTIVITY",
                    allowableValues = {"SKILL", "ACTIVITY", "CUSTOM"}) String source,
            @Schema(description = "레벨 내 순서", example = "1") int order,
            @Schema(description = "낙관적 락 버전", example = "0") long version,
            @Schema(description = "화면 4-2 '완료 기준' 박스", example = "네트워크·OS·DB 핵심 답안을 정리한다") String completionCriteria,
            @Schema(description = "화면 4-2 'NCS 능력단위 근거' 박스. source: CUSTOM이면 null") NcsUnitResponse ncsUnit,
            @Schema(description = "화면 4-2 '추천 자격'. 빈 배열이면 '해당 없음'") List<CertResponse> certifications,
            @Schema(description = "화면 4-2 STAR 입력칸 4개 초기값. null이면 빈 값으로 시작", nullable = true) StarResponse star,
            @Schema(description = "STAR 출처. manual | ai-assisted", nullable = true) String starSource,
            @Schema(description = "최근 수정일", example = "2026-07-24T03:00:00Z") String updatedAt
    ) {}

    @Schema(name = "NcsUnitResponse")
    record NcsUnitResponse(
            @Schema(description = "NCS 능력단위 코드", example = "2001010701_16v3") String code,
            @Schema(description = "NCS 능력단위명", example = "응용SW기초기술활용") String name,
            @Schema(description = "NCS 능력단위 설명", example = "자료구조와 알고리즘 등 기초 기술을 활용하는 능력") String description
    ) {}

    @Schema(name = "CertResponse")
    record CertResponse(
            @Schema(description = "자격증 이름", example = "정보처리기사") String name
    ) {}

    @Schema(name = "StarResponse")
    record StarResponse(
            @Schema(description = "상황(S)", example = "팀 프로젝트에서 API 응답이 3초 이상 걸리는 문제가 발생했다.") String situation,
            @Schema(description = "과제(T)", example = "응답 시간을 500ms 이하로 줄여야 했다.") String task,
            @Schema(description = "행동(A)", example = "N+1 쿼리를 페치 조인으로 개선하고 Redis 캐시를 도입했다.") String action,
            @Schema(description = "결과(R)", example = "평균 응답 시간이 200ms로 감소했다.") String result
    ) {}

    @Schema(name = "SaveStarRequest")
    record SaveStarRequest(
            @Schema(description = "STAR 기록. 각 필드 trim 후 0~2000자. 임시 저장이므로 빈 값 허용") StarInput star,
            @Schema(description = "출처. manual | ai-assisted", example = "manual",
                    allowableValues = {"manual", "ai-assisted"}) String source,
            @Schema(description = "AI 보완을 적용한 경우 해당 요청 ID", example = "null") String aiEnhancementId
    ) {}

    @Schema(name = "StarInput")
    record StarInput(
            @Schema(description = "상황(S)", example = "팀 프로젝트에서 API 응답이 3초 이상 걸리는 문제가 발생했다.")
            @Size(max = 2000) String situation,
            @Schema(description = "과제(T)", example = "응답 시간을 500ms 이하로 줄여야 했다.")
            @Size(max = 2000) String task,
            @Schema(description = "행동(A)", example = "N+1 쿼리를 페치 조인으로 개선하고 Redis 캐시를 도입했다.")
            @Size(max = 2000) String action,
            @Schema(description = "결과(R)", example = "평균 응답 시간이 200ms로 감소했다.")
            @Size(max = 2000) String result
    ) {}

    @Schema(name = "SaveStarResponse")
    record SaveStarResponse(
            @Schema(description = "퀘스트 ID", example = "qst_05") String questId,
            @Schema(description = "저장된 STAR 기록") StarInput star,
            @Schema(description = "출처", example = "manual") String source,
            @Schema(description = "퀘스트 상태. 최초 저장 시 OPEN → PENDING", example = "PENDING") String status,
            @Schema(description = "수정 시각", example = "2026-07-24T03:10:00Z") String updatedAt
    ) {}

    @Schema(name = "CompleteRequest")
    record CompleteRequest(
            @Schema(description = "true: 완료 / false: 완료 취소", example = "true")
            @NotNull Boolean completed,
            @Schema(description = "낙관적 락 버전. GET /api/roadmaps/{id} 또는 GET /api/quests/{id}에서 받은 version", example = "3")
            @NotNull Long version
    ) {}

    @Schema(name = "CompleteResponse")
    record CompleteResponse(
            @Schema(description = "완료된 퀘스트 정보") CompletedQuestInfo quest,
            @Schema(description = "캐릭터 변화") CharacterChanges characterChanges,
            @Schema(description = "완료 직후 잠금 해제된 퀘스트 ID 목록. 화면 4-1 잠금 해제 애니메이션 대상") List<String> unlockedQuestIds,
            @Schema(description = "화면 5 레이더 갱신용. 재조회 없이 반영") List<RadarEntry> radar
    ) {}

    @Schema(name = "CompletedQuestInfo")
    record CompletedQuestInfo(
            @Schema(description = "퀘스트 ID", example = "qst_05") String questId,
            @Schema(description = "변경된 상태", example = "DONE") String status,
            @Schema(description = "완료 시각", example = "2026-07-24T03:20:00Z") String completedAt,
            @Schema(description = "증가된 버전", example = "4") long version
    ) {}

    @Schema(name = "CharacterChanges")
    record CharacterChanges(
            @Schema(description = "갱신된 완료율", example = "84") int completionRate,
            @Schema(description = "갱신된 성장 단계 숫자", example = "4") int stage,
            @Schema(description = "갱신된 성장 단계 라벨", example = "완성") String stageLabel,
            @Schema(description = "현재 진행 중인 레벨", example = "5") int level,
            @Schema(description = "다음 퀘스트. 모든 퀘스트 완료 시 null")
            CharacterController.NextQuestResponse nextQuest
    ) {}

    @Schema(name = "RadarEntry")
    record RadarEntry(
            @Schema(description = "역량 축 코드. enum이 아닌 자유 문자열", example = "programming") String axisCode,
            @Schema(description = "역량 축 이름", example = "프로그래밍 기초") String axisName,
            @Schema(description = "화면 5 역량 다각형(레이더) 축 값. 단순 완료율 = (DONE+ALREADY_KNOWN)/전체 × 100", example = "72") int percent
    ) {}

    @Schema(name = "AiEnhancementRequest")
    record AiEnhancementRequest(
            @Schema(description = "AI 보완 대상 STAR 원문") StarInput star,
            @Schema(description = "로케일", example = "ko-KR") String locale,
            @Schema(description = "문체 스타일", example = "concise-professional") String style
    ) {}

    @Schema(name = "AiEnhancementAcceptedResponse")
    record AiEnhancementAcceptedResponse(
            @Schema(description = "AI 보완 요청 ID. 폴링에 사용", example = "aie_01J3ABC") String requestId
    ) {}
}
