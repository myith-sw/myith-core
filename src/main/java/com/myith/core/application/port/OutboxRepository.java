package com.myith.core.application.port;

import java.util.UUID;

public interface OutboxRepository {

    void save(String aggregateType, String aggregateId, UUID eventId,
              String eventType, String payloadJson);
}
