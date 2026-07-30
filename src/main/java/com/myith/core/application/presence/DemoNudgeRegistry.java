package com.myith.core.application.presence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "myith.demo.enabled", havingValue = "true")
public class DemoNudgeRegistry {

    private static final Set<String> VALID_TYPES = Set.of("ANNOYING", "UPSET", "ABSENCE_48H");

    public record Pending(String type, String message) {}

    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    public void queue(Long userId, String type, String message) {
        pending.put(userId, new Pending(type, message));
    }

    /**
     * 대기 중인 Pending을 반환하고 제거한다. 없으면 null.
     */
    public Pending consume(Long userId) {
        return pending.remove(userId);
    }

    public static boolean isValidType(String type) {
        return VALID_TYPES.contains(type);
    }
}
