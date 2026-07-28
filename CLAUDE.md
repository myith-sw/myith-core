# CLAUDE.md — MYiTH Core 서버

이 파일이 이 저장소의 개발 기준이다.

---

# PART A. 행동 지침

1. **추측하지 않는다.** 불확실하면 묻는다. 해석이 여러 갈래면 모두 제시한다.
2. **최소한의 코드만.** 요청 범위를 넘는 기능, 한 번만 쓰는 추상화, 발생할 수 없는 예외 처리를 만들지 않는다.
3. **꼭 필요한 곳만 건드린다.** 인접 코드를 "개선"하지 않는다. 내 변경이 만든 고아만 정리한다.
4. **성공 기준을 먼저 정의한다.** 여러 단계 작업이면 짧은 계획을 먼저 말한다.

---

# PART B. 프로젝트 맥락

## MYiTH란

채용공고(원티드)와 국가직무능력표준(NCS)을 결합해 개인 맞춤형 취업 로드맵을 제공하는 서비스다. 사용자는 캐릭터를 만들고, 목표 직무를 고르고, 자가진단을 거쳐 퀘스트로 구성된 로드맵을 받는다. 퀘스트를 완료하면 캐릭터가 성장하고, 활동은 STAR 형식으로 기록되어 취업 자산이 된다.

## 두 서버 구조

| 서버 | 저장소 | 언어 | 책임 |
|---|---|---|---|
| **Core (이 저장소)** | myith-core | Java 21 / Spring Boot | 사용자 요청 전담. 즉시 응답. |
| **Worker** | myith-worker | Python / FastAPI | 백그라운드 전담. 외부 API, 텍스트·이미지 처리, LLM. |

**최상위 원칙:** 무거운 연산은 백그라운드에서 미리 처리하고, 사용자 트랜잭션은 즉시 응답한다.

두 서버는 RabbitMQ로 통신한다. Core는 Transactional Outbox로 작업을 발행하고, Worker는 fanout exchange로 결과를 발행하며 Core가 이를 소비한다.

## 화면 흐름

```
[신규] 알 선택 → 캐릭터 생성(분야·직무·닉네임) → 자가진단
       → 로드맵 생성 → 로드맵 상세 → 퀘스트 수행·STAR 기록 → 대시보드

[재방문] 홈 → 로드맵 상세 → 퀘스트·STAR
        └ '새 캐릭터' → 알 선택으로 순환
```

## 스택

Java 21, Spring Boot 3.x, Spring Data JPA, Spring Security, PostgreSQL, Redis + Caffeine, RabbitMQ, Flyway, springdoc-openapi, AWS SDK v2(S3 Presigned), google-api-client(ID Token 검증), OpenPDF 또는 Flying Saucer(한글 폰트 임베딩 필수), JUnit 5 + Testcontainers.

## 패키지 구조

```
com.myith.core
├── domain/                     # 순수 도메인. 프레임워크 의존 없음
│   ├── user/  character/  roadmap/  diagnosis/  star/  dashboard/  job/
├── application/                # 유스케이스. 트랜잭션 경계
│   ├── auth/  character/  roadmap/  quest/  star/
│   ├── dashboard/  export/  upload/  presence/
│   └── port/                   # out 포트 인터페이스
├── adapter/
│   ├── in/web/  in/sse/  in/messaging/
│   └── out/persistence/  out/cache/  out/messaging/  out/storage/
├── scheduler/                  # Outbox 릴레이, 48h 스캔, 정합성
├── config/                     # Security, Cache, Rabbit, OpenAPI, 정책값
└── common/                     # 예외, 공통 응답, TraceId 필터, 활동 인터셉터
```

`application`은 `domain`에만 의존한다. `adapter.out`이 `application.port`의 인터페이스를 구현한다. 컨트롤러는 유스케이스만 호출하고 비즈니스 로직을 갖지 않는다.

---

# PART C. 절대 규칙

이걸 어기면 아키텍처가 깨진다.

**C-1.** Core는 외부 데이터 API(원티드·NCS·LLM·OCR·Vision)를 호출하지 않는다. 이벤트로 Worker에 위임한다.
- **예외:** Google OAuth 토큰 검증 (인증 인프라, 캐싱된 공개키로 로컬 검증).

**C-2.** Core는 파일을 처리하지 않는다. 파일 키(문자열)만 다룬다. S3 Presigned URL 발급, fileKey 전달이 전부다.

