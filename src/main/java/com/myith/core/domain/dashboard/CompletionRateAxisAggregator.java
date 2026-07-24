package com.myith.core.domain.dashboard;

import com.myith.core.domain.roadmap.Quest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 축 % = count(DONE + ALREADY_KNOWN) / count(전체) × 100
 */
public class CompletionRateAxisAggregator implements AxisAggregator {

    @Override
    public BigDecimal aggregate(List<Quest> questsInAxis) {
        if (questsInAxis.isEmpty()) return BigDecimal.ZERO;

        long completed = questsInAxis.stream()
                .filter(q -> q.getStatus().isCompleted())
                .count();

        return BigDecimal.valueOf(completed * 100)
                .divide(BigDecimal.valueOf(questsInAxis.size()), 2, RoundingMode.HALF_UP);
    }
}
