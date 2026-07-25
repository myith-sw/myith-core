package com.myith.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 성공 응답 래퍼. 모든 성공 응답은 data로 감싼다. (GET /api/health 제외)")
public record ApiResponse<T>(
        @Schema(description = "응답 데이터")
        T data,
        @Schema(description = "페이지네이션 메타. 목록 조회 시에만 포함")
        Meta meta
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }

    /** @deprecated 하위 호환. of(data)를 사용한다. */
    @Deprecated
    public static <T> ApiResponse<T> success(T data) {
        return of(data);
    }

    /** @deprecated 하위 호환. of(null)를 사용한다. */
    @Deprecated
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(null, null);
    }
}
