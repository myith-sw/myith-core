package com.myith.core.adapter.out.cache;

import com.myith.core.application.port.AiEnhancementResultStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAiEnhancementResultStore implements AiEnhancementResultStore {

    private final ConcurrentHashMap<String, TimedEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String requestId, String resultJson, int ttlMinutes) {
        Instant expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);
        store.put(requestId, new TimedEntry(resultJson, expiresAt));
    }

    @Override
    public Optional<String> find(String requestId) {
        TimedEntry entry = store.get(requestId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(requestId);
            return Optional.empty();
        }
        return Optional.of(entry.json());
    }

    private record TimedEntry(String json, Instant expiresAt) {}
}
