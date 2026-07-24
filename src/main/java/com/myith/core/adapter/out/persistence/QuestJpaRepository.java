package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestJpaRepository extends JpaRepository<QuestJpaEntity, Long> {

    List<QuestJpaEntity> findByRoadmapIdAndDeletedAtIsNull(Long roadmapId);
}
