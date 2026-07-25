package com.myith.core.config;

import com.myith.core.domain.dashboard.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(DomainPolicyConfig.GrowthStageProperties.class)
public class DomainPolicyConfig {

    @ConfigurationProperties(prefix = "policy.growth-stage")
    public record GrowthStageProperties(List<Integer> boundaries, List<String> names) {}

    @Bean
    public GrowthStagePolicy growthStagePolicy(GrowthStageProperties props) {
        return new DefaultGrowthStagePolicy(props.boundaries(), props.names());
    }

    @Bean
    public AxisAggregator axisAggregator() {
        return new CompletionRateAxisAggregator();
    }
}
