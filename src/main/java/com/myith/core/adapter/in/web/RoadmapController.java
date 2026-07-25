package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestManageService;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.application.roadmap.RoadmapCreateService.*;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.domain.roadmap.Quest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Roadmap", description = "로드맵 생성·조회")
@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    private final RoadmapCreateService roadmapCreateService;
    private final RoadmapQueryService roadmapQueryService;
    private final QuestManageService questManageService;

    public RoadmapController(RoadmapCreateService roadmapCreateService,
                             RoadmapQueryService roadmapQueryService,
                             QuestManageService questManageService) {
        this.roadmapCreateService = roadmapCreateService;
        this.roadmapQueryService = roadmapQueryService;
        this.questManageService = questManageService;
    }

    // ────────────────── POST /api/roadmaps ──────────────────

    @Operation(
            summary = "로드맵 생성",
            description = """
                    narrative·repoUrl·fileKey는 전부 optional이다.
                    셋 중 하나라도 있으면 202(비동기 분석, generationState: ANALYZING),
                    없으면 200(즉시 조립, generationState: READY).
                    같은 직무의 기존 ACTIVE 로드맵이 있으면 자동으로 ARCHIVED 처리하고 새로 만든다. STAR 기록은 보존된다.
                    검증: nickname trim 후 1~20자 / jobCode는 available: true여야 함 / 모든 필수 문항 응답 필요.""",
            parameters = @Parameter(name = "Idempotency-Key", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
                    description = "멱등성 키 (optional)", required = false,
                    schema = @Schema(type = "string", example = "550e8400-e29b-41d4-a716-446655440000"))
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "즉시 조립 완료 (선택형만)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "즉시 조립", value = """
                                    {
                                      "data": {
                                        "roadmapId": "rmp_01J3ABC",
                                        "generationState": "READY"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "비동기 분석 접수 (서술형·repo·파일)",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "비동기 접수", value = """
                                    {
                                      "data": {
                                        "roadmapId": "rmp_01J3ABC",
                                        "generationState": "ANALYZING"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VALIDATION_ERROR",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "입력값이 올바르지 않습니다.",
                                        "fieldErrors": { "nickname": "닉네임은 1~20자여야 합니다." },
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoadmapResponse>> createRoadmap(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRoadmapRequest request) {
        CreateCommand cmd = new CreateCommand(
                request.jobCode(), request.profileVersion(),
                request.species(), request.nickname(),
                request.answers().stream()
                        .map(a -> new AnswerDto(a.skillCode(), levelToMastery(a.level())))
                        .toList(),
                request.narrative() != null
                        ? new NarrativeDto(request.narrative().experience(),
                        request.narrative().strength(), request.narrative().difficulty())
                        : null,
                request.repoUrl(), request.fileKey()
        );
        CreateResult result = roadmapCreateService.create(userId, cmd);
        HttpStatus status = result.async() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String genState = result.async() ? "ANALYZING" : "READY";
        return ResponseEntity.status(status)
                .body(ApiResponse.of(new CreateRoadmapResponse("rmp_" + result.roadmapId(), genState)));
    }

    private BigDecimal levelToMastery(String level) {
        return switch (level) {
            case "heard" -> new BigDecimal("0.33");
            case "tried" -> new BigDecimal("0.66");
            case "independent" -> BigDecimal.ONE;
            default -> BigDecimal.ZERO;
        };
    }

    // ────────────────── GET /api/roadmaps/{roadmapId} ──────────────────

    @Operation(
            summary = "로드맵 상세 조회",
            description = """
                    화면 4-1(로드맵 상세).
                    levels는 오름차순, quests는 order 오름차순으로 내린다.
                    레벨 개수는 직무마다 4~7개로 다르다. 구분선에는 "Lv.1"만 표시한다.
                    잠긴 퀘스트(LOCKED)도 목록에 포함한다.
                    version은 낙관적 락용이다. 프론트가 보관했다가 완료·순서변경 요청에 실어 보낸다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "roadmapId": "rmp_01J3ABC",
                                "generationState": "READY",
                                "roadmapStatus": "ACTIVE",
                                "jobCode": "server",
                                "jobName": "백엔드 개발자",
                                "tagline": "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.",
                                "character": {
                                  "characterId": "chr_01J3ABC",
                                  "species": "deokbaseu",
                                  "nickname": "견습 서버 개발자",
                                  "stage": 4,
                                  "stageLabel": "완성",
                                  "completionRate": 80
                                },
                                "levels": [
                                  {
                                    "level": 1,
                                    "quests": [
                                      {
                                        "questId": "qst_01",
                                        "title": "버전관리로 협업한다",
                                        "axisCode": "collaboration",
                                        "axisName": "협업·형상관리",
                                        "status": "DONE",
                                        "source": "SKILL",
                                        "order": 1,
                                        "version": 3
                                      },
                                      {
                                        "questId": "qst_02",
                                        "title": "언어 기초로 토이앱을 만든다",
                                        "axisCode": "programming",
                                        "axisName": "프로그래밍 기초",
                                        "status": "ALREADY_KNOWN",
                                        "source": "SKILL",
                                        "order": 2,
                                        "version": 1
                                      }
                                    ]
                                  }
                                ],
                                "updatedAt": "2026-07-24T03:00:00Z"
                              }
                            }""")))
    @GetMapping("/{roadmapId}")
    public ResponseEntity<ApiResponse<RoadmapDetailResponse>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId) {
        RoadmapQueryService.RoadmapDetailDto dto = roadmapQueryService.getDetail(userId, roadmapId);
        RoadmapDetailResponse response = new RoadmapDetailResponse(
                "rmp_" + dto.roadmapId(), dto.generationState(), "ACTIVE",
                "server", dto.jobName(), dto.tagline(),
                new RoadmapCharacterResponse(
                        "chr_01J3ABC",
                        dto.character().species(), dto.character().nickname(),
                        4, "완성",
                        dto.character().completionRate() != null ? dto.character().completionRate().intValue() : 0
                ),
                dto.levels().stream().map(l -> new RoadmapLevelResponse(
                        l.level(),
                        l.quests().stream().map(q -> new RoadmapQuestResponse(
                                "qst_" + q.questId(), q.title(),
                                "collaboration", q.axisName(),
                                q.status(), q.source(), 1, 0
                        )).toList()
                )).toList(),
                null
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ────────────────── POST /api/roadmaps/{roadmapId}/quests ──────────────────

    @Operation(
            summary = "사용자 정의 퀘스트 추가",
            description = "화면 4-1 퀘스트 추가. source는 자동으로 CUSTOM으로 설정된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "questId": "qst_99",
                                        "title": "사이드 프로젝트를 운영한다",
                                        "axisCode": "server-api",
                                        "axisName": "서버·API",
                                        "level": 3,
                                        "status": "OPEN",
                                        "source": "CUSTOM",
                                        "order": 4,
                                        "version": 0
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "VALIDATION_ERROR — title 1~80자, level은 해당 로드맵의 레벨 범위 내",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "입력값이 올바르지 않습니다.",
                                        "fieldErrors": { "title": "제목은 1~80자여야 합니다." },
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PostMapping("/{roadmapId}/quests")
    public ResponseEntity<ApiResponse<AddQuestResponse>> addQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @Valid @RequestBody AddQuestRequest request) {
        Quest quest = questManageService.addCustomQuest(userId, roadmapId,
                request.title(), request.axisCode(), request.level());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(new AddQuestResponse(
                        "qst_" + quest.getId(), quest.getTitle(),
                        quest.getAxisCode(), quest.getAxisCode(),
                        quest.getLevel(), "OPEN", "CUSTOM",
                        quest.getOrderInLevel(), quest.getVersion()
                )));
    }

    // ────────────────── PATCH /api/roadmaps/{roadmapId}/quests/order ──────────────────

    @Operation(
            summary = "퀘스트 순서 변경",
            description = "화면 4-1 드래그&드롭으로 퀘스트 순서를 변경한다. version은 낙관적 락용이다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공. 갱신된 전체 레벨·퀘스트 목록 반환",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "levels": [
                                          {
                                            "level": 1,
                                            "quests": [
                                              {
                                                "questId": "qst_02",
                                                "title": "언어 기초로 토이앱을 만든다",
                                                "axisCode": "programming",
                                                "axisName": "프로그래밍 기초",
                                                "status": "ALREADY_KNOWN",
                                                "source": "SKILL",
                                                "order": 1,
                                                "version": 1
                                              },
                                              {
                                                "questId": "qst_01",
                                                "title": "버전관리로 협업한다",
                                                "axisCode": "collaboration",
                                                "axisName": "협업·형상관리",
                                                "status": "DONE",
                                                "source": "SKILL",
                                                "order": 2,
                                                "version": 3
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "VERSION_CONFLICT — 낙관적 락 실패",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "VERSION_CONFLICT",
                                        "message": "다른 요청과 충돌했습니다. 새로고침 후 다시 시도해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @PatchMapping("/{roadmapId}/quests/order")
    public ResponseEntity<ApiResponse<ReorderResponse>> reorderQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @Valid @RequestBody ReorderRequest request) {
        questManageService.reorderQuest(userId, roadmapId,
                request.questId(), request.targetLevel(), request.targetIndex());
        // 재조회하여 갱신된 전체 목록 반환
        RoadmapQueryService.RoadmapDetailDto dto = roadmapQueryService.getDetail(userId, roadmapId);
        List<RoadmapLevelResponse> levels = dto.levels().stream().map(l -> new RoadmapLevelResponse(
                l.level(),
                l.quests().stream().map(q -> new RoadmapQuestResponse(
                        "qst_" + q.questId(), q.title(),
                        "collaboration", q.axisName(),
                        q.status(), q.source(), 1, 0
                )).toList()
        )).toList();
        return ResponseEntity.ok(ApiResponse.of(new ReorderResponse(levels)));
    }

    // ────────────────── DELETE /api/roadmaps/{roadmapId}/quests/{questId} ──────────────────

    @Operation(
            summary = "퀘스트 삭제",
            description = "source가 CUSTOM인 사용자 정의 퀘스트만 삭제할 수 있다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CUSTOM_QUEST_ONLY — 사용자 정의 퀘스트가 아닌데 삭제 시도",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "CUSTOM_QUEST_ONLY",
                                        "message": "사용자 정의 퀘스트만 삭제할 수 있습니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @DeleteMapping("/{roadmapId}/quests/{questId}")
    public ResponseEntity<Void> deleteQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @PathVariable Long questId) {
        questManageService.deleteQuest(userId, roadmapId, questId);
        return ResponseEntity.noContent().build();
    }

    // ────────────────── Request / Response DTOs ──────────────────

    @Schema(name = "CreateRoadmapRequest")
    record CreateRoadmapRequest(
            @Schema(description = "직무 코드. GET /api/jobs에서 available: true인 것만 가능", example = "server")
            @NotBlank String jobCode,
            @Schema(description = "GET /api/jobs/{jobCode}/diagnosis에서 받은 프로필 버전", example = "1")
            @NotNull Integer profileVersion,
            @Schema(description = "캐릭터 종류. 프론트 assets 22종 중 택 1. enum이 아닌 문자열", example = "deokbaseu")
            @NotBlank String species,
            @Schema(description = "캐릭터 닉네임. trim 후 1~20자", example = "견습 서버 개발자")
            @Size(min = 1, max = 20) String nickname,
            @Schema(description = "자가진단 응답 목록. 모든 필수 문항에 대해 응답해야 한다")
            @NotEmpty List<AnswerRequest> answers,
            @Schema(description = "서술형 입력. 하나라도 있으면 202 비동기 분석")
            NarrativeRequest narrative,
            @Schema(description = "GitHub 저장소 URL (optional)", example = "https://github.com/user/community")
            String repoUrl,
            @Schema(description = "POST /api/uploads/presign에서 받은 파일 키 (optional)", example = "portfolio/usr_01J3ABC/9f2c1d.pdf")
            String fileKey
    ) {}

    @Schema(name = "AnswerRequest")
    record AnswerRequest(
            @Schema(description = "스킬 코드", example = "git")
            @NotBlank String skillCode,
            @Schema(description = "자가진단 수준. unknown|heard|tried|independent", example = "tried",
                    allowableValues = {"unknown", "heard", "tried", "independent"})
            @NotBlank String level
    ) {}

    @Schema(name = "NarrativeRequest")
    record NarrativeRequest(
            @Schema(description = "경험 서술", example = "스프링으로 커뮤니티 CRUD를 만들어봤습니다.")
            String experience,
            @Schema(description = "강점 서술", example = "Java와 Spring이 가장 익숙합니다.")
            String strength,
            @Schema(description = "어려움 서술", example = "성능 최적화는 아직 해본 적이 없습니다.")
            String difficulty
    ) {}

    @Schema(name = "CreateRoadmapResponse")
    record CreateRoadmapResponse(
            @Schema(description = "생성된 로드맵 ID", example = "rmp_01J3ABC")
            String roadmapId,
            @Schema(description = "생성 상태. READY(즉시) / ANALYZING(비동기)", example = "READY",
                    allowableValues = {"PENDING", "ANALYZING", "READY", "FAILED"})
            String generationState
    ) {}

    @Schema(name = "RoadmapDetailResponse")
    record RoadmapDetailResponse(
            @Schema(description = "로드맵 ID", example = "rmp_01J3ABC")
            String roadmapId,
            @Schema(description = "생성 상태", example = "READY",
                    allowableValues = {"PENDING", "ANALYZING", "READY", "FAILED"})
            String generationState,
            @Schema(description = "로드맵 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "ARCHIVED"})
            String roadmapStatus,
            @Schema(description = "직무 코드", example = "server")
            String jobCode,
            @Schema(description = "화면 4-1 상단 직무명", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "화면 4-1 상단 직무 설명",
                    example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "캐릭터 정보")
            RoadmapCharacterResponse character,
            @Schema(description = "레벨별 퀘스트 목록. 레벨 개수는 직무마다 4~7개 가변")
            List<RoadmapLevelResponse> levels,
            @Schema(description = "최근 수정일", example = "2026-07-24T03:00:00Z")
            String updatedAt
    ) {}

    @Schema(name = "RoadmapCharacterResponse")
    record RoadmapCharacterResponse(
            @Schema(description = "캐릭터 ID", example = "chr_01J3ABC")
            String characterId,
            @Schema(description = "캐릭터 종류. 이미지 {species}-{stage}.png", example = "deokbaseu")
            String species,
            @Schema(description = "화면 4-1 상단 캐릭터 닉네임", example = "견습 서버 개발자")
            String nickname,
            @Schema(description = "캐릭터 이미지 파일명 숫자 (1~4)", example = "4")
            int stage,
            @Schema(description = "화면 4-1 캐릭터 옆 텍스트", example = "완성",
                    allowableValues = {"시작", "성장", "숙련", "완성"})
            String stageLabel,
            @Schema(description = "화면 4-1 상단 진행률 %", example = "80")
            int completionRate
    ) {}

    @Schema(name = "RoadmapLevelResponse")
    record RoadmapLevelResponse(
            @Schema(description = "화면 4-1 구분선 'Lv.1' 표기. 4~7 가변", example = "1")
            int level,
            @Schema(description = "해당 레벨의 퀘스트 목록 (order 오름차순)")
            List<RoadmapQuestResponse> quests
    ) {}

    @Schema(name = "RoadmapQuestResponse")
    record RoadmapQuestResponse(
            @Schema(description = "퀘스트 ID", example = "qst_01")
            String questId,
            @Schema(description = "퀘스트 제목", example = "버전관리로 협업한다")
            String title,
            @Schema(description = "역량 축 코드. enum이 아닌 자유 문자열", example = "collaboration")
            String axisCode,
            @Schema(description = "역량 축 이름", example = "협업·형상관리")
            String axisName,
            @Schema(description = "화면 4-1 카드 상태 (잠금 자물쇠/완료/이미 보유 뱃지)", example = "DONE",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"})
            String status,
            @Schema(description = "화면 4-1 CUSTOM이면 삭제 버튼 노출", example = "SKILL",
                    allowableValues = {"SKILL", "ACTIVITY", "CUSTOM"})
            String source,
            @Schema(description = "레벨 내 정렬 순서", example = "1")
            int order,
            @Schema(description = "화면 4-1·4-2 완료·순서변경 요청에 동봉하는 낙관적 락 버전", example = "3")
            long version
    ) {}

    @Schema(name = "AddQuestRequest")
    record AddQuestRequest(
            @Schema(description = "퀘스트 제목. 1~80자", example = "사이드 프로젝트를 운영한다")
            @NotBlank @Size(min = 1, max = 80) String title,
            @Schema(description = "역량 축 코드", example = "server-api")
            @NotBlank String axisCode,
            @Schema(description = "레벨. 해당 로드맵의 레벨 범위 내", example = "3")
            @NotNull Integer level
    ) {}

    @Schema(name = "AddQuestResponse")
    record AddQuestResponse(
            @Schema(description = "생성된 퀘스트 ID", example = "qst_99")
            String questId,
            @Schema(description = "퀘스트 제목", example = "사이드 프로젝트를 운영한다")
            String title,
            @Schema(description = "역량 축 코드", example = "server-api")
            String axisCode,
            @Schema(description = "역량 축 이름", example = "서버·API")
            String axisName,
            @Schema(description = "레벨", example = "3")
            int level,
            @Schema(description = "상태", example = "OPEN")
            String status,
            @Schema(description = "소스", example = "CUSTOM")
            String source,
            @Schema(description = "레벨 내 정렬 순서", example = "4")
            int order,
            @Schema(description = "낙관적 락 버전", example = "0")
            long version
    ) {}

    @Schema(name = "ReorderRequest")
    record ReorderRequest(
            @Schema(description = "이동할 퀘스트 ID (서버 내부 ID)", example = "2")
            @NotNull Long questId,
            @Schema(description = "이동 대상 레벨", example = "1")
            @NotNull Integer targetLevel,
            @Schema(description = "이동 대상 인덱스 (0부터)", example = "0")
            @NotNull Integer targetIndex,
            @Schema(description = "낙관적 락 버전", example = "3")
            @NotNull Long version
    ) {}

    @Schema(name = "ReorderResponse")
    record ReorderResponse(
            @Schema(description = "갱신된 전체 레벨·퀘스트 목록")
            List<RoadmapLevelResponse> levels
    ) {}
}
