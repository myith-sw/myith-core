package com.myith.core.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.application.port.DiagnosisRepository;
import com.myith.core.application.port.JobProfileReadRepository;
import com.myith.core.domain.diagnosis.UserDiagnosis;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.domain.roadmap.GenerationState;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.roadmap.RoadmapStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencySchedulerTest {

    @Test
    @DisplayName("done 이벤트의 roadmapId는 rmp_ 접두사가 붙어야 한다")
    void doneEvent_roadmapIdHasPrefix() {
        Roadmap stuck = Roadmap.restore(42L, 1L, "backend", 1,
                RoadmapStatus.ACTIVE, GenerationState.ANALYZING, 0,
                Instant.now(), Instant.now().minusSeconds(300), null, null);

        StubRoadmapRepository roadmapRepo = new StubRoadmapRepository(stuck);
        StubJobProfileReadRepository profileRepo = new StubJobProfileReadRepository();
        RecordingSseRegistry sseRegistry = new RecordingSseRegistry(42L);

        // assembleAndSnapshot을 no-op으로 오버라이드
        RoadmapCreateService createService = new RoadmapCreateService(
                roadmapRepo, null, null, new StubDiagnosisRepository(),
                null, null, profileRepo, null,
                new ObjectMapper(), null, null,
                BigDecimal.valueOf(0.66), 3) {
            @Override
            public void assembleAndSnapshot(Roadmap roadmap, JobProfileData profile,
                                            List<AnswerDto> answers) {
                // no-op
            }
        };

        ConsistencyScheduler scheduler = new ConsistencyScheduler(
                roadmapRepo, profileRepo,
                new StubDiagnosisRepository(),
                createService,
                sseRegistry, 3, 60);

        scheduler.scan();

        assertEquals(1, sseRegistry.sent.size(), "should send exactly one SSE event");
        Object[] event = sseRegistry.sent.get(0);
        assertEquals("done", event[0]);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) event[1];
        String roadmapId = data.get("roadmapId").toString();
        assertTrue(roadmapId.startsWith("rmp_"),
                "roadmapId should start with rmp_, got: " + roadmapId);
        assertEquals("rmp_42", roadmapId);
    }

    // ── Test doubles ──

    private static class StubRoadmapRepository implements RoadmapRepository {
        private final Roadmap stuck;
        StubRoadmapRepository(Roadmap stuck) { this.stuck = stuck; }
        @Override public List<Roadmap> findStuckAnalyzing(Instant cutoff) { return List.of(stuck); }
        @Override public Roadmap save(Roadmap roadmap) { return roadmap; }
        @Override public Optional<Roadmap> findById(Long id) { return Optional.empty(); }
        @Override public List<Roadmap> findActiveByUserIdAndJobCode(Long userId, String jobCode) { return List.of(); }
        @Override public List<Roadmap> findActiveByUserId(Long userId) { return List.of(); }
        @Override public List<Roadmap> findByUserId(Long userId) { return List.of(); }
        @Override public void softDeleteByUserId(Long userId) {}
    }

    private static class StubJobProfileReadRepository implements JobProfileReadRepository {
        @Override
        public Optional<JobProfileData> findByJobCodeAndVersion(String jobCode, int version) {
            return Optional.of(new JobProfileData(
                    "backend", 1, "[]", "[]", "[]", "[]", "[]", "[]", "[]"));
        }
        @Override
        public Optional<JobProfileData> findLatestByJobCode(String jobCode) {
            return findByJobCodeAndVersion(jobCode, 1);
        }
    }

    private static class StubDiagnosisRepository implements DiagnosisRepository {
        @Override public List<UserDiagnosis> saveAll(List<UserDiagnosis> diagnoses) { return diagnoses; }
        @Override public List<UserDiagnosis> findByRoadmapId(Long roadmapId) { return List.of(); }
        @Override public void deleteByRoadmapIds(List<Long> roadmapIds) {}
    }

    private static class RecordingSseRegistry extends SseRegistry {
        final List<Object[]> sent = new CopyOnWriteArrayList<>();
        private final Long connectedRoadmapId;
        RecordingSseRegistry(Long connectedRoadmapId) { this.connectedRoadmapId = connectedRoadmapId; }
        @Override public boolean hasConnection(Long roadmapId) { return roadmapId.equals(connectedRoadmapId); }
        @Override public void send(Long roadmapId, String eventName, Object data) { sent.add(new Object[]{eventName, data}); }
        @Override public void complete(Long roadmapId) {}
    }
}
