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
                    Electron 상주 앱 전용입니다. 웹 프론트에서는 호출하지 않습니다.
                    앱 실행 중 일정 주기(예: 30초)마다 호출해 서버에 생존을 알립니다.

                    서버는 마지막 실제 서비스 활동 시각(last_active_at) 기준으로 48시간 미접속을 판정해 nudge: true로 응답합니다.
                    heartbeat 자체는 활동 시각을 갱신하지 않습니다. (갱신하면 48시간 판정이 동작하지 않습니다.)

                    nudge가 true이면 트레이 아이콘에 알림 배지를 표시하거나 복귀 유도 알림을 띄우세요.
                    characterState가 null이면 아직 캐릭터를 생성하지 않은 상태입니다. 트레이 아이콘을 기본 이미지로 표시하세요."""
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
            @Schema(description = "48시간 미접속 판정 결과입니다. true이면 트레이 알림 또는 복귀 유도 UI를 표시하세요.", example = "true")
            boolean nudge,
            @Schema(description = "캐릭터 상태입니다. 캐릭터를 아직 생성하지 않았으면 null입니다.")
            CharacterStateResponse characterState
    ) {}

    @Schema(name = "CharacterStateResponse")
    record CharacterStateResponse(
            @Schema(description = "캐릭터 종류입니다. 트레이 아이콘 이미지 경로를 {species}-{stage}.png 형태로 조합해 사용하세요.", example = "deokbaseu")
            String species,
            @Schema(description = "성장 단계 숫자입니다(1~4). species와 조합해 트레이 아이콘 이미지를 선택하세요.", example = "4")
            int stage,
            @Schema(description = "완료율(%)입니다. 트레이 툴팁 또는 진행률 표시에 사용하세요.", example = "80")
            int completionRate
    ) {}
}
