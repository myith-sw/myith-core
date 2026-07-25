# MYiTH Core — 프론트엔드 연동 가이드

## 1. Mock 서버 실행 — 백엔드 없이 개발 시작

```bash
npx @stoplight/prism-cli mock docs/openapi.yaml --port 4010
# → http://localhost:4010/api/... 로 예시 응답이 그대로 내려온다
```

프론트 `.env`:
```
VITE_API_BASE_URL=http://localhost:4010/api    # mock
# VITE_API_BASE_URL=http://localhost:8080/api  # 실제 백엔드로 전환 시 이 줄만 교체
```

## 2. 타입 자동 생성 — 손으로 인터페이스를 쓰지 않는다

```bash
npx openapi-typescript docs/openapi.yaml -o src/types/api.d.ts
```

스펙이 바뀌면 이 명령만 다시 돌리면 프론트 타입이 따라온다.
`axisCode`가 `string`이므로 직무가 늘어나도 타입이 깨지지 않는다.

## 3. 연동 순서

```
1. mock(4010)으로 전 화면 개발
2. 백엔드 엔드포인트가 하나씩 열리면 그것만 8080으로 전환
3. 전부 열리면 BASE_URL 한 줄만 교체
```

## 4. Swagger UI

백엔드가 돌고 있을 때:
```
http://localhost:8080/swagger-ui.html
```

## 5. 스펙 변경 시 규칙

- OpenAPI를 고치면 **반드시 `./gradlew generateOpenApiDocs`를 다시 돌려 커밋**한다.
- 필드를 **삭제하거나 이름을 바꾸면** 프론트에 먼저 알린다. 추가는 자유.
- 예시 값을 바꿀 때는 통일 예시 데이터를 함께 바꾼다(전 엔드포인트 동시).

## 6. 인증

```
Authorization: Bearer {accessToken}
```

- `POST /api/auth/google` → `accessToken`, `refreshToken` 발급
- 401 + `TOKEN_EXPIRED` → `POST /api/auth/refresh` 1회 → 원 요청 재시도
- 재차 401 → 로그인 화면

## 7. 공통 응답 형식

**성공:**
```json
{ "data": { ... } }
{ "data": [ ... ], "meta": { "nextCursor": "...", "hasNext": true } }
```

**오류:**
```json
{
  "error": {
    "code": "QUEST_LOCKED",
    "message": "선행 퀘스트를 먼저 완료해주세요.",
    "fieldErrors": null,
    "requestId": "req_01J3ABC"
  }
}
```

`message`는 사용자에게 그대로 보여줄 한국어 문장이다.

## 8. 예외: GET /api/health

이 엔드포인트만 `data` 래퍼 없이 평문 JSON을 반환한다 (모니터링 도구 호환).
