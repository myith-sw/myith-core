package com.myith.core.application.port;

import java.util.Optional;

public interface AiEnhancementResultStore {
    void save(String requestId, String resultJson, int ttlMinutes);
    Optional<String> find(String requestId);
}
