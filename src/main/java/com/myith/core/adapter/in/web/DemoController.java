package com.myith.core.adapter.in.web;

import com.myith.core.application.port.UserRepository;
import com.myith.core.application.presence.DemoNudgeRegistry;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Demo", description = "시연 전용 API — 프로덕션에서는 비활성")
@ConditionalOnProperty(name = "myith.demo.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final DemoNudgeRegistry demoNudgeRegistry;
    private final UserRepository userRepository;
    private final String demoToken;

    public DemoController(DemoNudgeRegistry demoNudgeRegistry,
                          UserRepository userRepository,
                          @Value("${myith.demo.token}") String demoToken) {
        this.demoNudgeRegistry = demoNudgeRegistry;
        this.userRepository = userRepository;
        this.demoToken = demoToken;
    }

    @Operation(summary = "데모 넛지 트리거",
            description = """
                    시연 시 특정 사용자에게 즉시 넛지를 발생시킵니다. 다음 heartbeat에서 nudge=true로 응답합니다.
                    쿨다운·1회성 판정을 우회하므로 연속 트리거가 가능합니다.

                    type 별 조건 (실제 판정 시):
                    - ANNOYING: 12h 미접속 + OPEN 퀘스트 3개 이상
                    - UPSET: 24h 미접속
                    - ABSENCE_48H: 48h 미접속""")
    @PostMapping("/nudge")
    public ResponseEntity<?> triggerNudge(
            @RequestHeader("X-Demo-Token") String token,
            @RequestParam Long userId,
            @Parameter(description = "넛지 종류. 생략 시 ABSENCE_48H")
            @RequestParam(required = false, defaultValue = "ABSENCE_48H") String type,
            @Parameter(description = "커스텀 문구. 생략 시 application.yml 기본 문구")
            @RequestParam(required = false) String message) {

        if (demoToken.isBlank() || !demoToken.equals(token)) {
            return ResponseEntity.status(403)
                    .body(Map.of("code", "FORBIDDEN", "message", "Invalid demo token"));
        }

        if (userRepository.findById(userId).isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("code", "NOT_FOUND", "message", "User not found: " + userId));
        }

        if (!DemoNudgeRegistry.isValidType(type)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_NUDGE_TYPE",
                            "message", "허용된 type: ANNOYING, UPSET, ABSENCE_48H"));
        }

        demoNudgeRegistry.queue(userId, type, message);
        log.warn("DEMO NUDGE triggered for userId={}, type={}", userId, type);

        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "queued", true,
                "nudgeType", type,
                "deliverWithinSeconds", 10
        )));
    }
}
