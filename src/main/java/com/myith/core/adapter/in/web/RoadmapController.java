package com.myith.core.adapter.in.web;

import com.myith.core.application.dashboard.DashboardQueryService;
import com.myith.core.application.quest.QuestManageService;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.application.roadmap.RoadmapCreateService.*;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import com.myith.core.domain.roadmap.Quest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    private final RoadmapCreateService roadmapCreateService;
    private final RoadmapQueryService roadmapQueryService;
    private final QuestManageService questManageService;
    private final DashboardQueryService dashboardQueryService;

    public RoadmapController(RoadmapCreateService roadmapCreateService,
                             RoadmapQueryService roadmapQueryService,
                             QuestManageService questManageService,
                             DashboardQueryService dashboardQueryService) {
        this.roadmapCreateService = roadmapCreateService;
        this.roadmapQueryService = roadmapQueryService;
        this.questManageService = questManageService;
        this.dashboardQueryService = dashboardQueryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createRoadmap(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRoadmapRequest request) {
        CreateCommand cmd = new CreateCommand(
                request.jobCode(), request.profileVersion(),
                request.species(), request.nickname(),
                request.answers().stream()
                        .map(a -> new AnswerDto(a.skillCode(), a.mastery()))
                        .toList(),
                request.narrative() != null
                        ? new NarrativeDto(request.narrative().experience(),
                        request.narrative().strength(), request.narrative().difficulty())
                        : null,
                request.repoUrl(), request.fileKey()
        );
        CreateResult result = roadmapCreateService.create(userId, cmd);
        HttpStatus status = result.async() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.success(Map.of("roadmapId", result.roadmapId())));
    }

    @GetMapping("/{roadmapId}")
    public ResponseEntity<ApiResponse<RoadmapQueryService.RoadmapDetailDto>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId) {
        return ResponseEntity.ok(ApiResponse.success(roadmapQueryService.getDetail(userId, roadmapId)));
    }

    @PatchMapping("/{roadmapId}/quests/order")
    public ResponseEntity<ApiResponse<Void>> reorderQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @Valid @RequestBody ReorderRequest request) {
        questManageService.reorderQuest(userId, roadmapId,
                request.questId(), request.targetLevel(), request.targetIndex());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{roadmapId}/quests")
    public ResponseEntity<ApiResponse<Map<String, Long>>> addQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @Valid @RequestBody AddQuestRequest request) {
        Quest quest = questManageService.addCustomQuest(userId, roadmapId,
                request.title(), request.axisCode(), request.level());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("questId", quest.getId())));
    }

    @DeleteMapping("/{roadmapId}/quests/{questId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuest(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId,
            @PathVariable Long questId) {
        questManageService.deleteQuest(userId, roadmapId, questId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{roadmapId}/dashboard")
    public ResponseEntity<ApiResponse<DashboardQueryService.DashboardDto>> getDashboard(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roadmapId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardQueryService.getDashboard(userId, roadmapId)));
    }

    // ===== Request DTOs =====

    record CreateRoadmapRequest(
            @NotBlank String jobCode, @NotNull Integer profileVersion,
            @NotBlank String species, String nickname,
            @NotEmpty List<AnswerRequest> answers,
            NarrativeRequest narrative, String repoUrl, String fileKey) {}

    record AnswerRequest(@NotBlank String skillCode, @NotNull BigDecimal mastery) {}
    record NarrativeRequest(String experience, String strength, String difficulty) {}

    record ReorderRequest(@NotNull Long questId, @NotNull Integer targetLevel, @NotNull Integer targetIndex) {}

    record AddQuestRequest(@NotBlank String title, @NotBlank String axisCode, @NotNull Integer level) {}
}