**C-3.** Core는 Worker 소유 테이블에 쓰지 않는다.
- 읽기 전용: `job_profile`, `user_competency`, `ncs_unit`, `ncs_certification`, `job`, `skill_ncs_map`
- 소유·쓰기: `users`, `character`, `roadmap`, `quest`, `user_diagnosis`, `star_record`, `dashboard_snapshot`, `outbox`, `processed_event`
- **Worker 소유 테이블의 DDL은 Worker Alembic에 있다. Core Flyway에 만들지 않는다.**

**C-4.** AI로 계산하지 않는다. 완료율·성장단계·우선순위·레벨 배치는 결정론적 규칙이다.

**C-5.** `domain/` 안에 JPA·Spring 애노테이션이 없어야 한다. 영속성 모델과 도메인 모델을 분리한다.

**C-6.** 정책값을 하드코딩하지 않는다. 전부 `application.yml`에 둔다.

---

# PART D. 틀리기 쉬운 도메인 규칙

## D-1. Lv과 stage는 다른 축이다

| | Lv (레벨) | stage (성장 단계) |
|---|---|---|
| 의미 | 퀘스트 난이도 구간 | 캐릭터 성장 |
| 개수 | 4~7개, 직무마다 다름 | 4개 고정 |
| 응답 | `level: 3` (숫자) | `stage: "성장"` (문자열) |

`"Lv.4 전설 단계"` 같은 결합 문자열을 만들지 않는다. stage 값: `시작`(0~20%), `성장`(20~50%), `숙련`(50~80%), `완성`(80~100%).

## D-2. 역량 테이블은 둘이고, 조립 시 병합한다

```
user_diagnosis   (Core 소유)   — 자가진단 원본
user_competency  (Worker 소유) — AI 보정 + evidence

최종 보유도 M:
  user_competency에 해당 스킬이 있고 evidence가 비어있지 않으면 → 그 값
  아니면 → user_diagnosis의 값
  둘 다 없으면 → 0
```

## D-3. ALREADY_KNOWN은 완료로 집계한다

```
완료율 = count(DONE + ALREADY_KNOWN) / count(전체) × 100
```

M ≥ 0.66 → `ALREADY_KNOWN`. 로드맵에 남되 접힌 상태, STAR 기록 가능. 퇴화 방지: stage는 `max(계산된 stage, max_stage)`.

## D-4. 레이더 축은 단순 완료율이다

```
축 % = count(그 축의 DONE + ALREADY_KNOWN) / count(그 축의 전체) × 100
```

가중평균이 **아니다.** `AxisAggregator` 인터페이스로 분리하되 기본은 단순 완료율.

## D-5. 퀘스트는 세 종류, 스킬이 없을 수 있다

| 종류 | skill_code | axis_code | source |
|------|-----------|-----------|--------|
| 스킬형 | 있음 | 있음 | SKILL |
| 활동형 | null | 있음 | ACTIVITY |
| 사용자정의 | null | 있음 | CUSTOM |

`quest.skill_code`는 nullable. 활동형도 완료율·레이더에 정상 집계된다.

## D-6. Priority는 레벨 내부에서만 정렬한다

```
Priority = (1 − M) × P    // P = 시장 보편성 (job_profile)

조립: 위상정렬 → Lv 밴드 배치 → 레벨 내 Priority 정렬
     → M ≥ 0.66 ALREADY_KNOWN
     → 레벨 해금 + 선행관계 → LOCKED / OPEN 결정 (QuestUnlockPolicy)
```

## D-7. 접속 시각은 둘이다

```
last_heartbeat_at — POST /api/heartbeat 에서만 갱신 → 앱 생존 여부
last_active_at    — 그 외 인증 요청에서 갱신 (5분 쓰로틀) → 48시간 판정
```

제외: heartbeat, /health, Swagger. 48시간 판정은 서버 스케줄러가 한다.

## D-8. 로드맵은 프로필 재빌드에 영향받지 않는다

`roadmap.profile_version`이 버전을 고정한다. 재생성 시 기존은 ARCHIVED(STAR 보존), 새 로드맵·캐릭터를 만든다.

## D-9. 닉네임은 두 군데

`users.nickname`(계정, 구글에서 초기화) ≠ `character.nickname`(캐릭터, nullable).

## D-10. 인증은 구글만

password_hash 없음, BCrypt 없음. `users.google_id` unique, 탈퇴 시 null.

## D-11. 탈퇴는 soft delete + PII 익명화

