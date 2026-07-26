package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.roadmap.RoadmapStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RoadmapRepositoryAdapter implements RoadmapRepository {

    private final RoadmapJpaRepository jpaRepository;

    public RoadmapRepositoryAdapter(RoadmapJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Roadmap save(Roadmap roadmap) {
        RoadmapJpaEntity entity = RoadmapJpaEntity.fromDomain(roadmap);
        RoadmapJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Roadmap> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(RoadmapJpaEntity::toDomain);
    }

    @Override
    public List<Roadmap> findActiveByUserIdAndJobCode(Long userId, String jobCode) {
        return jpaRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, RoadmapStatus.ACTIVE.name())
                .stream()
                .filter(e -> e.toDomain().getJobCode().equals(jobCode))
                .map(RoadmapJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Roadmap> findActiveByUserId(Long userId) {
        return jpaRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, RoadmapStatus.ACTIVE.name())
                .stream()
                .map(RoadmapJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Roadmap> findStuckAnalyzing(Instant cutoff) {
        return jpaRepository.findByGenerationStateAndUpdatedAtBefore("ANALYZING", cutoff)
                .stream()
                .map(RoadmapJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Roadmap> findByUserId(Long userId) {
        return jpaRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(RoadmapJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void softDeleteByUserId(Long userId) {
        jpaRepository.softDeleteByUserId(userId);
    }
}
