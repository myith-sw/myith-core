package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoadmapJpaRepository extends JpaRepository<RoadmapJpaEntity, Long> {

    Optional<RoadmapJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<RoadmapJpaEntity> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, String status);

    @Query("SELECT r FROM RoadmapJpaEntity r WHERE r.generationState = :state AND r.updatedAt < :cutoff AND r.deletedAt IS NULL")
    List<RoadmapJpaEntity> findByGenerationStateAndUpdatedAtBefore(@Param("state") String state, @Param("cutoff") Instant cutoff);
}