```
email→'deleted_{id}@myith.local', google_id→null, nickname→'탈퇴한 사용자', profile_image_url→null
연관 데이터도 deleted_at 세팅. 모든 조회에 deleted_at IS NULL 필수.
```

## D-12. fanout 소비

인스턴스별 임시 큐(`core.sse.{instanceId}`, auto-delete, exclusive)로 Worker fanout exchange 구독. SSE 레지스트리에 해당 roadmapId 연결이 있으면 전달, 없으면 무시. 상태 변경 이벤트는 `processed_event`로 멱등 처리.

## D-13. 정합성 스케줄러

매 1분: `generation_state='ANALYZING'`이고 `updated_at < now()-60초`인 로드맵을 스캔. user_competency 있으면 보정 조립, 없으면 자가진단만으로 폴백 조립, 3회 초과 시 FAILED. DB만 본다.

## D-14. 레벨 해금 규칙

```
Lv1 퀘스트     : 항상 해금 후보
Lv N+1 퀘스트  : Lv N 의 퀘스트 **전부** 완료(DONE + ALREADY_KNOWN)해야 해금 후보

최종 상태 = (레벨 해금 후보) AND (선행관계 충족) → OPEN
            그 외 → LOCKED
```

- ALREADY_KNOWN이 완료로 집계되므로 경력자는 초기 조립 시점에 여러 레벨이 자동 해금된다.
- 재잠금: Lv N에 미완료가 생기면 Lv N+1의 미완료 퀘스트를 LOCKED로 되돌린다. DONE/ALREADY_KNOWN은 건드리지 않는다.
- 재계산 시점: ① 초기 조립 ② 완료 토글 ③ 퀘스트 추가·삭제 ④ 순서 변경
- 구현: `QuestUnlockPolicy.recompute()` (순수 도메인 함수)

## D-15. QuestStatus 상태 전이

LOCKED  --(선행 충족 + 레벨 해금)--> OPEN
OPEN    --(STAR 저장, 1칸 이상 내용 있음)--> PENDING
PENDING --(STAR 전부 공백)--> OPEN
PENDING --(완료 true)--> DONE
OPEN    --(완료 true)--> DONE
DONE    --(완료 false)--> STAR 있으면 PENDING, 없으면 OPEN
조립 시   M ≥ 0.66 --> ALREADY_KNOWN

PENDING은 isCompleted()에 포함되지 않는다. 완료율·레이더에 영향 없음.
PENDING은 "STAR를 썼지만 완료 처리 안 함"이다. AI 피드백 대기와 무관하다.

---

# PART E. DB 스키마

## Core 소유 (쓰기)

```sql
users (id, email, google_id, nickname, profile_image_url,
       last_heartbeat_at, last_active_at, last_nudge_sent_at,
       created_at, updated_at, deleted_at)

roadmap (id, user_id FK, job_code, profile_version, status, generation_state,
         retry_count DEFAULT 0, created_at, updated_at, archived_at, deleted_at)

character (id, user_id FK, roadmap_id FK UNIQUE, species, nickname NULL,
           created_at, updated_at, deleted_at)

quest (id, roadmap_id FK, skill_code NULL, axis_code, level, order_in_level,
       title, completion_criteria NULL, ncs_unit_code NULL,
       source, status, completed_at NULL, version, created_at, updated_at, deleted_at)

user_diagnosis (id, roadmap_id FK, skill_code, mastery NUMERIC(3,2), created_at)
  UNIQUE (roadmap_id, skill_code)

star_record (id, quest_id FK, user_id FK, situation, task, action, result,
             completeness, tags VARCHAR[], created_at, updated_at, deleted_at)

dashboard_snapshot (roadmap_id PK, completion_rate, stage, max_stage,
                    radar JSONB, computed_at, version)

outbox (id, aggregate_type, aggregate_id, event_id UUID UNIQUE, event_type,
        payload JSONB, status, retry_count, created_at, published_at)

processed_event (event_id UUID PK, consumed_at)
```

## 읽기 전용 (Worker·배치 소유)

```sql
job (job_code PK, job_name, category_code, category_name, tagline, ncs_mapping)

job_profile (job_code + version PK, axes JSONB, skills JSONB, levels JSONB,
             prerequisites JSONB, questions JSONB, quest_templates JSONB,
             activity_quests JSONB, built_at)

user_competency (roadmap_id, skill_code, mastery, evidence, confidence)

ncs_unit (code PK, name, description, level)

ncs_certification (ncs_unit_code FK, cert_code, cert_name, unit_type)

skill_ncs_map (skill_code, ncs_unit_code)
-- DDL은 Worker Alembic이 소유한다. Core Flyway에 만들지 않는다.
```

