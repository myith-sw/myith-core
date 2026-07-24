package com.myith.core.adapter.in.web;

import com.myith.core.application.presence.HeartbeatService;
import com.myith.core.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<HeartbeatService.HeartbeatResult>> heartbeat(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(heartbeatService.heartbeat(userId)));
    }
}
