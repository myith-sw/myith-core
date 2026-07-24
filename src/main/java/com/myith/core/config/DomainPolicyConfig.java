package com.myith.core.config;

import com.myith.core.domain.dashboard.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DomainPolicyConfig {

    @Bean
    public GrowthStagePolicy growthStagePolicy(
            @Value("${policy.growth-stage.boundaries}") List<Integer> boundaries,
            @Value("${policy.growth-stage.names}") List<String> names) {
        return new DefaultGrowthStagePolicy(boundaries, names);
    }

    @Bean
    public AxisAggregator axisAggregator() {
        return new CompletionRateAxisAggregator();
    }
}