---

# PART F. API 명세

에러: `{ code, message }`. 인증: `Authorization: Bearer {token}`.

## F-1. 인증

```
POST /api/auth/google    { idToken } → { accessToken, refreshToken, isNewUser }
POST /api/auth/refresh   { refreshToken } → { accessToken }
GET  /api/users/me
PATCH /api/users/me      { nickname?, profileImageUrl? }
DELETE /api/users/me     -- D-11 참조
```

## F-2. 캐릭터 목록

```
GET /api/characters
→ { characters: [{ characterId, roadmapId, species, nickname,
     jobCode, jobName, tagline, completionRate, stage, level,
     nextQuest: { questId, title } }] }
```

## F-3. 직무 목록

```
GET /api/jobs
→ { categories: [{ categoryCode, categoryName,
     jobs: [{ jobCode, jobName, tagline, keywords:[최대5], available }] }] }
```

job_profile 없으면 `available:false`, `JobProfileBuildRequested` 발행.

## F-4. 자가진단 문항

```
GET /api/jobs/{jobCode}/diagnosis
→ { profileVersion, questions: [{ skillCode, text, axisCode }] }
```

## F-5. 파일 업로드

```
POST /api/uploads/presign { fileName, contentType }
→ { uploadUrl, fileKey, expiresIn }
```

## F-6. 로드맵 생성

```
POST /api/roadmaps
  { jobCode, profileVersion, species, nickname?,
    answers:[{ skillCode, mastery }],
    narrative?:{ strength, difficulty },
    experiences?:[{ content?, repoUrl?, fileKey? }]   최대 3개. 초과 시 400 EXPERIENCES_LIMIT_EXCEEDED
  }
→ 200 { roadmapId }   // 선택형만 → 즉시 조립
→ 202 { roadmapId }   // 비정형(narrative 또는 experiences 존재) → 비동기, 정합성 스케줄러가 폴백(D-13)

GET /api/roadmaps/{id}/progress  (SSE)
→ event: progress { step, percent } / done { roadmapId } / error { code, message }
```

## F-7. 로드맵 상세

```
GET /api/roadmaps/{id}
→ { roadmapId, jobName, tagline, generationState,
    character: { species, nickname, stage, completionRate },
    levels: [{ level, quests:[{ questId, title, axisName, status, source }] }] }

PATCH  /api/roadmaps/{id}/quests/order  { questId, targetLevel, targetIndex }
POST   /api/roadmaps/{id}/quests        { title, axisCode, level }
DELETE /api/roadmaps/{id}/quests/{qid}  -- CUSTOM만
```

## F-8. 퀘스트 · STAR

```
GET   /api/quests/{id}
→ { questId, title, axisName, level, status,
    ncsUnit:{ code, name, description }|null, certifications:[{ name }],
    completionCriteria, star:{ situation, task, action, result }|null }

PATCH /api/quests/{id}/complete    { completed, version }
PUT   /api/quests/{id}/star        { situation, task, action, result }
POST  /api/quests/{id}/ai-enhancements   → 202 { requestId }
GET   /api/ai-enhancements/{reqId}       → { status, enhancedStar?, feedback[], resumeDraft? }
```

완료 토글 시 dashboard_snapshot 재계산. AI 보완은 Outbox 발행, 결과는 저장하지 않음.

## F-9. 대시보드 · 내보내기

```
GET /api/roadmaps/{id}/dashboard
→ { completionRate, stage, radar:[{ axisCode, axisName, percent }],
    skillTree:[...], experienceCards:[...] }

GET /api/star/records?cursor=&size=&axis=&completeness=&tag=
→ { records:[...], nextCursor }

GET /api/roadmaps/{id}/export?format=md|pdf  → 파일 다운로드
```

커서 기반 페이지네이션(OFFSET 금지). PDF는 한글 폰트 임베딩 필수.

## F-10. 데스크톱 앱

```
POST /api/heartbeat → { nudge, nudgeType?, nudgeMessage?, characterState:{ species, stage, completionRate }|null }
```

nudge=true일 때 nudgeType(종류)과 nudgeMessage(표시 문구)를 함께 반환한다. Electron은 이 문구를 그대로 표시한다.

## F-11. 시연 전용

```
POST /api/demo/nudge   Header: X-Demo-Token
  { userId, nudgeType }
→ 200 { queued, deliverWithinSeconds }
→ 403 데모 모드 꺼짐 또는 토큰 불일치
```

