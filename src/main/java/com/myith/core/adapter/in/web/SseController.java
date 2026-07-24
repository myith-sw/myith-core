package com.myith.core.adapter.in.web;

import com.myith.core.adapter.in.sse.SseRegistry;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {

    private final SseRegistry sseRegistry;

    public SseController(SseRegistry sseRegistry) {
        this.sseRegistry = sseRegistry;
    }

    @GetMapping(value = "/api/roadmaps/{roadmapId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@AuthenticationPrincipal Long userId,
                               @PathVariable Long roadmapId) {
        return sseRegistry.register(roadmapId);
    }
}
