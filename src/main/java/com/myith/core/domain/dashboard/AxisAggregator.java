package com.myith.core.domain.dashboard;

import com.myith.core.domain.roadmap.Quest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 레이더 축 집계.
 * 기본 구현: 단순 완료율 (D-4)
 */
public interface AxisAggregator {
    BigDecimal aggregate(List<Quest> questsInAxis);
}
