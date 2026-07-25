package com.myith.core.adapter.in.web;

import com.myith.core.adapter.in.sse.SseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Roadmap", description = "로드맵 생성·조회")
@RestController
public class SseController {

    private final SseRegistry sseRegistry;

    public SseController(SseRegistry sseRegistry) {
        this.sseRegistry = sseRegistry;
    }

    @Operation(
            summary = "로드맵 생성 진행률 (SSE)",
            description = """
                    POST /api/roadmaps에서 202를 받은 뒤 구독한다.

                    이벤트 3종:
                    - event: progress — data: { "step": "증거 분석", "percent": 60 }
                    - event: done — data: { "roadmapId": "rmp_01J3ABC" }
                    - event: error — data: { "code": "ANALYSIS_FAILED", "message": "분석에 실패했습니다." }

                    연결이 끊겨도 결과는 서버에 저장된다.
                    재접속 시 GET /api/roadmaps/{roadmapId}로 완성본을 조회할 수 있다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "SSE 스트림",
            content = @Content(mediaType = "text/event-stream",
                    examples = {
                            @ExampleObject(name = "progress", value = """
                                    event: progress
                                    data: { "step": "증거 분석", "percent": 60 }
                                    """),
                            @ExampleObject(name = "done", value = """
                                    event: done
                                    data: { "roadmapId": "rmp_01J3ABC" }
                                    """),
                            @ExampleObject(name = "error", value = """
                                    event: error
                                    data: { "code": "ANALYSIS_FAILED", "message": "분석에 실패했습니다." }
                                    """)
                    }))
    @GetMapping(value = "/api/roadmaps/{roadmapId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@AuthenticationPrincipal Long userId,
                               @PathVariable Long roadmapId) {
        return sseRegistry.register(roadmapId);
    }
}
