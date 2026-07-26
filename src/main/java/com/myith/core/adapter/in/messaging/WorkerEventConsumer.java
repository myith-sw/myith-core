package com.myith.core.adapter.in.messaging;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.adapter.out.persistence.ProcessedEventJpaRepository;
import com.myith.core.adapter.out.persistence.ProcessedEventJpaEntity;
import com.myith.core.application.port.AiEnhancementResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Worker fanout exchange 소비자 (D-12).
 * 모든 Core 인스턴스가 모든 메시지를 받는다.
 * SSE 레지스트리에 연결이 있으면 전달, 없으면 무시.
 * 상태 변경 이벤트는 processed_event로 멱등 처리.
 */
@Component
public class WorkerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkerEventConsumer.class);

    private final SseRegistry sseRegistry;
    private final ProcessedEventJpaRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final AiEnhancementResultStore aiEnhancementResultStore;

    public WorkerEventConsumer(SseRegistry sseRegistry,
                               ProcessedEventJpaRepository processedEventRepository,
                               ObjectMapper objectMapper,
                               AiEnhancementResultStore aiEnhancementResultStore) {
        this.sseRegistry = sseRegistry;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.aiEnhancementResultStore = aiEnhancementResultStore;
    }

    @RabbitListener(queues = "#{instanceQueue.name}")
    @Transactional
    public void handleWorkerEvent(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String eventIdStr = (String) message.getMessageProperties().getHeaders().get("eventId");

        if (eventType == null || eventIdStr == null) {
            log.warn("Worker event missing headers, skipping");
            return;
        }

        UUID eventId = UUID.fromString(eventIdStr);

        // 멱등 처리: 이미 처리한 이벤트면 스킵
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Event already processed: {}", eventId);
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            Long roadmapId = payload.has("roadmapId") ? payload.get("roadmapId").asLong() : null;

            switch (eventType) {
                case "RoadmapGenerationProgress" -> handleProgress(roadmapId, payload);
                case "CompetencyExtracted" -> handleCompetencyExtracted(eventId, roadmapId);
                case "JobProfileBuilt" -> handleJobProfileBuilt(eventId, payload);
                // LEGACY: Worker는 AiEnhancementCompleted로 대체 발행한다.
                // 큐에 남은 구 메시지 소진용으로만 유지. 2026-08 이후 제거 가능.
                case "StarFeedbackCompleted" -> handleStarFeedback(roadmapId, payload);
                case "AiEnhancementCompleted" -> handleAiEnhancementCompleted(eventId, payload);
                default -> log.debug("Unknown worker event type: {}", eventType);
            }
        } catch (IOException e) {
            log.error("Failed to parse worker event payload", e);
        }
    }

    private void handleProgress(Long roadmapId, JsonNode payload) {
        if (roadmapId == null || !sseRegistry.hasConnection(roadmapId)) return;
        sseRegistry.send(roadmapId, "progress", Map.of(
                "step", payload.has("step") ? payload.get("step").asText() : "",
                "percent", payload.has("percent") ? payload.get("percent").asInt() : 0
        ));
    }

    private void handleCompetencyExtracted(UUID eventId, Long roadmapId) {
        // 상태 변경 이벤트 → 멱등 기록
        processedEventRepository.save(ProcessedEventJpaEntity.create(eventId));
        log.info("CompetencyExtracted received for roadmap {}, will be handled by consistency scheduler", roadmapId);
        // 실제 재조립은 정합성 스케줄러(D-13)가 수행.
        // 여기서 직접 조립하지 않는 이유: 여러 인스턴스가 동시에 조립하면 충돌.
        // SSE 알림만 전달.
        if (roadmapId != null && sseRegistry.hasConnection(roadmapId)) {
            sseRegistry.send(roadmapId, "progress", Map.of("step", "분석 완료", "percent", 90));
        }
    }

    private void handleJobProfileBuilt(UUID eventId, JsonNode payload) {
        processedEventRepository.save(ProcessedEventJpaEntity.create(eventId));
        log.info("JobProfileBuilt received: {}", payload.has("jobCode") ? payload.get("jobCode").asText() : "unknown");
    }

    private void handleStarFeedback(Long roadmapId, JsonNode payload) {
        // 피드백 결과를 저장하지 않음 (F-8 스펙). SSE로 전달만.
        if (roadmapId != null && sseRegistry.hasConnection(roadmapId)) {
            sseRegistry.send(roadmapId, "starFeedback", payload.toString());
        }
    }

    private void handleAiEnhancementCompleted(UUID eventId, JsonNode payload) {
        processedEventRepository.save(ProcessedEventJpaEntity.create(eventId));

        String requestId = payload.has("requestId") ? payload.get("requestId").asText() : null;
        if (requestId == null) {
            log.warn("AiEnhancementCompleted missing requestId");
            return;
        }

        aiEnhancementResultStore.save(requestId, payload.toString(), 30);
        log.info("AiEnhancementCompleted stored for requestId={}", requestId);

        // SSE 알림
        Long roadmapId = payload.has("roadmapId") ? payload.get("roadmapId").asLong() : null;
        if (roadmapId != null && sseRegistry.hasConnection(roadmapId)) {
            sseRegistry.send(roadmapId, "aiEnhancementCompleted", Map.of("requestId", requestId));
        }
    }
}
