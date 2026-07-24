package com.myith.core.adapter.in.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 연결 레지스트리.
 * roadmapId → SseEmitter 매핑. 인스턴스 메모리에 유지.
 */
@Component
public class SseRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);
    private static final long SSE_TIMEOUT = 300_000L; // 5분

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long roadmapId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> emitters.remove(roadmapId));
        emitter.onTimeout(() -> emitters.remove(roadmapId));
        emitter.onError(e -> emitters.remove(roadmapId));
        emitters.put(roadmapId, emitter);
        return emitter;
    }

    public void send(Long roadmapId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(roadmapId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            emitters.remove(roadmapId);
            log.debug("SSE send failed for roadmap {}, removing emitter", roadmapId);
        }
    }

    public void complete(Long roadmapId) {
        SseEmitter emitter = emitters.remove(roadmapId);
        if (emitter != null) emitter.complete();
    }

    public boolean hasConnection(Long roadmapId) {
        return emitters.containsKey(roadmapId);
    }

    /** 연결 유지를 위한 주기적 ping */
    @Scheduled(fixedRate = 30000)
    public void ping() {
        emitters.forEach((roadmapId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException e) {
                emitters.remove(roadmapId);
            }
        });
    }
}
