package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ProcessedEventJpaEntity() {}

    public static ProcessedEventJpaEntity create(UUID eventId) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.eventId = eventId;
        e.consumedAt = Instant.now();
        return e;
    }
}
