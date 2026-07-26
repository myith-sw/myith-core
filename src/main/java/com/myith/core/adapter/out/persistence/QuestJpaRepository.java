package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestJpaRepository extends JpaRepository<QuestJpaEntity, Long> {

    List<QuestJpaEntity> findByRoadmapIdAndDeletedAtIsNull(Long roadmapId);

    Optional<QuestJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Modifying
    @Query("UPDATE QuestJpaEntity q SET q.deletedAt = CURRENT_TIMESTAMP WHERE q.roadmapId IN :roadmapIds AND q.deletedAt IS NULL")
    void softDeleteByRoadmapIds(@Param("roadmapIds") List<Long> roadmapIds);
}
