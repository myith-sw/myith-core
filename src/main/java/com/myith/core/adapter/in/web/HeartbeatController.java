package com.myith.core.adapter.in.web;

import com.myith.core.application.presence.HeartbeatService;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Desktop", description = "데스크톱 앱 연동")
@RestController
@RequestMapping("/api")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @Operation(
            summary = "하트비트",
            description = """
                    Electron 상주 앱 전용이다. 웹 프론트는 호출하지 않는다.
                    서버는 마지막 실제 서비스 활동 시각 기준으로 48시간 미접속을 판정해 nudge: true로 응답한다.
                    heartbeat 자체는 활동 시각을 갱신하지 않는다(갱신하면 48시간이 영원히 지나지 않는다).
                    캐릭터가 없으면 characterState: null."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "응답",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "nudge": true,
                                "characterState": {
                                  "species": "deokbaseu",
                                  "stage": 4,
                                  "completionRate": 80
                                }
                              }
                            }""")))
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatResponse>> heartbeat(
            @AuthenticationPrincipal Long userId) {
        HeartbeatService.HeartbeatResult result = heartbeatService.heartbeat(userId);
        HeartbeatResponse response = new HeartbeatResponse(
                result.nudge(),
                result.characterState() != null
                        ? new CharacterStateResponse(
                        result.characterState().species(),
                        4,
                        result.characterState().completionRate() != null
                                ? result.characterState().completionRate().intValue() : 0)
                        : null
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @Schema(name = "HeartbeatResponse")
    record HeartbeatResponse(
            @Schema(description = "48시간 미접속 판정 결과. true면 앱이 nudge UI를 표시한다", example = "true")
            boolean nudge,
            @Schema(description = "캐릭터 상태. 캐릭터가 없으면 null")
            CharacterStateResponse characterState
    ) {}

    @Schema(name = "CharacterStateResponse")
    record CharacterStateResponse(
            @Schema(description = "캐릭터 종류. 앱 트레이 아이콘용", example = "deokbaseu")
            String species,
            @Schema(description = "성장 단계 숫자. 앱 트레이 이미지 {species}-{stage}.png", example = "4")
            int stage,
            @Schema(description = "완료율. 앱 트레이 진행률 표시", example = "80")
            int completionRate
    ) {}
}
