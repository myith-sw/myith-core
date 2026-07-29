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

    private final Map<Long, String> pending = new ConcurrentHashMap<>();

    public void queue(Long userId, String type) {
        pending.put(userId, type);
    }

    /**
     * 대기 중인 타입을 반환하고 제거한다. 없으면 null.
     */
    public String consume(Long userId) {
        return pending.remove(userId);
    }

    public static boolean isValidType(String type) {
        return VALID_TYPES.contains(type);
    }
}