`myith.demo.enabled=true`일 때만 빈이 등록된다. 기본 꺼짐.
다음 heartbeat 호출 시 해당 userId에 대해 1회성 nudge를 반환한다.

---

# PART G. 비기능

## Transactional Outbox

비즈니스 트랜잭션과 동일 트랜잭션으로 outbox에 기록 → 릴레이 스케줄러가 RabbitMQ로 발행.

메시지 봉투:
```json
{ "eventId":"uuid", "eventType":"...", "version":1,
  "traceId":"uuid", "occurredAt":"...", "payload":{} }
```

발행: `RoadmapGenerationRequested`, `JobProfileBuildRequested`, `AiEnhancementRequested`
소비: `RoadmapGenerationProgress`, `CompetencyExtracted`, `JobProfileBuilt`, `AiEnhancementCompleted`, `StarFeedbackCompleted`(레거시, 큐 잔여 소진용)
소비: `RoadmapGenerationProgress`, `CompetencyExtracted`, `JobProfileBuilt`, `AiEnhancementCompleted`

### RoadmapGenerationRequested payload

```json
{
  "roadmapId": 1,
  "userId": 1,
  "jobCode": "server",
  "profileVersion": 1,
  "answers": [{ "skillCode": "git", "mastery": 0.66 }],
  "narrative": { "strength": "...", "difficulty": "..." },
  "experiences": [
    { "content": "경험 서술", "repoUrl": "https://...", "fileKey": "portfolio/..." },
    { "content": "경험 서술 2", "repoUrl": null, "fileKey": "portfolio/..." }
  ]
}
```

- `narrative`: 강점·어려움 서술. `experience` 필드는 `experiences[].content`로 이동됨.
- `experiences`: 프로젝트 경험 카드 배열. 사용자가 여러 개 등록 가능. 각 카드에 서술·링크·파일이 독립.
  - `content`: 경험 서술 텍스트 (nullable)
  - `repoUrl`: GitHub 저장소 URL (nullable) — 여러 개일 수 있으므로 순회 처리
  - `fileKey`: S3 파일 키 (nullable) — 여러 개일 수 있으므로 각각 가져와 파싱
- `narrative`와 `experiences` 모두 null이면 선택형 경로(즉시 조립), 하나라도 있으면 비동기 경로.

### CompetencyExtracted payload (확정, 2026-07-26)

```json
{
  "userId": 42,
  "roadmapId": 1007,
  "competencies": [
    { "skillCode": "git", "mastery": 0.66, "confidence": 0.85, "evidence": "GitHub Actions로 CI 파이프라인을 구성하고 브랜치 전략을 PR 기반으로 운영" }
  ]
}
```

- 원소 4필드 고정: `skillCode`, `mastery`, `confidence`, `evidence`. 추가 필드 없음.
- **근거 있는 스킬만** 포함. evidence null인 스킬은 배열에 들어오지 않는다.
- `mastery`, `confidence` 범위: 0.00~1.00.
- **부분 업서트(upsert)** 시맨틱: 배열에 없는 skill_code는 "변경 없음". 삭제 아님.
- **빈 배열** `competencies: []` = "추출 완료, 근거 0건, 자가진단만으로 조립하라" 신호. 에러 아님.
- 봉투 `version`: 현재 1 고정, 스키마 진화 시 올림. Core는 미지 필드 무시(전방 호환).
- 진실의 원천은 `user_competency` 테이블. 이벤트는 즉시 조립 트리거 + 편의 미러.

## CQRS 스냅샷

트리거: 퀘스트 완료 토글, 퀘스트 추가·삭제, 로드맵 조립 완료. 멱등하게 설계.

## 캐시

- **Redis**: dashboard_snapshot, job_profile. 키에 버전 포함.
- **Caffeine**: ncs_unit, ncs_certification, job (갱신 빈도 극히 낮음).

## 관측

TraceId를 MDC에 저장, RabbitMQ 헤더에 전파. 로그에 항상 포함.

## ID 표기 이원화

| 경계 | 표기 | 예 |
|---|---|---|
| REST API | 접두사 문자열 | `rmp_01J3ABC`, `qst_05` |
| RabbitMQ payload | 숫자(Long) | `"roadmapId": 1` |
| DB | BIGSERIAL | `1` |

변환은 `IdCodec`이 API 경계에서만 수행한다.

## 공통 응답 래퍼

