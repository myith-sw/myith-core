# Known Issues — 시연 후 정리 대상

2026-07-30 전수조사에서 발견. 시연 3일 전이라 코드 안정성 우선으로 보류.

| # | 항목 | 위치 | 영향 | 보류 사유 |
|---|------|------|------|-----------|
| 2 | ConsistencyScheduler 단일 트랜잭션 | `ConsistencyScheduler.java:57-108` | 동시 ANALYZING 여럿일 때 하나 실패 시 전체 롤백 | 데모는 로드맵 1개씩 생성. 동시 ANALYZING 불가 |
| 3 | WorkerEventConsumer TOCTOU 레이스 | `WorkerEventConsumer.java:60-94` | existsById/save 사이 동시 처리 시 AI 보완 결과 누락 | 단일 인스턴스라 확률 극히 낮음 |
| 4 | profileImageUrl null 초기화 불가 | `User.java:50` | Swagger "기본 아바타 초기화" 설명과 불일치 | 시연에서 프로필 이미지 변경 안 함 |
| 5 | nextQuest/currentLevel에 PENDING 미포함 | `QuestDetailService.java:187-196` | STAR 작성 중 퀘스트가 nextQuest에서 빠짐 | D-16은 API 응답명 매핑 규칙. 오인용 |
| 6 | axis/tag 필터 미구현 | `StarController.java:85` | 파라미터 선언만 있고 서비스 전달 안 됨 | 프론트에서 해당 필터 미사용 |
| 8 | TraceId 클라이언트 무검증 | `TraceIdFilter.java:27-31` | 로그 인젝션 가능 | 시연 사용자 1명, 공격 시나리오 없음 |
| 9 | SSE 소유권 미검증 | `SseController.java:74` | 타인 roadmapId 구독 가능 | 시연 사용자 1명 |
| 10 | 캐릭터 목록 currentLevel ALREADY_KNOWN 제외 | `RoadmapQueryService.java:145-149` | 경력자 level 표시 오류 가능 | 대시보드 핵심 수치는 수정 완료. 목록은 보조 |
| 11 | nickname trim 미처리 | `User.java:48-51` | 공백 닉네임 통과 | 프론트 입력 제한으로 방어 |
| 12 | 탈퇴 시 dashboard_snapshot 미삭제 | `UserService.java:49-77` | 고아 레코드 | 탈퇴 사용자 재접근 불가 |
| 13 | 퀘스트 레벨 이동 시 원래 레벨 orderInLevel 재정렬 누락 | `QuestManageService.java:70-107` | 순서 빈 공백 | 프론트가 index 기반 렌더링 |
| 14 | addCustomQuest level 범위 미검증 | `QuestManageService.java:118` | 존재하지 않는 레벨에 추가 가능 | 프론트 UI가 유효 레벨만 선택 |
| 15 | OutboxRelayScheduler traceId null | `OutboxRelayScheduler.java:50` | 스케줄러 발행 메시지에 traceId 없음 | 추적 불편하지만 동작 무관 |
| 16 | AI Enhancement TTL 하드코딩 | `WorkerEventConsumer.java:125` | C-6 위반 (30분 고정) | yml 값과 동일. 기능 영향 없음 |
| 17 | Content-Disposition 파일명 하드코딩 | `DashboardController.java:231` | 다운로드 파일명이 고정 | 파일 내용은 정상 |
| 18 | NO_EXPORTABLE_EXPERIENCE 미구현 | `ExportService.java` | STAR 0건이어도 빈 문서 다운로드 | 시연에서 STAR 없이 내보내기 안 함 |
| 19 | cursor NumberFormatException 미처리 | `StarController.java:85` | 잘못된 cursor → 500 | 프론트가 정상 cursor만 전달 |
| 20 | completed=false 수렴 확인 미처리 | `QuestWriteFacade.java:166` | 완료 취소 3회 충돌 시 불필요한 409 | 데모에서 완료 취소 안 함 |
| 21 | CORS origins/originPatterns 충돌 | `CorsConfig.java:20-21` | 로컬 개발 시 CORS 허용 불확실 | 현재 동작 확인됨 |
| 22 | findStuckAnalyzing deleted_at 누락 | `RoadmapJpaRepository.java:18` | 삭제된 로드맵도 재조립 대상 | 삭제 후 ANALYZING 상태 유지 확률 극히 낮음 |
| 23 | UserController IdCodec 미사용 | `UserController.java:126` | "usr_" + id 하드코딩 | 기능 동일. 일관성 문제 |
| 24 | Upload 허용 확장자 Swagger 불일치 | `UploadController.java` | 문서는 pdf/png/jpeg, 코드는 zip/tar.gz도 허용 | 프론트가 제한된 타입만 전송 |
| 25 | DemoController 에러 공통 래퍼 미사용 | `DemoController.java:55` | 에러 응답 형식 비표준 | 시연 전용 API |
| 26 | ConsistencyScheduler TODO 오해 유발 | `ConsistencyScheduler.java:97` | 이미 구현된 로직을 미구현으로 오인 | 주석 정리. 동작 무관 |
