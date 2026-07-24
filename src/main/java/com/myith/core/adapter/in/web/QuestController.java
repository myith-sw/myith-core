package com.myith.core.adapter.in.web;

import com.myith.core.application.quest.QuestDetailService;
import com.myith.core.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestDetailService questDetailService;

    public QuestController(QuestDetailService questDetailService) {
        this.questDetailService = questDetailService;
    }

    @GetMapping("/{questId}")
    public ResponseEntity<ApiResponse<QuestDetailService.QuestDetailDto>> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questId) {
        return ResponseEntity.ok(ApiResponse.success(questDetailService.getDetail(userId, questId)));
    }

    @PatchMapping("/{questId}/complete")
    public ResponseEntity<ApiResponse<Void>> toggleComplete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questId,
            @Valid @RequestBody CompleteRequest request) {
        questDetailService.toggleComplete(userId, questId, request.completed(), request.version());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{questId}/star")
    public ResponseEntity<ApiResponse<Void>> saveStar(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long questId,
            @RequestBody StarRequest request) {
        questDetailService.saveStar(userId, questId,
                request.situation(), request.task(), request.action(), request.result());
        return ResponseEntity.ok(ApiResponse.success());
    }

    record CompleteRequest(@NotNull Boolean completed, @NotNull Long version) {}
    record StarRequest(String situation, String task, String action, String result) {}
}
