package com.myith.core.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Ops", description = "운영")
@RestController
public class HealthController {

    @Operation(
            summary = "헬스체크",
            description = """
                    서버 상태를 확인한다.
                    이 엔드포인트는 data 래퍼를 사용하지 않는 유일한 엔드포인트다.
                    모니터링 도구(AWS ALB, k8s probe 등) 호환을 위해 래퍼 없이 평문 JSON을 반환한다."""
    )
    @ApiResponse(responseCode = "200", description = "서버 정상",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "status": "ok",
                              "version": "0.1.0",
                              "time": "2026-07-24T03:00:00Z"
                            }""")))
    @SecurityRequirements
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("version", "0.1.0");
        body.put("time", Instant.now().toString());
        return ResponseEntity.ok(body);
    }
}
