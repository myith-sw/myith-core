package com.myith.core.domain.dashboard;

import java.math.BigDecimal;

public interface GrowthStagePolicy {
    String determine(BigDecimal completionRate);
    boolean isHigherOrEqual(String candidate, String current);
}
