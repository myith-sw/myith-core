package com.myith.core.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.AiEnhancementResultStore;
import com.myith.core.application.port.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiEnhancementControllerTest {

    private StubResultStore resultStore;
    private StubOutboxRepository outboxRepository;
    private AiEnhancementController controller;

    @BeforeEach
    void setUp() {
        resultStore = new StubResultStore();
        outboxRepository = new StubOutboxRepository();
        controller = new AiEnhancementController(resultStore, outboxRepository, new ObjectMapper(), 60);
    }

    // ── Bug ① requestId 정규화 ──

    @Test
    @DisplayName("접두어 붙은 requestId로 조회하면 순수 UUID 키로 저장된 결과를 찾는다")
    void prefixedRequestIdMatchesStoredUuidKey() {
        UUID uuid = UUID.randomUUID();
        String completedPayload = """
                {"status":"COMPLETED","enhancedStar":{"situation":"s","task":"t","action":"a","result":"r"}}""";
        resultStore.save(uuid.toString(), completedPayload, 30);

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(200, response.getStatusCode().value());
        // body에 COMPLETED 상태가 포함되어야 한다
        String body = response.getBody().toString();
        assertTrue(body.contains("COMPLETED"), "should return COMPLETED, got: " + body);
    }

    @Test
    @DisplayName("접두어 없는 순수 UUID로도 정상 조회된다")
    void rawUuidAlsoWorks() {
        UUID uuid = UUID.randomUUID();
        resultStore.save(uuid.toString(), """
                {"status":"COMPLETED","enhancedStar":{"situation":"s","task":"t","action":"a","result":"r"}}""", 30);

        var response = controller.getResult(1L, uuid.toString());

        assertEquals(200, response.getStatusCode().value());
    }

    // ── Bug ② questId 접두사 ──

    @Test
    @DisplayName("COMPLETED 결과에 questId가 숫자로 있으면 qst_ 접두사가 붙어야 한다")
    void completedResult_questIdHasPrefix() {
        UUID uuid = UUID.randomUUID();
        resultStore.save(uuid.toString(), """
                {"status":"COMPLETED","questId":5,"enhancedStar":{"situation":"s","task":"t","action":"a","result":"r"}}""", 30);

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody().toString();
        assertTrue(body.contains("qst_5"), "questId should be prefixed, got: " + body);
        assertFalse(body.contains("\"5\""), "raw numeric questId should not appear");
    }

    @Test
    @DisplayName("FAILED 결과에 questId가 없으면 null이어야 한다 (qst_0 아님)")
    void failedResult_questIdIsNull() {
        UUID uuid = UUID.randomUUID();
        resultStore.save(uuid.toString(), """
                {"status":"FAILED","errorCode":"AI_PROVIDER_TIMEOUT"}""", 30);

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody().toString();
        assertTrue(body.contains("FAILED"), body);
        assertFalse(body.contains("qst_0"), "questId should be null, not qst_0, got: " + body);
    }

    // ── Bug ③ 타임아웃 ──

    @Test
    @DisplayName("결과 없음 + outbox 존재 + 타임아웃 이내 → PROCESSING")
    void withinTimeout_returnsProcessing() {
        UUID uuid = UUID.randomUUID();
        outboxRepository.setCreatedAt(uuid, Instant.now().minusSeconds(30));

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("PROCESSING"));
    }

    @Test
    @DisplayName("결과 없음 + outbox 존재 + 타임아웃 초과 → FAILED + AI_TIMEOUT")
    void afterTimeout_returnsFailed() {
        UUID uuid = UUID.randomUUID();
        outboxRepository.setCreatedAt(uuid, Instant.now().minusSeconds(120));

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody().toString();
        assertTrue(body.contains("FAILED"), body);
        assertTrue(body.contains("AI_TIMEOUT"), body);
    }

    @Test
    @DisplayName("결과 없음 + outbox에 event_id 없음 → 404")
    void noOutboxEntry_returns404() {
        UUID uuid = UUID.randomUUID();

        var response = controller.getResult(1L, "aie_" + uuid);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("잘못된 형식의 requestId → 400")
    void invalidRequestId_returns400() {
        var response = controller.getResult(1L, "abc");
        assertEquals(400, response.getStatusCode().value());

        var response2 = controller.getResult(1L, "aie_");
        assertEquals(400, response2.getStatusCode().value());
    }

    // ── Stubs ──

    private static class StubResultStore implements AiEnhancementResultStore {
        private final java.util.concurrent.ConcurrentHashMap<String, String> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(String requestId, String resultJson, int ttlMinutes) {
            store.put(requestId, resultJson);
        }

        @Override
        public Optional<String> find(String requestId) {
            return Optional.ofNullable(store.get(requestId));
        }
    }

    private static class StubOutboxRepository implements OutboxRepository {
        private final java.util.concurrent.ConcurrentHashMap<UUID, Instant> createdAts = new java.util.concurrent.ConcurrentHashMap<>();

        void setCreatedAt(UUID eventId, Instant createdAt) {
            createdAts.put(eventId, createdAt);
        }

        @Override
        public Optional<Instant> findCreatedAtByEventId(UUID eventId) {
            return Optional.ofNullable(createdAts.get(eventId));
        }

        @Override public void save(String aggregateType, String aggregateId, UUID eventId, String eventType, String payloadJson) {}
        @Override public java.util.List<OutboxEvent> findPending() { return java.util.List.of(); }
        @Override public void markPublished(Long id) {}
        @Override public void incrementRetry(Long id) {}
        @Override public void markFailed(Long id) {}
        @Override public boolean existsRecentEvent(String aggregateId, String eventType, Instant since) { return false; }
    }
}