성공(단건): `{ "data": { ... } }`
성공(목록): `{ "data": [...], "meta": { "nextCursor": "...", "hasNext": true } }`
오류: `{ "error": { "code": "...", "message": "...", "fieldErrors": null, "requestId": "..." } }`
예외: GET /api/health 만 래퍼 없이 평문 JSON.

---

# PART H. 시드 전략

Worker Alembic이 시드를 적재한다. Core Flyway에 시드 SQL을 넣지 않는다.
개발 환경에서는 Worker를 먼저 기동해 공유 테이블과 시드를 생성한 뒤 Core를 기동한다.

## 배포 순서 — 순서를 지킬 것

Core 는 `ddl-auto: validate` 로 기동 시 모든 엔티티의 테이블을 검증한다.
Worker 소유 테이블 6개(job, job_profile, ncs_unit, ncs_certification,
skill_ncs_map, user_competency)가 없으면 **Core 는 기동하지 못한다.**

1. Worker  alembic upgrade head            ← 공유 테이블 DDL 생성
2. Worker  python -m app.seed.load_all     ← NCS 265건·자격 521행·10직무 적재
3. Core    부팅                             ← Flyway V1 → validate 통과
4. Worker  컨테이너 기동

1·2 를 건너뛰면 Core 가 SchemaManagementException 으로 죽는다.

---

# PART I. 확장 포인트

| 인터페이스 | 역할 |
|---|---|
| `GrowthStagePolicy` | 완료율 → 성장 단계 |
| `AxisAggregator` | 레이더 축 집계 (기본: 단순 완료율) |
| `QuestOrderingStrategy` | 레벨 내 정렬 (기본: Priority) |
| `ExportRenderer` | 내보내기 포맷 (md, pdf) |
| `NotificationSender` | 알림 채널 |
| `FileStoragePort` | 파일 저장소 (S3) |

---

# PART J. 위임 프로토콜

Worker 작업을 만나면: (1) 멈추고 (2) `docs/handoff-to-worker.md`에 기록하고 (3) 사용자에게 알린다. 인라인은 `// HANDOFF(worker):` 태그.

기록 형식:
```markdown
## [YYYY-MM-DD] <제목>
**Worker 일인 이유:** C-1 / C-2 / C-3
**Core 쪽 계약:** 발행/소비 이벤트, 읽는 테이블
**미결:** Worker가 결정할 것
```

---

# PART K. 세션 규약

- 코드 쓰기 전에 PART C와 D를 읽는다.
- 도메인 규칙·절대 규칙·API·스키마가 바뀌면 이 파일을 갱신한다.
- 작업 완료 시 무엇을 구현했고, 위임 기록이 있는지, 이 파일 갱신이 필요한지 보고한다.

---

# PART L. 구현 순서

```
 1. 프로젝트 셋업  (완료)
 2. 인증 — 구글 로그인·JWT, 활동 갱신 인터셉터
 3. 조회 API — 직무 목록, 자가진단 문항 + 시드 데이터
 4. 로드맵 생성 — 트랜잭션 조립(선택형 경로), user_diagnosis, 레벨 해금(QuestUnlockPolicy)
 5. 로드맵 조회 — 상세, 퀘스트 순서·추가·삭제
 6. 퀘스트·STAR — 완료 토글(낙관적 락, 레벨 재계산), STAR CRUD, AI 보완, 커서 페이지네이션
 7. 대시보드 — CQRS 스냅샷, 레이더 완료율
 8. 내보내기 — MD, PDF(한글 폰트)
 9. 메시징 — Outbox 릴레이, fanout 컨슈머, SSE
10. 비동기 경로 — 202, SSE 진행률, 정합성 스케줄러
11. 앱 연동 — S3 Presigned, heartbeat, 48h 스케줄러
12. 마무리 — 캐시, 테스트, Dockerfile
```

테스트 필수: M값 병합, 조립(선후관계·Priority), 완료율·stage(퇴화 방지), 레이더, 낙관적 락, 스냅샷 멱등성, Outbox 롤백, 이벤트 멱등 소비, 정합성 스케줄러 폴백.

### 시연 리스크 — 레벨 해금이 전부-완료 조건이다

QuestUnlockPolicy 는 이전 레벨을 100% 완료해야 다음 레벨을 연다.
완화 스위치가 없다. 리허설에서 진행이 막히면 recompute 에 모드 인자를 추가해야 한다.

---

## 인프라 운영

인프라 관리는 `myith-infra` 저장소의 `OPS.md`를 참조한다.