package com.myith.core.application.quest;

import com.myith.core.application.port.QuestRepository;
import com.myith.core.application.port.StarRecordRepository;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestSource;
import com.myith.core.domain.roadmap.QuestStatus;
import com.myith.core.domain.star.StarRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestWriteFacadeTest {

    private QuestDetailService questDetailService;
    private QuestRepository questRepository;
    private StarRecordRepository starRecordRepository;
    private QuestWriteFacade facade;

    private static final Long USER_ID = 1L;
    private static final Long QUEST_ID = 42L;

    @BeforeEach
    void setUp() {
        questDetailService = mock(QuestDetailService.class);
        questRepository = mock(QuestRepository.class);
        starRecordRepository = mock(StarRecordRepository.class);
        facade = new QuestWriteFacade(questDetailService, questRepository, starRecordRepository);
    }

    private Quest doneQuest() {
        return Quest.restore(QUEST_ID, 1L, "git", "axis1", 1, 0, "Git Quest",
                null, null, null, QuestSource.SKILL, QuestStatus.DONE,
                Instant.now(), 2, Instant.now(), Instant.now());
    }

    private Quest openQuest() {
        return Quest.restore(QUEST_ID, 1L, "git", "axis1", 1, 0, "Git Quest",
                null, null, null, QuestSource.SKILL, QuestStatus.OPEN,
                null, 1, Instant.now(), Instant.now());
    }

    // ===== Test 1: PUT → PATCH sequential → 200 + DONE + STAR =====

    @Test
    @DisplayName("1. PUT → PATCH { completed:true } 순차 → 200, 최종 DONE + STAR")
    void sequentialPutThenPatch() {
        QuestDetailService.SaveStarResult starResult =
                new QuestDetailService.SaveStarResult("OPEN", 1);
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenReturn(starResult);

        QuestDetailService.ToggleResult toggleResult = new QuestDetailService.ToggleResult(
                QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                BigDecimal.valueOf(50), "성장", 1, null, List.of());
        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenReturn(toggleResult);

        QuestDetailService.SaveStarResult sr = facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        assertEquals("OPEN", sr.status());

        QuestDetailService.ToggleResult tr = facade.toggleComplete(USER_ID, QUEST_ID, true, null);
        assertEquals(QuestStatus.DONE, tr.newStatus());
    }

    // ===== Test 2: PUT → PATCH { completed:true, star } sequential → 200 =====

    @Test
    @DisplayName("2. PUT → PATCH { completed:true, star } 순차 → 200, 최종 DONE + STAR")
    void sequentialPutThenPatchWithStar() {
        QuestDetailService.SaveStarResult starResult =
                new QuestDetailService.SaveStarResult("OPEN", 1);
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenReturn(starResult);

        QuestDetailService.StarDto starDto = new QuestDetailService.StarDto("S", "T", "A", "R");
        QuestDetailService.ToggleResult toggleResult = new QuestDetailService.ToggleResult(
                QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                BigDecimal.valueOf(50), "성장", 1, null, List.of());
        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), eq(starDto)))
                .thenReturn(toggleResult);

        facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        QuestDetailService.ToggleResult tr = facade.toggleComplete(USER_ID, QUEST_ID, true, starDto);
        assertEquals(QuestStatus.DONE, tr.newStatus());
    }

    // ===== Test 3: PUT and PATCH concurrent → both 200, no 409 =====

    @Test
    @DisplayName("3. PUT과 PATCH 동시 실행 → 둘 다 200, 409 발생 0")
    void concurrentPutAndPatch() throws Exception {
        // saveStar: 첫 호출은 약간의 지연으로 동시성 시뮬레이션
        AtomicInteger saveStarCalls = new AtomicInteger();
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    saveStarCalls.incrementAndGet();
                    Thread.sleep(10); // 약간의 지연
                    return new QuestDetailService.SaveStarResult("OPEN", 1);
                });

        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenReturn(new QuestDetailService.ToggleResult(
                        QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                        BigDecimal.valueOf(50), "성장", 1, null, List.of()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<Object> putFuture = executor.submit(() -> {
            ready.countDown();
            go.await();
            return facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        });

        Future<Object> patchFuture = executor.submit(() -> {
            ready.countDown();
            go.await();
            return facade.toggleComplete(USER_ID, QUEST_ID, true, null);
        });

        ready.await();
        go.countDown();

        // 둘 다 예외 없이 완료
        Object starRes = putFuture.get();
        Object toggleRes = patchFuture.get();
        assertNotNull(starRes);
        assertNotNull(toggleRes);

        executor.shutdown();
    }

    // ===== Test 4: PATCH 먼저 시작해도 동일하게 200 =====

    @Test
    @DisplayName("4. PATCH 먼저 시작 + PUT 동시 → 둘 다 200")
    void concurrentPatchFirstThenPut() throws Exception {
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenReturn(new QuestDetailService.SaveStarResult("OPEN", 1));

        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenAnswer(inv -> {
                    Thread.sleep(10); // PATCH가 먼저 시작되도록 약간 지연
                    return new QuestDetailService.ToggleResult(
                            QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                            BigDecimal.valueOf(50), "성장", 1, null, List.of());
                });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // PATCH를 먼저 submit
        Future<Object> patchFuture = executor.submit(() -> {
            ready.countDown();
            go.await();
            return facade.toggleComplete(USER_ID, QUEST_ID, true, null);
        });

        Future<Object> putFuture = executor.submit(() -> {
            ready.countDown();
            go.await();
            Thread.sleep(1); // PATCH 후 약간 후에 시작
            return facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        });

        ready.await();
        go.countDown();

        assertNotNull(patchFuture.get());
        assertNotNull(putFuture.get());

        executor.shutdown();
    }

    // ===== Test 5: 같은 내용 PUT 두 번 → 둘 다 200 =====

    @Test
    @DisplayName("5. 같은 내용 PUT 두 번 → 둘 다 200")
    void duplicatePutReturnsOk() {
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenReturn(new QuestDetailService.SaveStarResult("OPEN", 1));

        QuestDetailService.SaveStarResult r1 = facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        QuestDetailService.SaveStarResult r2 = facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");

        assertEquals("OPEN", r1.status());
        assertEquals("OPEN", r2.status());
        // saveStar가 2번 호출되지만 QuestDetailService 내부의 starFieldsMatch로 실제 쓰기는 스킵
    }

    // ===== Test 6: 동시 호출 20회 반복 — 실패 0 =====

    @Test
    @DisplayName("6. 동시 호출 20회 반복 → 실패 0")
    void concurrentRepeated20Times() throws Exception {
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenReturn(new QuestDetailService.SaveStarResult("OPEN", 1));

        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenReturn(new QuestDetailService.ToggleResult(
                        QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                        BigDecimal.valueOf(50), "성장", 1, null, List.of()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < 20; i++) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Future<Object> putFuture = executor.submit(() -> {
                ready.countDown();
                go.await();
                return facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
            });

            Future<Object> patchFuture = executor.submit(() -> {
                ready.countDown();
                go.await();
                return facade.toggleComplete(USER_ID, QUEST_ID, true, null);
            });

            ready.await();
            go.countDown();

            try {
                putFuture.get();
                patchFuture.get();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        }

        assertEquals(0, failures.get(), "20회 반복 중 실패가 발생했다");
        executor.shutdown();
    }

    // ===== Test: 재시도로 복구 성공 =====

    @Test
    @DisplayName("재시도: 첫 호출 실패 → 두 번째 성공")
    void retryOnOptimisticLockFailure() {
        AtomicInteger calls = new AtomicInteger();
        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenAnswer(inv -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new ObjectOptimisticLockingFailureException("Quest", null);
                    }
                    return new QuestDetailService.ToggleResult(
                            QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                            BigDecimal.valueOf(50), "성장", 1, null, List.of());
                });

        QuestDetailService.ToggleResult result = facade.toggleComplete(USER_ID, QUEST_ID, true, null);
        assertEquals(QuestStatus.DONE, result.newStatus());
        assertEquals(2, calls.get());
    }

    // ===== Test: 수렴 확인 — 3회 실패 후 목표 달성 확인 =====

    @Test
    @DisplayName("수렴 확인: 3회 실패 후 이미 DONE → 200")
    void convergenceCheckAfterExhaustedRetries() {
        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenThrow(new QuestManageService.OptimisticLockConflictException("conflict"));

        when(questRepository.findById(QUEST_ID))
                .thenReturn(Optional.of(doneQuest()));

        QuestDetailService.ToggleResult convergenceResult = new QuestDetailService.ToggleResult(
                QUEST_ID, QuestStatus.DONE, 2, Instant.now(), List.of(),
                BigDecimal.valueOf(50), "성장", 1, null, List.of());
        when(questDetailService.buildToggleResultFromCurrentState(USER_ID, QUEST_ID))
                .thenReturn(convergenceResult);

        QuestDetailService.ToggleResult result = facade.toggleComplete(USER_ID, QUEST_ID, true, null);
        assertEquals(QuestStatus.DONE, result.newStatus());
    }

    // ===== Test: saveStar 수렴 확인 =====

    @Test
    @DisplayName("saveStar 수렴 확인: 3회 실패 후 내용 동일 → 200")
    void saveStarConvergenceCheck() {
        when(questDetailService.saveStar(eq(USER_ID), eq(QUEST_ID), any(), any(), any(), any()))
                .thenThrow(new QuestManageService.OptimisticLockConflictException("conflict"));

        StarRecord existing = StarRecord.create(QUEST_ID, USER_ID, "S", "T", "A", "R");
        when(starRecordRepository.findByQuestId(QUEST_ID))
                .thenReturn(Optional.of(existing));
        when(questRepository.findById(QUEST_ID))
                .thenReturn(Optional.of(openQuest()));

        QuestDetailService.SaveStarResult result = facade.saveStar(USER_ID, QUEST_ID, "S", "T", "A", "R");
        assertEquals("OPEN", result.status());
    }

    // ===== Test: 수렴 실패 → 409 =====

    @Test
    @DisplayName("수렴 실패: 3회 실패 + 상태 불일치 → 409")
    void convergenceFailsThrows409() {
        when(questDetailService.toggleComplete(eq(USER_ID), eq(QUEST_ID), eq(true), isNull()))
                .thenThrow(new QuestManageService.OptimisticLockConflictException("conflict"));

        when(questRepository.findById(QUEST_ID))
                .thenReturn(Optional.of(openQuest())); // 아직 OPEN → completed=true 와 불일치

        assertThrows(QuestManageService.OptimisticLockConflictException.class,
                () -> facade.toggleComplete(USER_ID, QUEST_ID, true, null));
    }
}
