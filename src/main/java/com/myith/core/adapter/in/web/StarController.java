package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestDetailService;
import com.myith.core.application.star.StarQueryService;
import com.myith.core.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/star")
public class StarController {

    private final StarQueryService starQueryService;
    private final QuestDetailService questDetailService;

    public StarController(StarQueryService starQueryService, QuestDetailService questDetailService) {
        this.starQueryService = starQueryService;
        this.questDetailService = questDetailService;
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<StarQueryService.CursorResult>> getRecords(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String completeness) {
        return ResponseEntity.ok(ApiResponse.success(
                starQueryService.getRecords(userId, cursor, size, completeness)));
    }

    @PostMapping("/{starRecordId}/feedback")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> requestFeedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long starRecordId) {
        UUID requestId = questDetailService.requestStarFeedback(userId, starRecordId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(Map.of("requestId", requestId)));
    }
}
