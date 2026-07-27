package com.myith.core.adapter.in.web;

import com.myith.core.application.star.StarQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.Meta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Dashboard", description = "대시보드·경험카드·내보내기")
@RestController
@RequestMapping("/api/star")
public class StarController {

    private final StarQueryService starQueryService;

    public StarController(StarQueryService starQueryService) {
        this.starQueryService = starQueryService;
    }

    @Operation(
            summary = "경험 카드(STAR 기록) 목록 조회",
            description = """
                    화면 5(대시보드)의 경험 카드 목록을 조회합니다.
                    커서 기반 페이지네이션을 사용합니다(OFFSET 방식 미지원).
                    nextCursor가 null이거나 hasNext가 false이면 더 불러올 데이터가 없습니다.
                    axis 파라미터로 특정 역량 축만 필터링할 수 있습니다. 생략하면 전체를 반환합니다.
                    completeness: complete는 STAR 4칸 모두 입력된 카드만, partial은 일부만 입력된 카드만 반환합니다.
                    카드의 questId를 이용해 퀘스트 상세(GET /api/quests/{questId})로 이동할 수 있습니다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": [
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
                              "meta": {
                                "nextCursor": "eyJpZCI6ImV4cF8wMSJ9",
                                "hasNext": true
                              }
                            }""")))
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<ExperienceCardResponse>>> getRecords(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "커서", example = "eyJpZCI6ImV4cF8wMSJ9")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 100", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "축 코드로 필터. 생략 시 전체", example = "programming")
            @RequestParam(required = false) String axis,
            @Parameter(description = "완성도 필터입니다. all=전체(기본값), complete=STAR 4칸(situation·task·action·result) 모두 채워진 카드만, partial=일부만 채워진 카드만 반환합니다.",
                    schema = @Schema(allowableValues = {"all", "complete", "partial"}, defaultValue = "all"))
            @RequestParam(defaultValue = "all") String completeness,
            @Parameter(description = "star_record.tags 배열 필터", example = "리더십")
            @RequestParam(required = false) String tag) {
        String normalizedCompleteness = switch (completeness) {
            case "complete" -> "COMPLETE";
            case "partial" -> "PARTIAL";
            default -> null;  // "all" or unknown -> no filter
        };
        StarQueryService.CursorResult result = starQueryService.getRecords(
                userId, cursor != null ? Long.parseLong(cursor) : null, size, normalizedCompleteness);
        List<ExperienceCardResponse> records = result.records().stream().map(r ->
                new ExperienceCardResponse(
                        "exp_" + r.id(), "qst_" + r.questId(), null,
                        null, null, null,
                        new QuestController.StarResponse(r.situation(), r.task(), r.action(), r.result()),
                        null
                )).toList();
        Meta meta = new Meta(
                result.nextCursor() != null ? result.nextCursor().toString() : null,
                result.nextCursor() != null, null);
        return ResponseEntity.ok(ApiResponse.of(records, meta));
    }

    // ── Response DTO ──

    @Schema(name = "ExperienceCardResponse", description = "이 엔드포인트에서는 title, axisCode, axisName, ncsUnitName, createdAt이 null로 반환될 수 있습니다. 퀘스트 메타데이터가 보강된 경험 카드는 GET /api/roadmaps/{id}/dashboard 응답의 experienceCards를 사용하세요.")
    record ExperienceCardResponse(
            @Schema(description = "경험 카드 ID", example = "exp_01") String experienceId,
            @Schema(description = "퀘스트 ID입니다. 카드 클릭 시 GET /api/quests/{questId}로 퀘스트 상세 화면으로 이동하세요", example = "qst_02") String questId,
            @Schema(description = "퀘스트 제목입니다. 이 엔드포인트에서는 null일 수 있습니다. 보강된 값은 대시보드 응답을 사용하세요.", nullable = true, example = "언어 기초로 토이앱을 만든다") String title,
            @Schema(description = "역량 축 코드입니다. 이 엔드포인트에서는 null일 수 있습니다.", nullable = true, example = "programming") String axisCode,
            @Schema(description = "역량 축 이름입니다. 이 엔드포인트에서는 null일 수 있습니다.", nullable = true, example = "프로그래밍 기초") String axisName,
            @Schema(description = "NCS 능력단위명입니다. 퀘스트에 NCS 매핑이 없거나 이 엔드포인트에서는 null일 수 있습니다.", nullable = true, example = "프로그래밍언어활용") String ncsUnitName,
            @Schema(description = "STAR 기록입니다") QuestController.StarResponse star,
            @Schema(description = "STAR 최초 저장일입니다. 이 엔드포인트에서는 null일 수 있습니다.", nullable = true, example = "2026-07-22T05:00:00Z") String createdAt
    ) {}
}
