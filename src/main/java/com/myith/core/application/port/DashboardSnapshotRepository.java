package com.myith.core.application.port;

import java.math.BigDecimal;
import java.util.Optional;

public interface DashboardSnapshotRepository {

    void save(Long roadmapId, BigDecimal completionRate, String stage, String maxStage,
              String radarJson, long version);

    Optional<SnapshotData> findByRoadmapId(Long roadmapId);

    record SnapshotData(Long roadmapId, BigDecimal completionRate, String stage, String maxStage,
                        String radarJson, long version) {}
}
