package com.myith.core.domain.dashboard;

import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 축 % = count(DONE) / count(전체 − ALREADY_KNOWN) × 100
 * 분자·분모 양쪽에서 ALREADY_KNOWN을 빼서, 나머지를 전부 완료하면 100%에 도달한다.
 * 분모가 0이면(전부 ALREADY_KNOWN): DONE이 1개라도 있으면 100%, 없으면 0%.
 */
public class CompletionRateAxisAggregator implements AxisAggregator {

    @Override
    public BigDecimal aggregate(List<Quest> questsInAxis) {
        if (questsInAxis.isEmpty()) return BigDecimal.ZERO;

        long alreadyKnown = questsInAxis.stream()
                .filter(q -> q.getStatus() == QuestStatus.ALREADY_KNOWN)
                .count();
        long completed = questsInAxis.stream()
                .filter(q -> q.getStatus().isDone())
                .count();
        long denominator = questsInAxis.size() - alreadyKnown;
        if (denominator == 0) {
            return completed > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(completed * 100)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
