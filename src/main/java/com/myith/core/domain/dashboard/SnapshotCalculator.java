package com.myith.core.domain.dashboard;

import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * dashboard_snapshot 재계산 로직.
 * 멱등: 같은 퀘스트 상태로 호출하면 같은 결과.
 */
public class SnapshotCalculator {

    private final GrowthStagePolicy stagePolicy;
    private final AxisAggregator axisAggregator;

    public SnapshotCalculator(GrowthStagePolicy stagePolicy, AxisAggregator axisAggregator) {
        this.stagePolicy = stagePolicy;
        this.axisAggregator = axisAggregator;
    }

    public SnapshotResult calculate(List<Quest> quests, String currentMaxStage) {
        if (quests.isEmpty()) {
            return new SnapshotResult(BigDecimal.ZERO, stagePolicy.determine(BigDecimal.ZERO),
                    currentMaxStage, List.of());
        }

        // 완료율: DONE만 집계. 분모에서도 ALREADY_KNOWN을 뺀다.
        // 경력자가 자가진단만으로 높은 완료율을 얻는 것을 방지(ff83de0).
        // 분모가 0이면(전부 ALREADY_KNOWN): DONE이 1개라도 있으면 100%, 없으면 0%.
        long alreadyKnown = quests.stream().filter(q -> q.getStatus() == QuestStatus.ALREADY_KNOWN).count();
        long denominator = quests.size() - alreadyKnown;
        long completed = quests.stream().filter(q -> q.getStatus().isDone()).count();
        BigDecimal completionRate = denominator == 0
                ? (completed > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                : BigDecimal.valueOf(completed * 100)
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);

        // stage (퇴화 방지: D-3)
        String calculatedStage = stagePolicy.determine(completionRate);
        String stage = stagePolicy.isHigherOrEqual(calculatedStage, currentMaxStage)
                ? calculatedStage : currentMaxStage;
        String maxStage = stagePolicy.isHigherOrEqual(stage, currentMaxStage)
                ? stage : currentMaxStage;

        // 레이더 축별 집계 (D-4)
        Map<String, List<Quest>> byAxis = quests.stream()
                .collect(Collectors.groupingBy(Quest::getAxisCode));

        List<RadarEntry> radar = byAxis.entrySet().stream()
                .map(e -> new RadarEntry(e.getKey(), axisAggregator.aggregate(e.getValue())))
                .toList();

        return new SnapshotResult(completionRate, stage, maxStage, radar);
    }

    public record SnapshotResult(BigDecimal completionRate, String stage, String maxStage,
                                 List<RadarEntry> radar) {}

    public record RadarEntry(String axisCode, BigDecimal percent) {}
}
