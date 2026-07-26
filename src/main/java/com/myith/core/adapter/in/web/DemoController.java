package com.myith.core.adapter.in.web;

import com.myith.core.application.port.UserRepository;
import com.myith.core.application.presence.DemoNudgeRegistry;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "데모 넛지 트리거", description = "시연 시 특정 사용자에게 즉시 넛지를 발생시킵니다. 다음 heartbeat에서 nudge=true로 응답합니다.")
    @PostMapping("/nudge")
    public ResponseEntity<?> triggerNudge(
            @RequestHeader("X-Demo-Token") String token,
            @RequestParam Long userId) {

        if (demoToken.isBlank() || !demoToken.equals(token)) {
            return ResponseEntity.status(403)
                    .body(Map.of("code", "FORBIDDEN", "message", "Invalid demo token"));
        }

        if (userRepository.findById(userId).isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("code", "NOT_FOUND", "message", "User not found: " + userId));
        }

        demoNudgeRegistry.queue(userId);
        log.warn("DEMO NUDGE triggered for userId={}", userId);

        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "queued", true,
                "deliverWithinSeconds", 10
        )));
    }
}
