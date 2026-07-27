package com.myith.core.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    void save(String aggregateType, String aggregateId, UUID eventId,
              String eventType, String payloadJson);

    List<OutboxEvent> findPending();

    void markPublished(Long id);

    void incrementRetry(Long id);

    void markFailed(Long id);

    boolean existsRecentEvent(String aggregateId, String eventType, Instant since);

    Optional<Instant> findCreatedAtByEventId(UUID eventId);

    record OutboxEvent(Long id, UUID eventId, String eventType,
                       String payload, int retryCount) {}
}
