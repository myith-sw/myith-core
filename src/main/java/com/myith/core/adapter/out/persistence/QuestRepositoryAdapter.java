package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.QuestRepository;
import com.myith.core.domain.roadmap.Quest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestRepositoryAdapter implements QuestRepository {

    private final QuestJpaRepository jpaRepository;

    public QuestRepositoryAdapter(QuestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Quest save(Quest quest) {
        QuestJpaEntity entity = QuestJpaEntity.fromDomain(quest);
        return jpaRepository.save(entity).toDomain();
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
    public Optional<Quest> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(QuestJpaEntity::toDomain);
    }

    @Override
    public List<Quest> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapIdAndDeletedAtIsNull(roadmapId).stream()
                .map(QuestJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void delete(Quest quest) {
        jpaRepository.findByIdAndDeletedAtIsNull(quest.getId()).ifPresent(entity -> {
            entity.setDeletedAt(Instant.now());
            jpaRepository.save(entity);
        });
    }
}
