package com.myith.core.application.presence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "myith.demo.enabled", havingValue = "true")
public class DemoNudgeRegistry {

    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    public void queue(Long userId) {
        pending.add(userId);
    }

    public boolean consume(Long userId) {
        return pending.remove(userId);
    }
}
