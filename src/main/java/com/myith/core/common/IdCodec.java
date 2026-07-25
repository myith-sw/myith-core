package com.myith.core.common;

/**
 * 접두사 ID 인코딩·디코딩 유틸.
 * DB PK(Long) ↔ 응답/경로 문자열 ID (예: "rmp_1", "qst_42") 변환을 담당한다.
 *
 * 접두사 규칙:
 *   usr_ · chr_ · rmp_ · qst_ · exp_ · aie_ · req_
 */
public final class IdCodec {

    private IdCodec() {}

    /** "rmp_42" → 42L. 접두사나 파싱에 실패하면 IllegalArgumentException. */
    public static Long decode(String prefixedId) {
        if (prefixedId == null) {
            throw new IllegalArgumentException("ID는 null일 수 없습니다.");
        }
        int underscore = prefixedId.indexOf('_');
        if (underscore < 0) {
            throw new IllegalArgumentException("잘못된 ID 형식입니다: " + prefixedId);
        }
        try {
            return Long.parseLong(prefixedId.substring(underscore + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("파싱할 수 없는 ID입니다: " + prefixedId);
        }
    }

    /** 42L + "rmp_" → "rmp_42" */
    public static String encode(Long id, String prefix) {
        return prefix + id;
    }
}
