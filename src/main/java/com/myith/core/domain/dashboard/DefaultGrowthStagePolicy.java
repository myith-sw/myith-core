package com.myith.core.domain.dashboard;

import java.math.BigDecimal;
import java.util.List;

public class DefaultGrowthStagePolicy implements GrowthStagePolicy {

    private final List<Integer> boundaries;
    private final List<String> names;

    public DefaultGrowthStagePolicy(List<Integer> boundaries, List<String> names) {
        if (boundaries.size() + 1 != names.size()) {
            throw new IllegalArgumentException("names.size must be boundaries.size + 1");
        }
        this.boundaries = boundaries;
        this.names = names;
    }

    @Override
    public String determine(BigDecimal completionRate) {
        double rate = completionRate.doubleValue();
        for (int i = boundaries.size() - 1; i >= 0; i--) {
            if (rate >= boundaries.get(i)) {
                return names.get(i + 1);
            }
        }
        return names.getFirst();
    }

    @Override
    public boolean isHigherOrEqual(String candidate, String current) {
        return names.indexOf(candidate) >= names.indexOf(current);
    }
}
