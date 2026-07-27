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
                                "nudgeType": "ABSENCE_48H",
                                "nudgeMessage": "이틀 동안 못 봤어요. 오늘 퀘스트 하나만 해볼까요?",
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
                result.nudgeType(),
                result.nudgeMessage(),
                result.characterState() != null
                        ? new CharacterStateResponse(
                        result.characterState().species(),
                        stageToNumber(result.characterState().stage()),
                        result.characterState().completionRate() != null
                                ? result.characterState().completionRate().intValue() : 0)
                        : null
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    private static int stageToNumber(String stage) {
        if (stage == null) return 1;
        return switch (stage) {
            case "완성" -> 4;
            case "숙련" -> 3;
            case "성장" -> 2;
            default -> 1;
        };
    }

    @Schema(name = "HeartbeatResponse")
    record HeartbeatResponse(
            @Schema(description = "48시간 미접속 판정 결과입니다. true이면 트레이 알림 또는 복귀 유도 UI를 표시하세요. 1회성으로 동작합니다: 48시간 미접속 후 첫 heartbeat에서만 true이며, 이후 heartbeat에서는 사용자가 서비스에 접속해 활동 시각이 갱신될 때까지 false가 반환됩니다.", example = "true")
            boolean nudge,
            @Schema(description = "넛지 종류. nudge=false이면 null", allowableValues = {"ABSENCE_48H"}, nullable = true)
            String nudgeType,
            @Schema(description = "화면에 표시할 문구. 서버가 소유하므로 클라이언트에서 하드코딩하지 마세요.", nullable = true)
            String nudgeMessage,
            @Schema(description = "캐릭터 상태입니다. 캐릭터를 아직 생성하지 않았으면 null입니다.", nullable = true)
            CharacterStateResponse characterState
    ) {}

    @Schema(name = "CharacterStateResponse")
    record CharacterStateResponse(
            @Schema(description = "캐릭터 종류입니다. 트레이 아이콘 이미지 경로를 {species}-{stage}.png 형태로 조합해 사용하세요.", example = "deokbaseu")
            String species,
            @Schema(description = "캐릭터 성장 단계입니다(1=시작, 2=성장, 3=숙련, 4=완성). 퀘스트 난이도 레벨(level)과는 별개의 축입니다. species와 조합해 트레이 아이콘 이미지({species}-{stage}.png)를 선택하세요.", example = "4")
            int stage,
            @Schema(description = "퀘스트 완료율(%)입니다. DONE + ALREADY_KNOWN 상태 퀘스트의 비율입니다. 트레이 툴팁 또는 진행률 표시에 사용하세요.", example = "80")
            int completionRate
    ) {}
}
