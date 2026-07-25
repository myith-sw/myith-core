package com.myith.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "페이지네이션·재시도 메타데이터")
public record Meta(
        @Schema(description = "다음 페이지 커서. 마지막 페이지이면 null", example = "eyJpZCI6ImV4cF8wMSJ9")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        Boolean hasNext,
        @Schema(description = "AI 호출 제한 시 재시도까지 남은 초", example = "30")
        Integer retryAfterSeconds
) {
}
