package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.QuestRepository;
import com.myith.core.domain.roadmap.Quest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class QuestRepositoryAdapter implements QuestRepository {

    private final QuestJpaRepository jpaRepository;

    public QuestRepositoryAdapter(QuestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Quest> saveAll(List<Quest> quests) {
        List<QuestJpaEntity> entities = quests.stream()
                .map(QuestJpaEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(QuestJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Quest> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapIdAndDeletedAtIsNull(roadmapId).stream()
                .map(QuestJpaEntity::toDomain)
                .toList();
    }
}
