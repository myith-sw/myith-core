package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.OutboxRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    @Override
    public List<OutboxEvent> findPending() {
        return jpaRepository.findByStatusOrderByCreatedAtAsc("PENDING").stream()
                .map(e -> new OutboxEvent(e.getId(), e.getEventId(),
                        e.getEventType(), e.getPayload(), e.getRetryCount()))
                .toList();
    }

    @Override
    public void markPublished(Long id) {
        jpaRepository.findById(id).ifPresent(e -> {
            e.markPublished();
            jpaRepository.save(e);
        });
    }

    @Override
    public void incrementRetry(Long id) {
        jpaRepository.findById(id).ifPresent(e -> {
            e.incrementRetry();
            jpaRepository.save(e);
        });
    }

    @Override
    public void markFailed(Long id) {
        jpaRepository.findById(id).ifPresent(e -> {
            e.markFailed();
            jpaRepository.save(e);
        });
    }

    @Override
    public boolean existsRecentEvent(String aggregateId, String eventType, Instant since) {
        return jpaRepository.existsByAggregateIdAndEventTypeAndCreatedAtAfter(aggregateId, eventType, since);
    }

    @Override
    public java.util.Optional<Instant> findCreatedAtByEventId(java.util.UUID eventId) {
        return jpaRepository.findCreatedAtByEventId(eventId);
    }
}
