package com.myith.core.adapter.in.web;

import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.common.IdCodec;
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
                    비동기 경로(POST /api/roadmaps → 202)에서만 필요합니다. 200 응답이면 이 API를 호출하지 않아도 됩니다.
                    202를 받은 직후 구독을 시작하고 로딩 화면을 표시하세요.

                    이벤트 3종:
                    - event: progress — 진행 상황 업데이트입니다. data: { "step": "증거 분석", "percent": 60 }
                      percent는 0~100 정수입니다. 로딩 바 업데이트에 사용하세요.
                    - event: done — 생성 완료입니다. data: { "roadmapId": "rmp_01J3ABC" }
                      수신 즉시 GET /api/roadmaps/{roadmapId}를 호출해 상세 화면으로 이동하세요.
                    - event: error — 생성 실패입니다. data: { "code": "ANALYSIS_FAILED", "message": "분석에 실패했습니다." }
                      에러 화면을 표시하고 재시도 버튼을 제공하세요.

                    연결이 끊겨도 결과는 서버에 저장됩니다.
                    재접속 시 GET /api/roadmaps/{roadmapId}의 generationState 필드로 현재 상태를 확인하세요.
                    generationState가 DONE이면 이미 완료된 상태입니다. SSE를 구독하지 않고 상세 화면으로 바로 이동하세요."""
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
                               @PathVariable String roadmapId) {
        return sseRegistry.register(IdCodec.decode(roadmapId));
    }
}
