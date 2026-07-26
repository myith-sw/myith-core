package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestManageService;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.application.roadmap.RoadmapCreateService.CreateCommand;
import com.myith.core.application.roadmap.RoadmapCreateService.CreateResult;
import com.myith.core.application.roadmap.RoadmapCreateService.AnswerDto;
import com.myith.core.application.roadmap.RoadmapCreateService.NarrativeDto;
import com.myith.core.application.roadmap.RoadmapCreateService.ExperienceDto;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.IdCodec;
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
                    화면 3(자가진단) 완료 후 로드맵 생성을 요청할 때 호출합니다.
                    narrative·experiences는 모두 optional입니다.
                    둘 중 하나라도 있으면 202(비동기 분석, generationState: ANALYZING)가 반환됩니다.
                    둘 다 없으면 200(즉시 조립, generationState: READY)이 반환됩니다.
                    202 응답을 받은 경우 GET /api/roadmaps/{roadmapId}/progress SSE를 구독해 생성 완료를 기다려야 합니다.
                    같은 직무의 기존 ACTIVE 로드맵이 있으면 자동으로 ARCHIVED 처리하고 새로 만듭니다. STAR 기록은 보존됩니다.
                    검증: nickname은 trim 후 1~20자 / jobCode는 available: true인 직무여야 합니다 / 모든 필수 문항에 응답이 필요합니다.""",
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
                        ? new NarrativeDto(request.narrative().strength(), request.narrative().difficulty())
                        : null,
                request.experiences() != null
                        ? request.experiences().stream()
                        .map(e -> new ExperienceDto(e.content(), e.repoUrl(), e.fileKey()))
                        .toList()
                        : null
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
                    화면 4-1(로드맵 상세) 진입 시 호출합니다.
                    levels는 오름차순, quests는 order 오름차순으로 내려옵니다.
                    레벨 개수는 직무마다 4~7개로 다릅니다. 구분선에는 "Lv.{level}" 형태로 표시하면 됩니다.
                    LOCKED 상태인 퀘스트도 목록에 포함됩니다. 자물쇠 UI로 표시하고 상세 진입을 막으세요.
                    ALREADY_KNOWN 퀘스트는 접힌 상태로 표시하되 STAR 기록 진입은 허용합니다.
                    version은 낙관적 락용 값입니다. 각 퀘스트의 version을 보관했다가 완료 토글·순서변경 요청에 그대로 포함해야 합니다.
                    generationState가 ANALYZING이면 아직 생성 중이므로 SSE로 완료를 기다린 후 이 API를 호출하세요."""
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
            @PathVariable String roadmapId) {
        RoadmapQueryService.RoadmapDetailDto dto = roadmapQueryService.getDetail(userId, IdCodec.decode(roadmapId));
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
            description = """
                    화면 4-1 '퀘스트 추가' 버튼 제출 시 호출합니다.
                    source는 자동으로 CUSTOM으로 설정됩니다.
                    생성된 퀘스트는 지정 레벨의 맨 끝에 추가됩니다.
                    axisCode는 GET /api/roadmaps/{roadmapId} 응답의 quests[].axisCode 중 하나를 사용하세요.
                    CUSTOM 퀘스트만 삭제(DELETE)할 수 있습니다."""
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
            @PathVariable String roadmapId,
            @Valid @RequestBody AddQuestRequest request) {
        Quest quest = questManageService.addCustomQuest(userId, IdCodec.decode(roadmapId),
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
            description = """
                    화면 4-1 드래그&드롭으로 퀘스트 순서를 변경할 때 호출합니다.
                    version은 낙관적 락용입니다. GET /api/roadmaps/{roadmapId} 응답의 quest.version 값을 그대로 보내야 합니다.
                    409 VERSION_CONFLICT가 반환되면 로드맵 상세를 다시 조회한 뒤 재시도하세요.
                    성공 시 갱신된 전체 레벨·퀘스트 목록을 반환하므로 별도 재조회 없이 UI를 업데이트할 수 있습니다."""
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
            @PathVariable String roadmapId,
            @Valid @RequestBody ReorderRequest request) {
        Long roadmapLongId = IdCodec.decode(roadmapId);
        questManageService.reorderQuest(userId, roadmapLongId,
                IdCodec.decode(request.questId()), request.targetLevel(), request.targetIndex());
        // 재조회하여 갱신된 전체 목록 반환
        RoadmapQueryService.RoadmapDetailDto dto = roadmapQueryService.getDetail(userId, roadmapLongId);
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
            description = """
                    화면 4-1에서 CUSTOM 퀘스트의 삭제 버튼을 눌렀을 때 호출합니다.
                    source가 CUSTOM인 퀘스트만 삭제할 수 있습니다. SKILL·ACTIVITY 퀘스트에 시도하면 403이 반환됩니다.
                    삭제 후 204를 받으면 해당 퀘스트를 목록에서 제거하세요."""
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
            @PathVariable String roadmapId,
            @PathVariable String questId) {
        questManageService.deleteQuest(userId, IdCodec.decode(roadmapId), IdCodec.decode(questId));
        return ResponseEntity.noContent().build();
    }

    // ────────────────── Request / Response DTOs ──────────────────

    @Schema(name = "CreateRoadmapRequest")
    record CreateRoadmapRequest(
            @Schema(description = "직무 코드입니다. GET /api/jobs에서 available: true인 항목의 jobCode만 허용됩니다.", example = "server")
            @NotBlank String jobCode,
            @Schema(description = "GET /api/jobs/{jobCode}/diagnosis 응답에서 받은 profileVersion을 그대로 전달합니다.", example = "1")
            @NotNull Integer profileVersion,
            @Schema(description = "캐릭터 종류입니다. 프론트 assets 22종 중 하나를 선택합니다. enum이 아닌 자유 문자열입니다.", example = "deokbaseu")
            @NotBlank String species,
            @Schema(description = "캐릭터 닉네임입니다. trim 후 1~20자이며 생략하면 null로 저장됩니다.", example = "견습 서버 개발자")
            @Size(min = 1, max = 20) String nickname,
            @Schema(description = "자가진단 응답 목록입니다. 모든 필수 문항에 대해 응답해야 합니다.")
            @NotEmpty List<AnswerRequest> answers,
            @Schema(description = "자기 평가 서술(강점·어려움)입니다. 이 필드나 experiences 중 하나라도 있으면 202 비동기 분석으로 처리됩니다.")
            NarrativeRequest narrative,
            @Schema(description = "프로젝트 경험 카드 목록입니다. 있으면 202 비동기 분석으로 처리됩니다. 카드별로 서술·링크·파일을 각각 전달합니다.")
            List<ExperienceRequest> experiences
    ) {}

    @Schema(name = "AnswerRequest")
    record AnswerRequest(
            @Schema(description = "스킬 코드입니다. GET /api/jobs/{jobCode}/diagnosis 응답의 questions[].skillCode 값을 사용합니다.", example = "git")
            @NotBlank String skillCode,
            @Schema(description = "자가진단 수준 ID입니다. GET /api/jobs/{jobCode}/diagnosis 응답의 levels[].id 값을 사용합니다.", example = "tried",
                    allowableValues = {"unknown", "heard", "tried", "independent"})
            @NotBlank String level
    ) {}

    @Schema(name = "NarrativeRequest")
    record NarrativeRequest(
            @Schema(description = "자신의 강점에 대한 자유 서술입니다. null 허용입니다.", example = "Java와 Spring이 가장 익숙합니다.")
            String strength,
            @Schema(description = "어려움을 느끼는 부분에 대한 자유 서술입니다. null 허용입니다.", example = "성능 최적화는 아직 해본 적이 없습니다.")
            String difficulty
    ) {}

    @Schema(name = "ExperienceRequest")
    record ExperienceRequest(
            @Schema(description = "경험 서술 텍스트입니다. null 허용이며 repoUrl·fileKey와 독립적으로 사용할 수 있습니다.", example = "스프링으로 커뮤니티 CRUD를 만들어봤습니다.")
            String content,
            @Schema(description = "GitHub 저장소 URL입니다. null 허용입니다.", example = "https://github.com/user/community")
            String repoUrl,
            @Schema(description = "POST /api/uploads/presign에서 받은 파일 키입니다. null 허용입니다.", example = "portfolio/usr_01J3ABC/9f2c1d.pdf")
            String fileKey
    ) {}

    @Schema(name = "CreateRoadmapResponse")
    record CreateRoadmapResponse(
            @Schema(description = "생성된 로드맵 ID입니다. 이후 조회·SSE 구독에 사용합니다.", example = "rmp_01J3ABC")
            String roadmapId,
            @Schema(description = "생성 상태입니다. READY이면 즉시 조회 가능합니다. ANALYZING이면 SSE(/api/roadmaps/{roadmapId}/progress)로 완료를 기다려야 합니다.", example = "READY",
                    allowableValues = {"PENDING", "ANALYZING", "READY", "FAILED"})
            String generationState
    ) {}

    @Schema(name = "RoadmapDetailResponse")
    record RoadmapDetailResponse(
            @Schema(description = "로드맵 ID입니다.", example = "rmp_01J3ABC")
            String roadmapId,
            @Schema(description = "생성 상태입니다. ANALYZING이면 아직 분석 중이므로 SSE로 완료를 기다려야 합니다.", example = "READY",
                    allowableValues = {"PENDING", "ANALYZING", "READY", "FAILED"})
            String generationState,
            @Schema(description = "로드맵 상태입니다. ARCHIVED이면 과거 로드맵으로 읽기 전용으로 취급합니다.", example = "ACTIVE", allowableValues = {"ACTIVE", "ARCHIVED"})
            String roadmapStatus,
            @Schema(description = "직무 코드입니다.", example = "server")
            String jobCode,
            @Schema(description = "화면 4-1 상단에 표시할 직무명입니다.", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "화면 4-1 상단에 표시할 직무 설명입니다.",
                    example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "캐릭터 정보입니다.")
            RoadmapCharacterResponse character,
            @Schema(description = "레벨별 퀘스트 목록입니다. 레벨 개수는 직무마다 4~7개로 가변입니다. 빈 레벨은 내려오지 않습니다.")
            List<RoadmapLevelResponse> levels,
            @Schema(description = "로드맵 최근 수정일(ISO 8601)입니다.", example = "2026-07-24T03:00:00Z")
            String updatedAt
    ) {}

    @Schema(name = "RoadmapCharacterResponse")
    record RoadmapCharacterResponse(
            @Schema(description = "캐릭터 ID입니다.", example = "chr_01J3ABC")
            String characterId,
            @Schema(description = "캐릭터 종류입니다. 이미지 경로를 {species}-{stage}.png 형태로 조합해 사용합니다.", example = "deokbaseu")
            String species,
            @Schema(description = "화면 4-1 상단에 표시할 캐릭터 닉네임입니다. null이면 표시를 생략합니다.", example = "견습 서버 개발자")
            String nickname,
            @Schema(description = "캐릭터 이미지 파일명에 사용하는 성장 단계 번호(1~4)입니다. {species}-{stage}.png로 조합합니다.", example = "4")
            int stage,
            @Schema(description = "화면 4-1 캐릭터 옆에 표시할 성장 단계 레이블입니다.", example = "완성",
                    allowableValues = {"시작", "성장", "숙련", "완성"})
            String stageLabel,
            @Schema(description = "화면 4-1 상단 진행률(%)입니다. 0~100 정수로 내려옵니다.", example = "80")
            int completionRate
    ) {}

    @Schema(name = "RoadmapLevelResponse")
    record RoadmapLevelResponse(
            @Schema(description = "레벨 번호입니다. 화면 4-1 구분선에 'Lv.{level}' 형태로 표시합니다. 4~7 가변입니다.", example = "1")
            int level,
            @Schema(description = "해당 레벨의 퀘스트 목록입니다. order 오름차순으로 정렬되어 있습니다.")
            List<RoadmapQuestResponse> quests
    ) {}

    @Schema(name = "RoadmapQuestResponse")
    record RoadmapQuestResponse(
            @Schema(description = "퀘스트 ID입니다. 퀘스트 상세 조회·완료 토글·순서변경에 사용합니다.", example = "qst_01")
            String questId,
            @Schema(description = "퀘스트 제목입니다.", example = "버전관리로 협업한다")
            String title,
            @Schema(description = "역량 축 코드입니다. enum이 아닌 자유 문자열입니다.", example = "collaboration")
            String axisCode,
            @Schema(description = "역량 축 이름입니다. 퀘스트 카드 태그나 필터 레이블로 사용합니다.", example = "협업·형상관리")
            String axisName,
            @Schema(description = "퀘스트 상태입니다. LOCKED는 자물쇠 UI로 표시하고 상세 진입을 막으세요. ALREADY_KNOWN은 접힌 상태로 표시합니다. DONE은 완료 뱃지를 표시합니다.", example = "DONE",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"})
            String status,
            @Schema(description = "퀘스트 출처입니다. CUSTOM이면 삭제 버튼을 노출합니다.", example = "SKILL",
                    allowableValues = {"SKILL", "ACTIVITY", "CUSTOM"})
            String source,
            @Schema(description = "레벨 내 정렬 순서입니다.", example = "1")
            int order,
            @Schema(description = "낙관적 락 버전입니다. 완료 토글(PATCH /api/quests/{id}/complete)·순서변경(PATCH /api/roadmaps/{id}/quests/order) 요청에 그대로 포함해야 합니다.", example = "3")
            long version
    ) {}

    @Schema(name = "AddQuestRequest")
    record AddQuestRequest(
            @Schema(description = "퀘스트 제목입니다. 1~80자여야 합니다.", example = "사이드 프로젝트를 운영한다")
            @NotBlank @Size(min = 1, max = 80) String title,
            @Schema(description = "역량 축 코드입니다. 해당 로드맵의 quests[].axisCode 중 하나를 사용해야 합니다.", example = "server-api")
            @NotBlank String axisCode,
            @Schema(description = "배치할 레벨입니다. 해당 로드맵의 레벨 범위 내 값이어야 합니다.", example = "3")
            @NotNull Integer level
    ) {}

    @Schema(name = "AddQuestResponse")
    record AddQuestResponse(
            @Schema(description = "생성된 퀘스트 ID입니다.", example = "qst_99")
            String questId,
            @Schema(description = "퀘스트 제목입니다.", example = "사이드 프로젝트를 운영한다")
            String title,
            @Schema(description = "역량 축 코드입니다.", example = "server-api")
            String axisCode,
            @Schema(description = "역량 축 이름입니다.", example = "서버·API")
            String axisName,
            @Schema(description = "배치된 레벨입니다.", example = "3")
            int level,
            @Schema(description = "초기 상태입니다. 생성 직후에는 항상 OPEN입니다.", example = "OPEN",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"})
            String status,
            @Schema(description = "퀘스트 출처입니다. 사용자 정의 퀘스트는 항상 CUSTOM입니다.", example = "CUSTOM")
            String source,
            @Schema(description = "레벨 내 정렬 순서입니다. 해당 레벨의 마지막 순서로 추가됩니다.", example = "4")
            int order,
            @Schema(description = "낙관적 락 버전입니다. 생성 직후에는 항상 0입니다.", example = "0")
            long version
    ) {}

    @Schema(name = "ReorderRequest")
    record ReorderRequest(
            @Schema(description = "이동할 퀘스트 ID입니다.", example = "qst_02")
            @NotBlank String questId,
            @Schema(description = "이동 대상 레벨입니다. 레벨 간 이동도 가능합니다.", example = "1")
            @NotNull Integer targetLevel,
            @Schema(description = "이동 대상 위치의 인덱스입니다(0부터 시작). 드래그 결과의 목적지 index를 전달합니다.", example = "0")
            @NotNull Integer targetIndex,
            @Schema(description = "낙관적 락 버전입니다. GET /api/roadmaps/{roadmapId} 응답의 해당 quest.version 값을 사용합니다.", example = "3")
            @NotNull Long version
    ) {}

    @Schema(name = "ReorderResponse")
    record ReorderResponse(
            @Schema(description = "갱신된 전체 레벨·퀘스트 목록입니다. 이 응답으로 UI를 직접 업데이트하면 됩니다(별도 재조회 불필요).")
            List<RoadmapLevelResponse> levels
    ) {}
}
