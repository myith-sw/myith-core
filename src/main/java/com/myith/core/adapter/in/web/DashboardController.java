package com.myith.core.adapter.in.web;

import com.myith.core.application.dashboard.DashboardQueryService;
import com.myith.core.application.export.ExportService;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.IdCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Dashboard", description = "대시보드·경험카드·내보내기")
@RestController
@RequestMapping("/api/roadmaps/{roadmapId}")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final ExportService exportService;
    private final RoadmapQueryService roadmapQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService,
                               ExportService exportService,
                               RoadmapQueryService roadmapQueryService) {
        this.dashboardQueryService = dashboardQueryService;
        this.exportService = exportService;
        this.roadmapQueryService = roadmapQueryService;
    }

    // ────────────────── GET /api/roadmaps/{roadmapId}/dashboard ──────────────────

    @Operation(
            summary = "대시보드 조회",
            description = """
                    화면 5(대시보드/신화 페이지)에서 호출합니다.
                    로드맵 상세 → 대시보드 탭 진입 시, 또는 퀘스트 완료 후 대시보드로 돌아올 때 호출하세요.

                    radar는 배열이며 항상 정확히 6개 축이 반환됩니다. 고정 키 객체로 파싱하지 말고 배열을 순회하세요.
                    percent는 단순 완료율 = (DONE+ALREADY_KNOWN)/전체 × 100입니다. 가중평균이 아닙니다.
                    레이더 차트는 정육각형으로 렌더링하면 됩니다.

                    skillTree는 레벨별로 퀘스트를 묶은 구조입니다. 각 퀘스트의 status에 따라 잠금/활성/완료 표시를 다르게 렌더링하세요.
                    experienceCards는 STAR가 작성된 퀘스트만 포함됩니다. 카드가 없으면 빈 배열([])이 반환됩니다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "character": {
                                  "nickname": "견습 서버 개발자",
                                  "jobName": "백엔드 개발자",
                                  "species": "deokbaseu",
                                  "stage": 4,
                                  "stageLabel": "완성",
                                  "completionRate": 80,
                                  "completedQuestCount": 7,
                                  "level": 4
                                },
                                "radar": [
                                  { "axisCode": "programming", "axisName": "프로그래밍 기초", "percent": 67 },
                                  { "axisCode": "cs", "axisName": "CS·자료구조", "percent": 80 },
                                  { "axisCode": "database", "axisName": "데이터입출력", "percent": 18 },
                                  { "axisCode": "server-api", "axisName": "서버·API", "percent": 67 },
                                  { "axisCode": "collaboration", "axisName": "협업·형상관리", "percent": 90 },
                                  { "axisCode": "deploy", "axisName": "배포·운영", "percent": 10 }
                                ],
                                "skillTree": [
                                  {
                                    "level": 1,
                                    "quests": [
                                      {
                                        "questId": "qst_01",
                                        "title": "버전관리로 협업한다",
                                        "axisCode": "collaboration",
                                        "axisName": "협업·형상관리",
                                        "status": "DONE"
                                      }
                                    ]
                                  }
                                ],
                                "experienceCards": [
                                  {
                                    "experienceId": "exp_01",
                                    "questId": "qst_02",
                                    "title": "언어 기초로 토이앱을 만든다",
                                    "axisCode": "programming",
                                    "axisName": "프로그래밍 기초",
                                    "ncsUnitName": "프로그래밍언어활용",
                                    "star": {
                                      "situation": "팀 프로젝트에서 API 응답이 3초 이상 걸리는 문제가 발생했다.",
                                      "task": "응답 시간을 500ms 이하로 줄여야 했다.",
                                      "action": "N+1 쿼리를 페치 조인으로 개선하고 Redis 캐시를 도입했다.",
                                      "result": "평균 응답 시간이 200ms로 감소했다."
                                    },
                                    "createdAt": "2026-07-22T05:00:00Z"
                                  }
                                ],
                                "updatedAt": "2026-07-24T03:00:00Z"
                              }
                            }""")))
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal Long userId,
            @PathVariable String roadmapId) {
        Long roadmapLongId = IdCodec.decode(roadmapId);
        DashboardQueryService.DashboardDto dto = dashboardQueryService.getDashboard(userId, roadmapLongId);

        // axisCode → axisName 매핑
        Map<String, String> axisNameMap = roadmapQueryService.getAxisNameMap(roadmapLongId);

        // 캐릭터 정보 조회
        RoadmapQueryService.RoadmapDetailDto detail = roadmapQueryService.getDetail(userId, roadmapLongId);
        int completionRate = dto.completionRate() != null ? dto.completionRate().intValue() : 0;
        int stageNum = detail.character() != null ? detail.character().stageNumber() : 1;
        String stageLabel = detail.character() != null ? detail.character().stageLabel() : "시작";
        String nickname = detail.character() != null ? detail.character().nickname() : null;
        String species = detail.character() != null ? detail.character().species() : null;

        // 완료 퀘스트 수 계산
        long completedQuestCount = dto.skillTree().stream()
                .flatMap(st -> st.quests().stream())
                .filter(q -> "DONE".equals(q.status()) || "ALREADY_KNOWN".equals(q.status()))
                .count();

        // 현재 레벨 계산 (OPEN 또는 DONE 상태의 퀘스트 중 최고 레벨)
        int currentLevel = dto.skillTree().stream()
                .filter(st -> st.quests().stream()
                        .anyMatch(q -> "OPEN".equals(q.status()) || "DONE".equals(q.status())))
                .mapToInt(DashboardQueryService.SkillTreeDto::level)
                .max().orElse(1);

        DashboardResponse response = new DashboardResponse(
                new DashboardCharacterResponse(
                        nickname, detail.jobName(), species,
                        stageNum, stageLabel,
                        completionRate, (int) completedQuestCount, currentLevel
                ),
                dto.radar().stream().map(r -> new QuestController.RadarEntry(
                        r.axisCode(), axisNameMap.getOrDefault(r.axisCode(), r.axisCode()),
                        r.percent() != null ? r.percent().intValue() : 0
                )).toList(),
                dto.skillTree().stream().map(st -> new SkillTreeLevelResponse(
                        st.level(),
                        st.quests().stream().map(q -> new SkillTreeQuestResponse(
                                "qst_" + q.questId(), q.title(),
                                q.axisCode(),
                                axisNameMap.getOrDefault(q.axisCode(), q.axisCode()),
                                q.status()
                        )).toList()
                )).toList(),
                dto.experienceCards().stream().map(ec -> new StarController.ExperienceCardResponse(
                        "exp_" + ec.questId(), "qst_" + ec.questId(), ec.questTitle(),
                        ec.axisCode(), axisNameMap.getOrDefault(ec.axisCode(), ec.axisCode()),
                        ec.ncsUnitName(),
                        new QuestController.StarResponse(ec.situation(), ec.task(), ec.action(), ec.result()),
                        null
                )).toList(),
                null
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ────────────────── GET /api/roadmaps/{roadmapId}/export ──────────────────

    @Operation(
            summary = "로드맵 내보내기",
            description = """
                    대시보드 하단 '내보내기' 버튼에서 호출합니다.

                    format=md: 자소서 생성용 프롬프트 문서를 다운로드합니다 (Content-Type: text/markdown; charset=utf-8).
                    format=pdf: 원본 STAR 열람용 PDF를 다운로드합니다 (Content-Type: application/pdf, 한글 폰트 임베딩).
                    대상: 해당 로드맵의 STAR가 작성된 경험 카드 전체입니다(완성도 무관).

                    응답 헤더 Content-Disposition에서 파일명을 꺼내 브라우저 다운로드로 처리하세요.
                    STAR 기록이 하나도 없으면 409(NO_EXPORTABLE_EXPERIENCE)가 반환됩니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "파일 다운로드",
                    content = {
                            @Content(mediaType = "text/markdown", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
                    },
                    headers = @io.swagger.v3.oas.annotations.headers.Header(
                            name = "Content-Disposition",
                            description = "파일명. UTF-8 인코딩",
                            schema = @Schema(type = "string", example = "attachment; filename*=UTF-8''myith-server-export.md")
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_EXPORT_FORMAT — format이 md/pdf가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "INVALID_EXPORT_FORMAT",
                                        "message": "지원하지 않는 내보내기 형식입니다. md 또는 pdf만 가능합니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "NO_EXPORTABLE_EXPERIENCE — 내보낼 STAR 기록이 없음",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "NO_EXPORTABLE_EXPERIENCE",
                                        "message": "내보낼 경험 기록이 없습니다. STAR를 먼저 작성해주세요.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Long userId,
            @PathVariable String roadmapId,
            @Parameter(description = "내보내기 형식. md(자소서 프롬프트) 또는 pdf(원본 STAR)",
                    schema = @Schema(allowableValues = {"md", "pdf"}, defaultValue = "md"))
            @RequestParam(defaultValue = "md") String format) {
        ExportService.ExportResult result = exportService.export(userId, IdCodec.decode(roadmapId), format);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''myith-server-export." + format)
                .header("Content-Type", result.contentType())
                .body(result.content());
    }

    // ── Response DTOs ──

    @Schema(name = "DashboardResponse")
    record DashboardResponse(
            @Schema(description = "캐릭터 정보입니다. 화면 상단 캐릭터 카드 렌더링에 사용합니다.") DashboardCharacterResponse character,
            @Schema(description = "역량 다각형(레이더) 데이터입니다. 항상 정확히 6개 축이 반환됩니다. 배열을 순회하여 정육각형 레이더 차트를 렌더링하세요.") List<QuestController.RadarEntry> radar,
            @Schema(description = "레벨별 퀘스트 목록입니다. 스킬 트리 UI를 렌더링할 때 사용합니다.") List<SkillTreeLevelResponse> skillTree,
            @Schema(description = "STAR가 작성된 경험 카드 목록입니다. STAR 기록이 없으면 빈 배열([])이 반환됩니다.") List<StarController.ExperienceCardResponse> experienceCards,
            @Schema(description = "스냅샷 최근 갱신 시각(ISO 8601 UTC)입니다.", example = "2026-07-24T03:00:00Z") String updatedAt
    ) {}

    @Schema(name = "DashboardCharacterResponse")
    record DashboardCharacterResponse(
            @Schema(description = "캐릭터 닉네임입니다. null이면 species 기반 기본 이름을 사용하세요.", nullable = true, example = "견습 서버 개발자") String nickname,
            @Schema(description = "직무명입니다.", example = "백엔드 개발자") String jobName,
            @Schema(description = "캐릭터 종류입니다. 이미지 경로 /characters/{species}-{stage}.png 형태로 조합하세요.", example = "deokbaseu") String species,
            @Schema(description = "성장 단계 숫자입니다(1~4). stage와 stageLabel을 함께 사용하세요.", example = "4") int stage,
            @Schema(description = "성장 단계 라벨입니다. 시작·성장·숙련·완성 중 하나입니다.", allowableValues = {"시작", "성장", "숙련", "완성"}, example = "완성") String stageLabel,
            @Schema(description = "완료율(%)입니다. DONE+ALREADY_KNOWN 기준입니다.", example = "80") int completionRate,
            @Schema(description = "완료한 퀘스트 수입니다.", example = "7") int completedQuestCount,
            @Schema(description = "현재 진행 중인 레벨입니다.", example = "4") int level
    ) {}

    @Schema(name = "SkillTreeLevelResponse")
    record SkillTreeLevelResponse(
            @Schema(description = "레벨 번호입니다.", example = "1") int level,
            @Schema(description = "해당 레벨의 퀘스트 목록입니다.") List<SkillTreeQuestResponse> quests
    ) {}

    @Schema(name = "SkillTreeQuestResponse")
    record SkillTreeQuestResponse(
            @Schema(description = "퀘스트 ID입니다.", example = "qst_01") String questId,
            @Schema(description = "퀘스트 제목입니다.", example = "버전관리로 협업한다") String title,
            @Schema(description = "역량 축 코드입니다.", example = "collaboration") String axisCode,
            @Schema(description = "역량 축 이름입니다.", example = "협업·형상관리") String axisName,
            @Schema(description = """
                    퀘스트 상태입니다. 프론트 렌더링 가이드:
                    ■ LOCKED(흰색/회색): 선행 퀘스트 미완료로 잠김. 클릭 불가, 자물쇠 아이콘 표시.
                    ■ OPEN(기본색): 수행 가능. 클릭하면 퀘스트 상세(GET /api/quests/{questId})로 이동.
                    ■ PENDING(기본색+진행표시): STAR를 작성했지만 완료 버튼을 누르지 않은 상태. 점선 테두리 등으로 구분.
                    ■ DONE(파란색): 완료된 퀘스트. 체크 아이콘 표시.
                    ■ ALREADY_KNOWN(주황색): 자가진단에서 이미 보유한 역량(mastery ≥ 0.66). 접힌 상태로 표시하되,
                       펼쳐서 STAR 작성 가능. 완료율에 포함됨. STAR를 작성해도 DONE으로 바뀌지 않습니다(이미 완료 집계).""",
                    example = "DONE",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"}) String status
    ) {}
}
