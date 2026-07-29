package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestDetailService;
import com.myith.core.common.ApiResponse;
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
import java.util.UUID;

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
                    화면 4-2(퀘스트 상세 + STAR)에서 호출합니다.
                    ncsUnit은 source가 CUSTOM이면 null입니다. null인 경우 'NCS 근거' 섹션 자체를 숨기세요.
                    certifications는 unit_type '필수' 우선·가나다순 정렬, 최대 5개(설정값)까지 반환합니다. 잘린 개수는 moreCertificationCount로 내려옵니다. 빈 배열이면 '해당 없음'을 표시하세요.
                    star가 null이면 STAR 입력칸을 빈 값으로 초기화하세요."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "questId": "qst_05",
                                "roadmapId": "rmp_01J3ABC",
                                "level": 5,
                                "axisCode": "cs",
                                "axisName": "CS·자료구조",
                                "title": "CS 면접 질문을 정리한다",
                                "status": "OPEN",
                                "source": "ACTIVITY",
                                "order": 1,
                                "version": 0,
                                "completionCriteria": "네트워크·OS·DB 핵심 답안을 정리한다",
                                "ncsUnit": {
                                  "code": "2001010701_16v3",
                                  "name": "응용SW기초기술활용",
                                  "description": "자료구조와 알고리즘 등 기초 기술을 활용하는 능력"
                                },
                                "certifications": [
                                  { "name": "정보처리기사" },
                                  { "name": "정보처리산업기사" }
                                ],
                                "moreCertificationCount": 3,
                                "star": null,
                                "starSource": null,
                                "updatedAt": "2026-07-24T03:00:00Z"
                              }
                            }""")))
    @GetMapping("/{questId}")
    public ResponseEntity<ApiResponse<QuestDetailResponse>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId) {
        QuestDetailService.QuestDetailDto dto = questDetailService.getDetail(userId, IdCodec.decode(questId));
        QuestDetailResponse response = new QuestDetailResponse(
                "qst_" + dto.questId(), "rmp_" + dto.roadmapId(), dto.level(),
                dto.axisCode(), dto.axisName(), dto.title(), dto.status(), dto.source(),
                dto.order(), dto.version(),
                dto.completionCriteria(), dto.guidance(),
                dto.ncsUnit() != null
                        ? new NcsUnitResponse(dto.ncsUnit().code(), dto.ncsUnit().name(), dto.ncsUnit().description())
                        : null,
                dto.certifications() != null
                        ? dto.certifications().stream().map(c -> new CertResponse(c.name())).toList()
                        : List.of(),
                dto.moreCertificationCount(),
                dto.star() != null
                        ? new StarResponse(dto.star().situation(), dto.star().task(), dto.star().action(), dto.star().result())
                        : null,
                null, dto.updatedAt()
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ────────────────── PUT /api/quests/{questId}/star ──────────────────

    @Operation(
            summary = "STAR 기록 저장 (임시 저장)",
            description = """
                    완료와 무관한 STAR 저장·수정 및 AI 보완 적용 기록용입니다.
                    완료와 동시에 저장하려면 PATCH /complete 의 star 필드를 사용하세요.
                    화면 4-2(STAR 입력 탭)에서 textarea blur 또는 debounce 시 호출합니다.
                    각 필드는 trim 후 최대 2000자입니다. 임시 저장이므로 빈 값도 허용합니다.
                    최초 저장 시 퀘스트 상태는 내부적으로 PENDING이 되지만 API 응답에서는 OPEN으로 반환됩니다.
                    AI 제안을 적용해 저장하는 경우 source를 "ai-assisted"로, aiEnhancementId를 해당 요청 ID로 채우세요.
                    완료(DONE)된 퀘스트에도 STAR를 수정할 수 있습니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "questId": "qst_05",
                                        "star": {
                                          "situation": "팀 프로젝트에서 API 응답이 3초 이상 걸리는 문제가 발생했다.",
                                          "task": "응답 시간을 500ms 이하로 줄여야 했다.",
                                          "action": "N+1 쿼리를 페치 조인으로 개선하고 Redis 캐시를 도입했다.",
                                          "result": "평균 응답 시간이 200ms로 감소했다."
                                        },
                                        "source": "manual",
                                        "status": "OPEN",
                                        "version": 4,
                                        "updatedAt": "2026-07-24T03:10:00Z"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "QUEST_LOCKED — 잠긴 퀘스트에 쓰기 시도",
                    content = @Content(mediaType = "application/json",
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
            @Valid @RequestBody SaveStarRequest request) {
        QuestDetailService.SaveStarResult starResult = questDetailService.saveStar(
                userId, IdCodec.decode(questId),
                request.star().situation(), request.star().task(),
                request.star().action(), request.star().result());
        StarInput s = request.star();
        // Read actual quest detail after save for updatedAt
        QuestDetailService.QuestDetailDto detail = questDetailService.getDetail(userId, IdCodec.decode(questId));
        return ResponseEntity.ok(ApiResponse.of(new SaveStarResponse(
                questId, new StarResponse(s.situation(), s.task(), s.action(), s.result()),
                request.source() != null ? request.source() : "manual",
                starResult.status(), starResult.version(), detail.updatedAt()
        )));
    }

    // ────────────────── PATCH /api/quests/{questId}/complete ──────────────────

    @Operation(
            summary = "퀘스트 완료 토글",
            description = """
                    퀘스트 완료 상태를 토글합니다.
                    ★ star 를 함께 보내면 STAR 저장과 완료가 한 트랜잭션에서 처리됩니다.
                    star 를 함께 보내면 PUT /star 를 따로 호출할 필요가 없습니다.
                    단 AI 보완 적용 기록(source·aiEnhancementId)은 PUT /star 로만 가능합니다.
                    star 를 생략하면 완료 토글만 수행합니다.
                    응답 내 radar 배열을 활용해 레이더 차트를 재조회 없이 즉시 갱신하세요.
                    unlockedQuestIds 목록에 있는 퀘스트에 잠금 해제 애니메이션을 적용하세요.
                    completed: false를 보내면 완료 취소(DONE → OPEN)가 처리됩니다.""",
            parameters = @Parameter(name = "Idempotency-Key", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                    description = "멱등성 키 (optional)", required = false,
                    schema = @Schema(type = "string"))
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료 처리 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "quest": {
                                          "questId": "qst_05",
                                          "status": "DONE",
                                          "completedAt": "2026-07-24T03:20:00Z",
                                          "version": 4
                                        },
                                        "characterChanges": {
                                          "completionRate": 84,
                                          "stage": 4,
                                          "stageLabel": "완성",
                                          "level": 5,
                                          "nextQuest": {
                                            "questId": "qst_06",
                                            "title": "테스트 코드를 작성한다"
                                          }
                                        },
                                        "unlockedQuestIds": ["qst_07"],
                                        "radar": [
                                          { "axisCode": "programming", "axisName": "프로그래밍 기초", "percent": 72 },
                                          { "axisCode": "cs", "axisName": "CS·자료구조", "percent": 80 },
                                          { "axisCode": "database", "axisName": "데이터입출력", "percent": 18 },
                                          { "axisCode": "server-api", "axisName": "서버·API", "percent": 67 },
                                          { "axisCode": "collaboration", "axisName": "협업·형상관리", "percent": 90 },
                                          { "axisCode": "deploy", "axisName": "배포·운영", "percent": 10 }
                                        ]
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "QUEST_LOCKED — 잠긴 퀘스트에 완료 시도",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "QUEST_LOCKED",
                                        "message": "선행 퀘스트를 먼저 완료해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PatchMapping("/{questId}/complete")
    public ResponseEntity<ApiResponse<CompleteResponse>> toggleComplete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CompleteRequest request) {
        QuestDetailService.StarDto starDto = request.star() != null
                ? new QuestDetailService.StarDto(request.star().situation(), request.star().task(),
                        request.star().action(), request.star().result())
                : null;
        QuestDetailService.ToggleResult result =
                questDetailService.toggleComplete(userId, IdCodec.decode(questId), request.completed(), starDto);

        CompletedQuestInfo questInfo = new CompletedQuestInfo(
                IdCodec.encode(result.questId(), "qst_"),
                result.newStatus().toApiName(),
                result.completedAt() != null ? result.completedAt().toString() : null,
                result.newVersion()
        );

        int completionRate = result.completionRate() != null ? result.completionRate().intValue() : 0;
        int stageNum = stageToNumber(result.stage());
        String stageLabel = result.stage() != null ? result.stage() : "시작";

        CharacterController.NextQuestResponse nextQuestResp = result.nextQuest() != null
                ? new CharacterController.NextQuestResponse(
                        IdCodec.encode(result.nextQuest().questId(), "qst_"),
                        result.nextQuest().title())
                : null;

        CharacterChanges characterChanges = new CharacterChanges(
                completionRate, stageNum, stageLabel, result.currentLevel(), nextQuestResp);

        List<String> unlockedIds = result.unlockedQuestIds().stream()
                .map(id -> IdCodec.encode(id, "qst_"))
                .toList();

        List<RadarEntry> radar = result.radar().stream()
                .map(r -> new RadarEntry(r.axisCode(), r.axisName(), r.percent().intValue()))
                .toList();

        return ResponseEntity.ok(ApiResponse.of(new CompleteResponse(questInfo, characterChanges, unlockedIds, radar)));
    }

    private static int stageToNumber(String stage) {
        if (stage == null) return 1;
        return switch (stage) {
            case "완성" -> 4;
            case "숙련" -> 3;
            case "성장" -> 2;
            default -> 1;
        };
    }

    // ────────────────── POST /api/quests/{questId}/ai-enhancements ──────────────────

    @Operation(
            summary = "STAR AI 보완 요청",
            description = """
                    화면 4-2(STAR 입력 탭)의 'AI 보완 요청' 버튼 탭 시 호출합니다.
                    STAR 원문을 AI에 전달해 보완 제안·항목별 피드백·자기소개서 초안을 받습니다.
                    서버는 원문을 직접 수정하지 않습니다. 사용자가 '적용'을 누르면 프론트가 textarea를 채운 뒤 PUT /star로 저장하세요.
                    202 수신 후 GET /api/ai-enhancements/{requestId}를 1~2초 간격으로 폴링하여 결과를 확인하세요.
                    서버가 60초 경과 시 FAILED(errorCode: AI_TIMEOUT)를 반환합니다.
                    클라이언트는 별도 타임아웃 없이 COMPLETED 또는 FAILED가 올 때까지 폴링하세요."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "비동기 접수",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "requestId": "aie_01J3ABC"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "QUEST_LOCKED",
                    content = @Content(mediaType = "application/json",
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
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "AI_RATE_LIMITED",
                                        "message": "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PostMapping("/{questId}/ai-enhancements")
    public ResponseEntity<ApiResponse<AiEnhancementAcceptedResponse>> requestAiEnhancement(
            @AuthenticationPrincipal Long userId,
            @PathVariable String questId,
            @Valid @RequestBody AiEnhancementRequest request) {
        UUID requestId = questDetailService.requestAiEnhancement(
                userId, IdCodec.decode(questId),
                request.star().situation(), request.star().task(),
                request.star().action(), request.star().result(),
                request.locale(), request.style());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(new AiEnhancementAcceptedResponse("aie_" + requestId)));
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
            @Schema(description = """
                    퀘스트 상태입니다. 프론트 렌더링 가이드:
                    ■ LOCKED(흰색/회색): 선행 퀘스트 미완료로 잠김. 클릭 불가, 자물쇠 아이콘 표시.
                    ■ OPEN(기본색): 수행 가능. STAR 작성 중인 퀘스트도 OPEN으로 표시됨. 클릭하면 STAR 입력 탭으로 이동.
                    ■ DONE(파란색): 완료된 퀘스트. 체크 아이콘 표시. STAR 수정은 가능.
                    ■ ALREADY_KNOWN(주황색): 자가진단에서 이미 보유한 역량(mastery ≥ 0.66). 접힌 상태로 표시하되,
                       펼쳐서 STAR 작성 가능. 완료율에 포함됨. STAR를 작성해도 DONE으로 바뀌지 않습니다(이미 완료 집계).""", example = "OPEN",
                    allowableValues = {"LOCKED", "OPEN", "DONE", "ALREADY_KNOWN"}) String status,
            @Schema(description = """
                    퀘스트 종류입니다. SKILL: 스킬 기반 퀘스트(NCS 능력단위 연계). \
                    ACTIVITY: 활동형 퀘스트(skill_code 없음, 실습·포트폴리오 중심). \
                    CUSTOM: 사용자 정의 퀘스트(삭제 가능, NCS 연계 없음).""", example = "ACTIVITY",
                    allowableValues = {"SKILL", "ACTIVITY", "CUSTOM"}) String source,
            @Schema(description = "레벨 내 순서", example = "1") int order,
            @Schema(description = "내부 버전 (참고용)", example = "0") long version,
            @Schema(description = "화면 4-2 '완료 기준' 박스에 표시합니다. CUSTOM 퀘스트는 null일 수 있으며, null이면 섹션을 숨기세요.", nullable = true, example = "네트워크·OS·DB 핵심 답안을 정리한다") String completionCriteria,
            @Schema(description = """
                    자가진단 수준에 맞춘 안내 문구입니다. 퀘스트 상세의 설명 영역에 표시하세요.
                    서술형을 입력한 사용자는 AI가 맥락을 반영해 다듬은 문구가 내려옵니다.
                    null 일 수 있습니다(활동형 퀘스트, 또는 guidance 가 없는 옛 프로필 버전).
                    null 이면 해당 영역을 숨기세요.""",
                    nullable = true,
                    example = "Git 형상관리이(가) 처음이라면 기본 개념부터 차근히 익혀보세요.") String guidance,
            @Schema(description = "화면 4-2 'NCS 능력단위 근거' 박스에 표시합니다. source가 CUSTOM이면 null이므로 섹션 자체를 숨기세요", nullable = true) NcsUnitResponse ncsUnit,
            @Schema(description = "화면 4-2 '추천 자격' 목록입니다. unit_type '필수' 우선, 가나다순 정렬. 빈 배열이면 '해당 없음'을 표시하세요") List<CertResponse> certifications,
            @Schema(description = "상한(기본 5개)을 초과해 잘린 자격 수. 0이면 전부 표시된 것. '외 N개'로 표기하세요", example = "3") int moreCertificationCount,
            @Schema(description = "화면 4-2 STAR 입력칸 4개의 초기값입니다. null이면 빈 값으로 초기화하세요", nullable = true) StarResponse star,
            @Schema(description = "STAR 출처. manual 또는 ai-assisted", nullable = true) String starSource,
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
            @Schema(description = "STAR 기록입니다. 각 필드는 trim 후 최대 2000자이며, 임시 저장이므로 빈 값도 허용합니다") StarInput star,
            @Schema(description = "저장 출처입니다. 직접 작성이면 manual, AI 제안 적용이면 ai-assisted를 전달하세요", example = "manual",
                    allowableValues = {"manual", "ai-assisted"}) String source,
            @Schema(description = "AI 보완을 적용한 경우 해당 요청 ID를 전달하세요. 직접 작성 시 null", nullable = true, example = "aie_01J3ABC") String aiEnhancementId
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
            @Schema(description = "저장된 STAR 기록") StarResponse star,
            @Schema(description = "저장 출처", example = "manual") String source,
            @Schema(description = """
                    저장 후 퀘스트 상태입니다. PENDING은 내부 전용이며 API에서는 OPEN으로 반환됩니다.
                    ■ OPEN: 수행 가능 (STAR 미작성 또는 작성 중)
                    ■ DONE 유지: 완료된 퀘스트의 STAR를 수정해도 상태는 바뀌지 않음.
                    ■ ALREADY_KNOWN 유지: 이미 보유 역량의 STAR를 작성해도 상태는 바뀌지 않음.""", example = "OPEN",
                    allowableValues = {"LOCKED", "OPEN", "DONE", "ALREADY_KNOWN"}) String status,
            @Schema(description = "저장 후의 내부 버전 (참고용)", example = "4") long version,
            @Schema(description = "수정 시각", example = "2026-07-24T03:10:00Z") String updatedAt
    ) {}

    @Schema(name = "CompleteRequest")
    record CompleteRequest(
            @Schema(description = "true면 완료 처리, false면 완료 취소(DONE → OPEN)입니다", example = "true")
            @NotNull Boolean completed,
            @Schema(description = "함께 저장할 STAR 내용입니다. 생략하면 완료 토글만 수행합니다. " +
                    "STAR 작성 후 완료를 한 번에 처리하려면 이 필드에 담아 보내세요.", nullable = true)
            @Valid StarInput star
    ) {}

    @Schema(name = "CompleteResponse")
    record CompleteResponse(
            @Schema(description = """
                    완료 처리된 퀘스트 정보입니다. quest.status로 UI 상태를 즉시 갱신하세요.""") CompletedQuestInfo quest,
            @Schema(description = """
                    완료에 따른 캐릭터 변화 정보입니다. completionRate로 진행바를 갱신하고, \
                    stage/stageLabel로 캐릭터 이미지({species}-{stage}.png)를 교체하세요. \
                    nextQuest가 있으면 '다음 퀘스트' 바로가기 버튼을 표시하세요.""") CharacterChanges characterChanges,
            @Schema(description = """
                    완료 직후 잠금 해제된 퀘스트 ID 목록입니다. 화면 4-1에서 해당 퀘스트에 \
                    잠금 해제 애니메이션을 적용하고 status를 LOCKED → OPEN으로 변경하세요. \
                    빈 배열이면 해제된 퀘스트가 없습니다.""") List<String> unlockedQuestIds,
            @Schema(description = """
                    역량 다각형(레이더) 갱신 데이터입니다. 항상 정확히 6개 축이 반환됩니다. \
                    이 값으로 레이더 차트를 재조회 없이 즉시 갱신하세요.""") List<RadarEntry> radar
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
            @Schema(description = "갱신된 완료율(%)입니다", example = "84") int completionRate,
            @Schema(description = "갱신된 성장 단계 숫자입니다. 1~4 범위이며 절대 감소하지 않습니다", example = "4") int stage,
            @Schema(description = "갱신된 성장 단계 라벨입니다. 시작·성장·숙련·완성 중 하나입니다", example = "완성") String stageLabel,
            @Schema(description = "현재 진행 중인 레벨입니다", example = "5") int level,
            @Schema(description = "다음 권장 퀘스트입니다. 모든 퀘스트를 완료했으면 null입니다", nullable = true)
            CharacterController.NextQuestResponse nextQuest
    ) {}

    @Schema(name = "RadarEntry")
    record RadarEntry(
            @Schema(description = "역량 축 코드입니다. enum이 아닌 자유 문자열이며 직무마다 다릅니다.", example = "programming") String axisCode,
            @Schema(description = "역량 축 이름입니다. 레이더 차트의 각 꼭짓점 레이블로 사용하세요.", example = "프로그래밍 기초") String axisName,
            @Schema(description = "해당 축의 완료율(%)입니다. 계산식: (DONE+ALREADY_KNOWN) / 전체 × 100. 항상 정확히 6개 축이 반환되므로 정육각형 레이더 차트로 렌더링하세요.", example = "72") int percent
    ) {}

    @Schema(name = "AiEnhancementRequest")
    record AiEnhancementRequest(
            @Valid @Schema(description = "AI 보완을 요청할 STAR 원문입니다. 각 필드 최대 2000자") StarInput star,
            @Schema(description = "결과물 언어 로케일입니다", example = "ko-KR") String locale,
            @Schema(description = "자기소개서 초안 문체 스타일입니다", example = "concise-professional") String style
    ) {}

    @Schema(name = "AiEnhancementAcceptedResponse")
    record AiEnhancementAcceptedResponse(
            @Schema(description = "AI 보완 요청 ID. 폴링에 사용", example = "aie_01J3ABC") String requestId
    ) {}
}
