package com.myith.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 오류 응답. 모든 오류는 이 스키마를 사용한다.")
public record ErrorResponse(
        @Schema(description = "오류 상세")
        ErrorDetail error
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorDetail(code, message, null, null));
    }

    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(new ErrorDetail(code, message, null, requestId));
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fieldErrors, String requestId) {
        return new ErrorResponse(new ErrorDetail(code, message, fieldErrors, requestId));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "오류 상세 정보")
    public record ErrorDetail(
            @Schema(description = "오류 코드", example = "QUEST_LOCKED")
            String code,
            @Schema(description = "사용자에게 그대로 보여줄 한국어 메시지", example = "선행 퀘스트를 먼저 완료해주세요.")
            String message,
            @Schema(description = "422 필드 검증 실패 시 필드별 오류. 그 외엔 null",
                    example = "{\"nickname\": \"닉네임은 1자 이상이어야 합니다.\"}")
            Map<String, String> fieldErrors,
            @Schema(description = "로그 추적용 요청 ID", example = "req_01J3ABC")
            String requestId
    ) {
    }
}
