package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.StarRecordRepository;
import com.myith.core.domain.star.StarRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class StarRecordRepositoryAdapter implements StarRecordRepository {

    private final StarRecordJpaRepository jpaRepository;

    public StarRecordRepositoryAdapter(StarRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StarRecord save(StarRecord record) {
        StarRecordJpaEntity entity = StarRecordJpaEntity.fromDomain(record);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<StarRecord> findByQuestId(Long questId) {
        return jpaRepository.findByQuestIdAndDeletedAtIsNull(questId)
                .map(StarRecordJpaEntity::toDomain);
    }

    @Override
    public List<StarRecord> findByCursor(Long userId, Long cursor, String completeness, int size) {
        return jpaRepository.findByCursor(userId, cursor, completeness, PageRequest.of(0, size))
                .stream()
                .map(StarRecordJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void softDeleteByUserId(Long userId) {
        jpaRepository.softDeleteByUserId(userId);
    }
}
