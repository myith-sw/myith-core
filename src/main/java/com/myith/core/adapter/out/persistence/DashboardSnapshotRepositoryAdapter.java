package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.DashboardSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
public class DashboardSnapshotRepositoryAdapter implements DashboardSnapshotRepository {

    private final DashboardSnapshotJpaRepository jpaRepository;

    public DashboardSnapshotRepositoryAdapter(DashboardSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Long roadmapId, BigDecimal completionRate, String stage, String maxStage,
                     String radarJson, long version) {
        DashboardSnapshotJpaEntity entity = jpaRepository.findByRoadmapId(roadmapId)
                .orElseGet(() -> {
                    DashboardSnapshotJpaEntity e = new DashboardSnapshotJpaEntity();
                    e.setRoadmapId(roadmapId);
                    return e;
                });
        entity.setCompletionRate(completionRate);
        entity.setStage(stage);
        entity.setMaxStage(maxStage);
        entity.setRadar(radarJson);
        entity.setComputedAt(Instant.now());
        entity.setVersion(version);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<SnapshotData> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapId(roadmapId)
                .map(e -> new SnapshotData(
                        e.getRoadmapId(),
                        e.getCompletionRate(),
                        e.getStage(),
                        e.getMaxStage(),
                        e.getRadar(),
                        e.getVersion()
                ));
    }
}
