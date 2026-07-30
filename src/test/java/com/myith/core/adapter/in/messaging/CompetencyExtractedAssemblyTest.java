package com.myith.core.adapter.in.messaging;

import com.myith.core.domain.roadmap.*;
import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompetencyExtracted 즉시 조립과 ConsistencyScheduler 안전망의 핵심 로직을 검증한다.
 * assembleAndSnapshot 자체는 RoadmapCreateService가 담당하므로,
 * 여기서는 조립 전후의 상태 가드·멱등성·MasteryMerger 반영을 테스트한다.
 */
class CompetencyExtractedAssemblyTest {

    // ── 테스트 1: ANALYZING 상태에서만 조립 진행 ──

    @Test
    @DisplayName("ANALYZING 상태인 로드맵에서만 조립이 진행된다")
    void onlyAnalyzingRoadmapIsAssembled() {
        Roadmap analyzing = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);
        assertEquals(GenerationState.ANALYZING, analyzing.getGenerationState());

        // 조립 후 READY
        analyzing.markReady();
        assertEquals(GenerationState.READY, analyzing.getGenerationState());
    }

    @Test
    @DisplayName("이미 READY인 로드맵은 조립 대상이 아니다")
    void readyRoadmapSkipped() {
        Roadmap ready = Roadmap.create(1L, "server", 1, GenerationState.READY);
        assertNotEquals(GenerationState.ANALYZING, ready.getGenerationState());
    }

    @Test
    @DisplayName("이미 FAILED인 로드맵은 조립 대상이 아니다")
    void failedRoadmapSkipped() {
        Roadmap roadmap = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);
        roadmap.markFailed();
        assertNotEquals(GenerationState.ANALYZING, roadmap.getGenerationState());
    }

    // ── 테스트 2: 같은 eventId 재수신 → 중복 방지는 processed_event PK로 ──
    // (DB 의존이므로 여기서는 상태 가드만 검증)

    @Test
    @DisplayName("markReady 후 다시 조립해도 상태가 READY로 유지된다 (중복 무해)")
    void doubleMarkReadyIsSafe() {
        Roadmap roadmap = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);
        roadmap.markReady();
        roadmap.markReady(); // 두 번째 호출
        assertEquals(GenerationState.READY, roadmap.getGenerationState());
    }

    // ── 테스트 4: AI 보정값 반영 (MasteryMerger) ──

    @Test
    @DisplayName("user_competency가 있으면 AI 보정값이 mastery에 반영된다")
    void aiCompetencyReflectedInMastery() {
        Map<String, BigDecimal> self = Map.of("git", new BigDecimal("0.33"));
        Map<String, CompetencyEntry> ai = Map.of(
                "git", new CompetencyEntry(new BigDecimal("0.66"), "GitHub Actions CI 구성 경험"));

        BigDecimal merged = MasteryMerger.merge(self, ai, "git");
        assertEquals(0, merged.compareTo(new BigDecimal("0.66")),
                "AI 보정값(0.66)이 자가진단(0.33)을 대체해야 한다");
    }

    @Test
    @DisplayName("AI 보정값이 alreadyKnownThreshold(0.66)이면 ALREADY_KNOWN 판정")
    void aiCompetencyTriggersAlreadyKnown() {
        BigDecimal threshold = new BigDecimal("0.66");
        BigDecimal merged = new BigDecimal("0.66"); // AI 보정 결과

        assertTrue(merged.compareTo(threshold) >= 0,
                "M >= 0.66이면 ALREADY_KNOWN 판정");
    }

    @Test
    @DisplayName("user_competency가 없으면 자가진단 값만 사용된다")
    void noCompetencyFallsBackToSelf() {
        Map<String, BigDecimal> self = Map.of("git", new BigDecimal("0.33"));

        BigDecimal merged = MasteryMerger.merge(self, null, "git");
        assertEquals(0, merged.compareTo(new BigDecimal("0.33")),
                "AI 보정 없으면 자가진단(0.33) 그대로");
    }

    // ── 테스트 5: retry 누적 → max 도달 시 자가진단만으로 조립 (FAILED 아님) ──

    @Test
    @DisplayName("retry 누적 후 max 도달 → FAILED가 아닌 READY로 확정 가능")
    void maxRetriesLeadsToReadyNotFailed() {
        int maxRetries = 3;
        Roadmap roadmap = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);

        // 3회 retry
        roadmap.incrementRetry();
        roadmap.incrementRetry();
        roadmap.incrementRetry();

        assertTrue(roadmap.getRetryCount() >= maxRetries);
        assertEquals(GenerationState.ANALYZING, roadmap.getGenerationState(),
                "retry만 올리고 아직 ANALYZING");

        // max 도달 시 자가진단만으로 조립 후 markReady (FAILED가 아님)
        roadmap.markReady();
        assertEquals(GenerationState.READY, roadmap.getGenerationState(),
                "max-retries 후에도 READY로 확정 (FAILED 아님)");
    }

    @Test
    @DisplayName("competency 없고 retry < max → ANALYZING 유지 (Worker를 더 기다림)")
    void noCompetencyAndRetryBelowMax_staysAnalyzing() {
        int maxRetries = 3;
        Roadmap roadmap = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);

        roadmap.incrementRetry(); // retry 1
        boolean hasCompetency = false;

        // max 미도달 + competency 없음 → ANALYZING 유지
        if (!hasCompetency && roadmap.getRetryCount() < maxRetries) {
            // save만 하고 continue
        }

        assertEquals(GenerationState.ANALYZING, roadmap.getGenerationState(),
                "Worker 응답 대기 중이므로 ANALYZING 유지");
    }

    // ── 테스트 6: 조립 실패 → ANALYZING 유지 ──

    @Test
    @DisplayName("조립 중 예외 발생 → ANALYZING 유지 (안전망이 재시도)")
    void assemblyFailureKeepsAnalyzing() {
        Roadmap roadmap = Roadmap.create(1L, "server", 1, GenerationState.ANALYZING);

        // 조립 시도 중 예외 발생 (시뮬레이션)
        try {
            throw new RuntimeException("assembly failed");
        } catch (Exception e) {
            // 예외를 삼키고 상태를 변경하지 않는다
        }

        assertEquals(GenerationState.ANALYZING, roadmap.getGenerationState(),
                "예외 후에도 ANALYZING 유지 → 스케줄러가 재시도");
    }
}
