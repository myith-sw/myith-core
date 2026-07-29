## [2026-07-29] Electron 로그인 — GOOGLE_DESKTOP_CLIENT_ID 환경변수 추가

**인프라 일인 이유:** 환경변수 주입은 myith-infra 소유(deploy.sh, docker-compose)

**Core 쪽 계약:**
- `application.yml`에 `google.desktop-client-id: ${GOOGLE_DESKTOP_CLIENT_ID:}` 추가
- 빈 문자열이면 웹 Client ID만으로 동작 (기존 동작 유지)
- Google Cloud Console에서 Desktop application 타입 OAuth Client를 생성해야 함

**myith-infra 수정 필요 3곳:**
1. `deploy.sh` — `get()` 승계 목록에 `GOOGLE_DESKTOP_CLIENT_ID` 추가
2. `deploy.sh` — `env.core` 히어독에 `GOOGLE_DESKTOP_CLIENT_ID=${GOOGLE_DESKTOP_CLIENT_ID}` 추가
3. `docker-compose.core.yml` — `environment:` 블록에 `GOOGLE_DESKTOP_CLIENT_ID: ${GOOGLE_DESKTOP_CLIENT_ID}` 추가

**미결:** Google Cloud Console에서 Desktop Client ID 생성 및 값 확보
