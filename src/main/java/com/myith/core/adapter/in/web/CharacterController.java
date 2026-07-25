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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                    화면 1-1(알 선택, 캐릭터 없을 때)과 1-2(홈, 캐릭터 있을 때) 모두에 사용한다.
                    프론트는 응답의 species 목록으로 알 선택 화면에서 보유 종류를 제외한다.
                    nextQuest가 null이면 모든 퀘스트 완료 상태다.
                    사이드바 카드와 홈 화면 카드가 함께 쓴다.
                    status=archived로 아카이브된 로드맵을 조회한다(경험 카드 열람용)."""
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
        List<RoadmapQueryService.CharacterListDto> characters = roadmapQueryService.getCharacters(userId);
        List<CharacterResponse> response = characters.stream().map(c -> new CharacterResponse(
                "chr_" + c.characterId(),
                "rmp_" + c.roadmapId(),
                c.species(), c.nickname(),
                c.jobCode(), c.jobName(), c.tagline(),
                "ACTIVE",
                c.completionRate() != null ? c.completionRate().intValue() : 0,
                stageFromRate(c.completionRate() != null ? c.completionRate().intValue() : 0),
                stageLabelFromRate(c.completionRate() != null ? c.completionRate().intValue() : 0),
                c.level(),
                c.nextQuest() != null ? new NextQuestResponse("qst_" + c.nextQuest().questId(), c.nextQuest().title()) : null,
                null, null
        )).toList();
        return ResponseEntity.ok(ApiResponse.of(response));
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
            @Schema(description = "화면 1-1 보유 종류 제외 필터 / 캐릭터 이미지 {species}-{stage}.png", example = "deokbaseu")
            String species,
            @Schema(description = "화면 1-2 카드 큰 제목, 사이드바 첫 줄, 화면 4-1 상단", example = "견습 서버 개발자")
            String nickname,
            @Schema(description = "직무 코드", example = "server")
            String jobCode,
            @Schema(description = "화면 1-2 카드 작은 글씨, 화면 4-1 상단, 화면 5 캐릭터 정보", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "화면 2 직무 카드 설명문, 화면 4-1 상단",
                    example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "로드맵 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "ARCHIVED"})
            String roadmapStatus,
            @Schema(description = "화면 1-2 진행률 %, 화면 4-1 상단, 화면 5 진행률", example = "80")
            int completionRate,
            @Schema(description = "캐릭터 이미지 파일명 숫자. {species}-{stage}.png", example = "4")
            int stage,
            @Schema(description = "화면 1-2·4-1 캐릭터 옆 텍스트 (시작/성장/숙련/완성)", example = "완성",
                    allowableValues = {"시작", "성장", "숙련", "완성"})
            String stageLabel,
            @Schema(description = "화면 1-2 'Lv.4' 표기. 현재 진행 중인 퀘스트의 레벨", example = "4")
            int level,
            @Schema(description = "화면 1-2 카드 '다음 퀘스트' 줄. 모든 퀘스트 완료 시 null")
            NextQuestResponse nextQuest,
            @Schema(description = "생성일", example = "2026-07-20T02:00:00Z")
            String createdAt,
            @Schema(description = "최근 수정일", example = "2026-07-24T03:00:00Z")
            String updatedAt
    ) {}

    @Schema(name = "NextQuestResponse")
    record NextQuestResponse(
            @Schema(description = "퀘스트 ID", example = "qst_01J3ABC")
            String questId,
            @Schema(description = "퀘스트 제목", example = "REST API 서버를 구현한다")
            String title
    ) {}
}
