package com.myith.core.adapter.in.web;

import com.myith.core.application.star.StarQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.common.Meta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
            description = "화면 5(대시보드) 경험 카드 목록. 커서 기반 페이지네이션(OFFSET 금지)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<ExperienceCardResponse>>> getRecords(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "커서", example = "eyJpZCI6ImV4cF8wMSJ9")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 기본 20, 최대 100", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "축 코드로 필터. 생략 시 전체", example = "programming")
            @RequestParam(required = false) String axis,
            @Parameter(description = "완성도 필터. all | complete | partial (기본 all). complete = STAR 4칸 모두 채워짐",
                    schema = @Schema(allowableValues = {"all", "complete", "partial"}, defaultValue = "all"))
            @RequestParam(defaultValue = "all") String completeness,
            @Parameter(description = "star_record.tags 배열 필터", example = "리더십")
            @RequestParam(required = false) String tag) {
        StarQueryService.CursorResult result = starQueryService.getRecords(
                userId, cursor != null ? Long.parseLong(cursor) : null, size, completeness);
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

    @Schema(name = "ExperienceCardResponse")
    record ExperienceCardResponse(
            @Schema(description = "경험 카드 ID", example = "exp_01") String experienceId,
            @Schema(description = "퀘스트 ID. 카드 클릭 시 퀘스트 상세로 이동", example = "qst_02") String questId,
            @Schema(description = "퀘스트 제목", example = "언어 기초로 토이앱을 만든다") String title,
            @Schema(description = "역량 축 코드", example = "programming") String axisCode,
            @Schema(description = "역량 축 이름", example = "프로그래밍 기초") String axisName,
            @Schema(description = "NCS 능력단위명. 퀘스트의 NCS 능력단위명 재사용", example = "프로그래밍언어활용") String ncsUnitName,
            @Schema(description = "STAR 기록") QuestController.StarResponse star,
            @Schema(description = "생성일", example = "2026-07-22T05:00:00Z") String createdAt
    ) {}
}
