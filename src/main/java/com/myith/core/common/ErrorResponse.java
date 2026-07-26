package com.myith.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 오류 응답입니다. 모든 오류는 이 스키마를 사용합니다.")
public record ErrorResponse(
        @Schema(description = "오류 상세입니다.")
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
    @Schema(description = "오류 상세 정보입니다.")
    public record ErrorDetail(
            @Schema(description = "오류 식별 코드입니다. 클라이언트 분기 처리에 사용합니다.", example = "QUEST_LOCKED")
            String code,
            @Schema(description = "사용자에게 그대로 노출 가능한 한국어 메시지입니다.", example = "선행 퀘스트를 먼저 완료해주세요.")
            String message,
            @Schema(description = "422 유효성 검사 실패 시 필드별 오류 맵입니다. 그 외에는 null입니다.",
                    example = "{\"nickname\": \"닉네임은 1자 이상이어야 합니다.\"}")
            Map<String, String> fieldErrors,
            @Schema(description = "로그 추적용 요청 ID입니다. 문의 시 함께 전달해 주세요.", example = "req_01J3ABC")
            String requestId
    ) {
    }
}
