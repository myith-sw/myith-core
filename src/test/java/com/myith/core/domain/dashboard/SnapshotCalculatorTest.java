package com.myith.core.domain.dashboard;

import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestSource;
import com.myith.core.domain.roadmap.QuestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotCalculatorTest {

    private SnapshotCalculator calculator;

    @BeforeEach
    void setUp() {
        GrowthStagePolicy stagePolicy = new DefaultGrowthStagePolicy(
                List.of(20, 50, 80), List.of("시작", "성장", "숙련", "완성"));
        AxisAggregator axisAggregator = new CompletionRateAxisAggregator();
        calculator = new SnapshotCalculator(stagePolicy, axisAggregator);
    }

    private static long idSeq = 1;

    private static Quest quest(String axisCode, QuestStatus status) {
        long id = idSeq++;
        return Quest.restore(id, 1L, "skill_" + id, axisCode,
                1, 0, "Quest " + id, null, null, null,
                QuestSource.SKILL, status, null, 0, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("ALREADY_KNOWN 3 + DONE 0 + OPEN 2 → 완료율 0%")
    void alreadyKnownExcluded_noDone() {
        idSeq = 1;
        var quests = List.of(
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.OPEN),
                quest("a", QuestStatus.OPEN)
        );

        var result = calculator.calculate(quests, "시작");

        assertEquals(0, result.completionRate().compareTo(BigDecimal.ZERO),
                "DONE 0개, 분모 2(전체5 − AK3) → 0%");
    }

    @Test
    @DisplayName("ALREADY_KNOWN 3 + DONE 2 + OPEN 0 → 완료율 100%")
    void alreadyKnownExcluded_allDone() {
        idSeq = 1;
        var quests = List.of(
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.DONE),
                quest("a", QuestStatus.DONE)
        );

        var result = calculator.calculate(quests, "시작");

        assertEquals(0, result.completionRate().compareTo(BigDecimal.valueOf(100)),
                "DONE 2개, 분모 2(전체5 − AK3) → 100%");
    }

    @Test
    @DisplayName("전부 ALREADY_KNOWN → 완료율 100%, 분모 0 예외 없음")
    void allAlreadyKnown_returns100() {
        idSeq = 1;
        var quests = List.of(
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN),
                quest("a", QuestStatus.ALREADY_KNOWN)
        );

        var result = calculator.calculate(quests, "시작");

        assertEquals(0, result.completionRate().compareTo(BigDecimal.valueOf(100)),
                "분모 0 → 100%");
    }

    @Test
    @DisplayName("축에 ALREADY_KNOWN만 있음 → 그 축 100%")
    void axisAllAlreadyKnown_returns100() {
        idSeq = 1;
        var quests = List.of(
                quest("axisA", QuestStatus.ALREADY_KNOWN),
                quest("axisA", QuestStatus.ALREADY_KNOWN),
                quest("axisB", QuestStatus.OPEN),
                quest("axisB", QuestStatus.DONE)
        );

        var result = calculator.calculate(quests, "시작");

        // axisA: 전부 AK → 100%
        var axisARadar = result.radar().stream()
                .filter(r -> "axisA".equals(r.axisCode())).findFirst().orElseThrow();
        assertEquals(0, axisARadar.percent().compareTo(BigDecimal.valueOf(100)),
                "axisA 전부 ALREADY_KNOWN → 100%");

        // axisB: DONE 1 / (전체2 − AK0) = 50%
        var axisBRadar = result.radar().stream()
                .filter(r -> "axisB".equals(r.axisCode())).findFirst().orElseThrow();
        assertEquals(0, axisBRadar.percent().compareTo(BigDecimal.valueOf(50)),
                "axisB DONE 1개 / 전체 2개 → 50%");
    }

    @Test
    @DisplayName("ALREADY_KNOWN 없는 일반 케이스는 기존과 동일")
    void noAlreadyKnown_worksAsUsual() {
        idSeq = 1;
        var quests = List.of(
                quest("a", QuestStatus.DONE),
                quest("a", QuestStatus.DONE),
                quest("a", QuestStatus.OPEN),
                quest("a", QuestStatus.LOCKED)
        );

        var result = calculator.calculate(quests, "시작");

        assertEquals(0, result.completionRate().compareTo(BigDecimal.valueOf(50)),
                "DONE 2 / 전체 4 → 50%");
    }
}
