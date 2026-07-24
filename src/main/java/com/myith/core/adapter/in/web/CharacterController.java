package com.myith.core.adapter.in.web;

import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final RoadmapQueryService roadmapQueryService;

    public CharacterController(RoadmapQueryService roadmapQueryService) {
        this.roadmapQueryService = roadmapQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<RoadmapQueryService.CharacterListDto>>>> getCharacters(
            @AuthenticationPrincipal Long userId) {
        List<RoadmapQueryService.CharacterListDto> characters = roadmapQueryService.getCharacters(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("characters", characters)));
    }
}
