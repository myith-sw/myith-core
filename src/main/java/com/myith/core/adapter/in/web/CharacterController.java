package com.myith.core.adapter.in.web;

import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.myith.core.common.IdCodec;

import java.util.List;

@Tag(name = "Roadmap", description = "로드맵 생성·조회")
@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final RoadmapQueryService roadmapQueryService;

    public CharacterController(RoadmapQueryService roadmapQueryService) {
        this.roadmapQueryService = roadmapQueryService;
    }

    @Operation(
            summary = "캐릭터 목록 조회",
            description = """
                    로그인 직후 홈 진입 시, 그리고 사이드바를 열 때 호출합니다.
                    status 파라미터로 범위를 조절합니다(기본 active).
                    - active: 현재 진행 중인 로드맵 캐릭터 목록 (홈 화면 1-2, 사이드바 카드)
                    - archived: 완료·보관된 로드맵 캐릭터 목록 (경험 카드 열람용)
                    - all: active + archived 전체
                    응답 배열의 species 목록을 참고해 알 선택 화면(1-1)에서 이미 보유한 종류를 비활성화합니다.
                    nextQuest가 null이면 해당 캐릭터의 모든 퀘스트가 완료된 상태입니다.
                    캐릭터가 하나도 없으면 빈 배열([])이 반환되며, 이 경우 알 선택 화면(1-1)으로 이동합니다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": [
                                {
                                  "characterId": "chr_01J3ABC",
                                  "roadmapId": "rmp_01J3ABC",
                                  "species": "deokbaseu",
                                  "nickname": "견습 서버 개발자",
                                  "jobCode": "server",
                                  "jobName": "백엔드 개발자",
                                  "tagline": "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.",
                                  "roadmapStatus": "ACTIVE",
                                  "completionRate": 80,
                                  "stage": 4,
                                  "stageLabel": "완성",
                                  "level": 4,
                                  "nextQuest": {
                                    "questId": "qst_01J3ABC",
                                    "title": "REST API 서버를 구현한다"
                                  },
                                  "createdAt": "2026-07-20T02:00:00Z",
                                  "updatedAt": "2026-07-24T03:00:00Z"
                                }
                              ]
                            }""")))
    @GetMapping
    public ResponseEntity<ApiResponse<List<CharacterResponse>>> getCharacters(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "active | archived | all (기본 active)", example = "active",
                    schema = @Schema(allowableValues = {"active", "archived", "all"}, defaultValue = "active"))
            @RequestParam(defaultValue = "active") String status) {
        List<RoadmapQueryService.CharacterListDto> characters = roadmapQueryService.getCharacters(userId, status);
        List<CharacterResponse> response = characters.stream().map(c -> {
            int rate = c.completionRate() != null ? c.completionRate().intValue() : 0;
            // 스냅샷의 stage를 우선 사용하고, 없으면 completionRate 기반 계산
            int stageNum = c.stage() != null ? stageToNumber(c.stage()) : stageFromRate(rate);
            String stageLabel = c.stage() != null ? c.stage() : stageLabelFromRate(rate);
            return new CharacterResponse(
                "chr_" + c.characterId(),
                "rmp_" + c.roadmapId(),
                c.species(), c.nickname(),
                c.jobCode(), c.jobName(), c.tagline(),
                c.roadmapStatus(),
                rate, stageNum, stageLabel,
                c.level(),
                c.nextQuest() != null ? new NextQuestResponse("qst_" + c.nextQuest().questId(), c.nextQuest().title()) : null,
                c.createdAt(), c.updatedAt()
            );
        }).toList();
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ────────────────── DELETE /api/characters/{characterId} ──────────────────

    @Operation(
            summary = "캐릭터 삭제 (로드맵 보관 처리)",
            description = """
                    화면 1-2에서 캐릭터 삭제 버튼을 누를 때 호출합니다.
                    캐릭터와 연결된 로드맵이 ARCHIVED 상태로 전환됩니다.
                    STAR 기록은 보존되며, 캐릭터 목록에서 더 이상 표시되지 않습니다.
                    이미 ARCHIVED된 캐릭터를 다시 삭제하면 무시됩니다(멱등).
                    삭제 후 캐릭터 목록을 다시 조회하세요."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제(보관) 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "캐릭터를 찾을 수 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "NOT_FOUND",
                                        "message": "캐릭터를 찾을 수 없습니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "다른 사용자의 캐릭터",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "FORBIDDEN_RESOURCE",
                                        "message": "접근 권한이 없습니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @DeleteMapping("/{characterId}")
    public ResponseEntity<Void> deleteCharacter(
            @AuthenticationPrincipal Long userId,
            @PathVariable String characterId) {
        roadmapQueryService.archiveByCharacterId(userId, IdCodec.decode(characterId));
        return ResponseEntity.noContent().build();
    }

    private int stageToNumber(String stage) {
        return switch (stage) {
            case "완성" -> 4;
            case "숙련" -> 3;
            case "성장" -> 2;
            default -> 1;
        };
    }

    private int stageFromRate(int rate) {
        if (rate >= 80) return 4;
        if (rate >= 50) return 3;
        if (rate >= 20) return 2;
        return 1;
    }

    private String stageLabelFromRate(int rate) {
        if (rate >= 80) return "완성";
        if (rate >= 50) return "숙련";
        if (rate >= 20) return "성장";
        return "시작";
    }

    // ── Response DTOs ──

    @Schema(name = "CharacterResponse")
    record CharacterResponse(
            @Schema(description = "캐릭터 ID", example = "chr_01J3ABC")
            String characterId,
            @Schema(description = "로드맵 ID", example = "rmp_01J3ABC")
            String roadmapId,
            @Schema(description = "캐릭터 종류입니다. 알 선택 화면(1-1)에서 이미 보유한 종류를 비활성화할 때 사용합니다. 이미지 파일명 {species}-{stage}.png 조합에도 사용됩니다.", example = "deokbaseu")
            String species,
            @Schema(description = "캐릭터 닉네임입니다. 화면 1-2 카드 큰 제목, 사이드바 첫 줄, 화면 4-1 상단에 표시합니다. null이면 미설정 상태이므로 기본 텍스트(예: '이름 없음')를 표시합니다.", nullable = true, example = "견습 서버 개발자")
            String nickname,
            @Schema(description = "직무 코드입니다.", example = "server")
            String jobCode,
            @Schema(description = "직무 이름입니다. 화면 1-2 카드 작은 글씨, 화면 4-1 상단, 화면 5 캐릭터 정보에 표시합니다.", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "직무 설명 한 줄입니다. 화면 2 직무 카드 설명문, 화면 4-1 상단에 표시합니다.",
                    example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "로드맵 상태입니다. ACTIVE이면 진행 중, ARCHIVED이면 완료·보관된 로드맵입니다.", example = "ACTIVE", allowableValues = {"ACTIVE", "ARCHIVED"})
            String roadmapStatus,
            @Schema(description = "퀘스트 완료율(%)입니다. 0~100 정수입니다. DONE + ALREADY_KNOWN 상태의 퀘스트를 전체 퀘스트 수로 나눈 비율입니다. 화면 1-2 진행 바, 화면 4-1 상단, 화면 5 진행률에 사용합니다.", example = "80")
            int completionRate,
            @Schema(description = "캐릭터 성장 단계 숫자입니다(1~4). 퀘스트 난이도 레벨(level)과는 별개의 축입니다. 캐릭터 이미지 파일명 {species}-{stage}.png 조합에 사용합니다. completionRate 기준: 1=시작(0~19%), 2=성장(20~49%), 3=숙련(50~79%), 4=완성(80~100%).", example = "4")
            int stage,
            @Schema(description = "성장 단계 한글 레이블입니다. stage 숫자에 대응합니다(1=시작, 2=성장, 3=숙련, 4=완성). 화면 1-2·4-1 캐릭터 옆에 표시합니다.", example = "완성",
                    allowableValues = {"시작", "성장", "숙련", "완성"})
            String stageLabel,
            @Schema(description = "현재 진행 중인 퀘스트의 난이도 레벨입니다. 캐릭터 성장 단계(stage)와는 별개의 축입니다. 화면 1-2에서 'Lv.{level}' 형식으로 표시합니다.", example = "4")
            int level,
            @Schema(description = "다음 수행할 OPEN 상태 퀘스트 정보입니다. 가장 낮은 레벨 중 가장 낮은 순서의 OPEN 퀘스트가 선택됩니다. 화면 1-2 카드 '다음 퀘스트' 줄에 사용합니다. 모든 퀘스트가 완료된 경우 null이 반환되며, 완료 축하 메시지를 표시합니다.", nullable = true)
            NextQuestResponse nextQuest,
            @Schema(description = "캐릭터 생성 일시(ISO 8601 UTC)입니다.", nullable = true, example = "2026-07-20T02:00:00Z")
            String createdAt,
            @Schema(description = "마지막 수정 일시(ISO 8601 UTC)입니다.", nullable = true, example = "2026-07-24T03:00:00Z")
            String updatedAt
    ) {}

    @Schema(name = "NextQuestResponse")
    record NextQuestResponse(
            @Schema(description = "퀘스트 ID입니다. 'qst_' 접두사를 포함합니다. 퀘스트 상세 화면 진입 시 사용합니다.", example = "qst_01J3ABC")
            String questId,
            @Schema(description = "퀘스트 제목입니다. 화면 1-2 카드 '다음 퀘스트' 줄에 그대로 표시합니다.", example = "REST API 서버를 구현한다")
            String title
    ) {}
}
