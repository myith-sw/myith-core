package com.myith.core.adapter.in.web;

import com.myith.core.application.dashboard.DashboardQueryService;
import com.myith.core.application.export.ExportService;
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

@Tag(name = "Dashboard", description = "대시보드·경험카드·내보내기")
@RestController
@RequestMapping("/api/roadmaps/{roadmapId}")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final ExportService exportService;

    public DashboardController(DashboardQueryService dashboardQueryService, ExportService exportService) {
        this.dashboardQueryService = dashboardQueryService;
        this.exportService = exportService;
    }

    // ────────────────── GET /api/roadmaps/{roadmapId}/dashboard ──────────────────

    @Operation(
            summary = "대시보드 조회",
            description = """
                    화면 5(대시보드/신화 페이지).
                    radar는 배열이다. 축 개수가 직무마다 다르므로 고정 키 객체로 만들지 않는다.
                    percent는 단순 완료율 = (DONE+ALREADY_KNOWN)/전체 × 100이다. 가중평균이 아니다."""
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
        DashboardQueryService.DashboardDto dto = dashboardQueryService.getDashboard(userId, IdCodec.decode(roadmapId));
        DashboardResponse response = new DashboardResponse(
                new DashboardCharacterResponse(
                        "견습 서버 개발자", "백엔드 개발자", "deokbaseu",
                        4, "완성",
                        dto.completionRate() != null ? dto.completionRate().intValue() : 0,
                        7, 4
                ),
                dto.radar().stream().map(r -> new QuestController.RadarEntry(
                        r.axisCode(), r.axisCode(),
                        r.percent() != null ? r.percent().intValue() : 0
                )).toList(),
                dto.skillTree().stream().map(st -> new SkillTreeLevelResponse(
                        st.level(),
                        st.quests().stream().map(q -> new SkillTreeQuestResponse(
                                "qst_" + q.questId(), q.title(), "collaboration", q.axisName(), q.status()
                        )).toList()
                )).toList(),
                dto.experienceCards().stream().map(ec -> new StarController.ExperienceCardResponse(
                        "exp_01", "qst_01", ec.questTitle(),
                        "programming", ec.axisName(), ec.ncsUnitName(),
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
                    format=md: 자소서 생성용 프롬프트 문서 (Content-Type: text/markdown; charset=utf-8).
                    format=pdf: 원본 STAR 열람용 PDF (Content-Type: application/pdf, 한글 폰트 임베딩).
                    대상: 해당 로드맵의 star IS NOT NULL인 경험 카드 전체(완성도 무관)."""
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
            @Schema(description = "화면 5 캐릭터 정보") DashboardCharacterResponse character,
            @Schema(description = "화면 5 역량 다각형(레이더). 배열이다. 축 개수는 직무마다 4~7개 가변") List<QuestController.RadarEntry> radar,
            @Schema(description = "화면 5 스킬 트리") List<SkillTreeLevelResponse> skillTree,
            @Schema(description = "화면 5 경험 카드 목록") List<StarController.ExperienceCardResponse> experienceCards,
            @Schema(description = "최근 갱신 시각", example = "2026-07-24T03:00:00Z") String updatedAt
    ) {}

    @Schema(name = "DashboardCharacterResponse")
    record DashboardCharacterResponse(
            @Schema(description = "화면 5 캐릭터 닉네임", example = "견습 서버 개발자") String nickname,
            @Schema(description = "화면 5 직무명", example = "백엔드 개발자") String jobName,
            @Schema(description = "캐릭터 종류", example = "deokbaseu") String species,
            @Schema(description = "성장 단계 숫자", example = "4") int stage,
            @Schema(description = "성장 단계 라벨", example = "완성") String stageLabel,
            @Schema(description = "완료율 %", example = "80") int completionRate,
            @Schema(description = "완료한 퀘스트 수", example = "7") int completedQuestCount,
            @Schema(description = "현재 진행 중인 레벨", example = "4") int level
    ) {}

    @Schema(name = "SkillTreeLevelResponse")
    record SkillTreeLevelResponse(
            @Schema(description = "레벨", example = "1") int level,
            @Schema(description = "해당 레벨 퀘스트 목록") List<SkillTreeQuestResponse> quests
    ) {}

    @Schema(name = "SkillTreeQuestResponse")
    record SkillTreeQuestResponse(
            @Schema(description = "퀘스트 ID", example = "qst_01") String questId,
            @Schema(description = "퀘스트 제목", example = "버전관리로 협업한다") String title,
            @Schema(description = "역량 축 코드", example = "collaboration") String axisCode,
            @Schema(description = "역량 축 이름", example = "협업·형상관리") String axisName,
            @Schema(description = "상태", example = "DONE",
                    allowableValues = {"LOCKED", "OPEN", "PENDING", "DONE", "ALREADY_KNOWN"}) String status
    ) {}
}
