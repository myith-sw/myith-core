package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {
    List<OutboxJpaEntity> findByStatusOrderByCreatedAtAsc(String status);

    @Query("SELECT COUNT(o) > 0 FROM OutboxJpaEntity o WHERE o.aggregateId = :aggregateId AND o.eventType = :eventType AND o.createdAt > :since")
    boolean existsByAggregateIdAndEventTypeAndCreatedAtAfter(
            @Param("aggregateId") String aggregateId,
            @Param("eventType") String eventType,
            @Param("since") Instant since);
}
