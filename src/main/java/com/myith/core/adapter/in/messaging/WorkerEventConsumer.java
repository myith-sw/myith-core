package com.myith.core.adapter.in.messaging;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.adapter.out.persistence.ProcessedEventJpaRepository;
import com.myith.core.adapter.out.persistence.ProcessedEventJpaEntity;
import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.common.IdCodec;
import com.myith.core.domain.roadmap.GenerationState;
import com.myith.core.domain.roadmap.Roadmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final RoadmapRepository roadmapRepository;
    private final JobProfileReadRepository jobProfileReadRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final RoadmapCreateService roadmapCreateService;

    public WorkerEventConsumer(SseRegistry sseRegistry,
                               ProcessedEventJpaRepository processedEventRepository,
                               ObjectMapper objectMapper,
                               AiEnhancementResultStore aiEnhancementResultStore,
                               RoadmapRepository roadmapRepository,
                               JobProfileReadRepository jobProfileReadRepository,
                               DiagnosisRepository diagnosisRepository,
                               RoadmapCreateService roadmapCreateService) {
        this.sseRegistry = sseRegistry;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.aiEnhancementResultStore = aiEnhancementResultStore;
        this.roadmapRepository = roadmapRepository;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.roadmapCreateService = roadmapCreateService;
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

    /**
     * CompetencyExtracted 수신 → 즉시 조립 (정상 경로).
     * 현재 배포는 Core 인스턴스 1대이므로 동시 조립 충돌 없음.
     * 다중 인스턴스로 전환하면 분산 락(ex. SELECT FOR UPDATE)이 필요하다.
     * 조립 실패 시 ANALYZING을 유지해서 ConsistencyScheduler가 안전망으로 받는다.
     */
    private void handleCompetencyExtracted(UUID eventId, Long roadmapId) {
        processedEventRepository.save(ProcessedEventJpaEntity.create(eventId));

        if (roadmapId == null) {
            log.warn("CompetencyExtracted missing roadmapId, eventId={}", eventId);
            return;
        }

        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
        if (roadmap == null) {
            log.warn("CompetencyExtracted: roadmap {} not found", roadmapId);
            return;
        }

        // 이미 READY/FAILED면 중복 조립 방지
        if (roadmap.getGenerationState() != GenerationState.ANALYZING) {
            log.info("CompetencyExtracted: roadmap {} already {}, skipping",
                    roadmapId, roadmap.getGenerationState());
            return;
        }

        // SSE 진행률
        if (sseRegistry.hasConnection(roadmapId)) {
            sseRegistry.send(roadmapId, "progress", Map.of("step", "분석 완료", "percent", 90));
        }

        // job_profile 조회
        JobProfileData profile = jobProfileReadRepository
                .findByJobCodeAndVersion(roadmap.getJobCode(), roadmap.getProfileVersion())
                .orElse(null);

        if (profile == null) {
            log.error("CompetencyExtracted: job profile not found for roadmap {}", roadmapId);
            roadmap.markFailed();
            roadmapRepository.save(roadmap);
            if (sseRegistry.hasConnection(roadmapId)) {
                sseRegistry.send(roadmapId, "error", Map.of(
                        "code", "GENERATION_FAILED", "message", "직무 프로필을 찾을 수 없습니다."));
                sseRegistry.complete(roadmapId);
            }
            return;
        }

        // 자가진단 조회
        List<RoadmapCreateService.AnswerDto> answers = diagnosisRepository
                .findByRoadmapId(roadmapId).stream()
                .map(d -> new RoadmapCreateService.AnswerDto(d.getSkillCode(), d.getMastery()))
                .toList();

        try {
            // 조립 (user_competency 반영은 assembleAndSnapshot 내부에서 수행)
            roadmapCreateService.assembleAndSnapshot(roadmap, profile, answers);
            log.info("CompetencyExtracted: roadmap {} assembled immediately", roadmapId);

            // SSE done
            if (sseRegistry.hasConnection(roadmapId)) {
                sseRegistry.send(roadmapId, "done",
                        Map.of("roadmapId", IdCodec.encode(roadmapId, "rmp_")));
                sseRegistry.complete(roadmapId);
            }
        } catch (Exception e) {
            // 조립 실패 → ANALYZING 유지. ConsistencyScheduler가 안전망으로 재시도.
            log.error("CompetencyExtracted: assembly failed for roadmap {}, leaving ANALYZING for scheduler",
                    roadmapId, e);
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
            sseRegistry.send(roadmapId, "aiEnhancementCompleted", Map.of("requestId", "aie_" + requestId));
        }
    }
}
