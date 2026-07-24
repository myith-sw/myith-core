package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.OutboxRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    public OutboxRepositoryAdapter(OutboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(String aggregateType, String aggregateId, UUID eventId,
                     String eventType, String payloadJson) {
        OutboxJpaEntity entity = OutboxJpaEntity.create(
                aggregateType, aggregateId, eventId, eventType, payloadJson
        );
        jpaRepository.save(entity);
    }
}
