package com.myith.core.application.quest;

import com.myith.core.application.port.QuestRepository;
import com.myith.core.application.port.StarRecordRepository;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestStatus;
import com.myith.core.domain.star.StarRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 퀘스트 쓰기 작업의 비트랜잭션 파사드.
 * <p>
 * 같은 questId에 대한 쓰기(saveStar, toggleComplete)를 프로세스 내 striped lock으로
 * 직렬화하고, 낙관적 락 충돌 시 최대 3회 재시도한다.
 * 재시도 후에도 실패하면 의도 수렴(convergence) 확인을 거쳐 불필요한 409를 제거한다.
 * <p>
 * 주의: 단일 인스턴스에서만 유효하다. 다중 인스턴스 환경에서는 DB-level 락 또는
 * 분산 락이 필요하며, 그때도 조치 b(재시도)와 c(수렴 확인)가 안전망으로 작동한다.
 */
@Component
public class QuestWriteFacade {

    private static final Logger log = LoggerFactory.getLogger(QuestWriteFacade.class);
    private static final int STRIPE_COUNT = 64;
    private static final int MAX_RETRIES = 3;
    private static final long LOCK_TIMEOUT_MS = 5_000;
    private static final long RETRY_BASE_MS = 50;
    private static final long RETRY_JITTER_BOUND_MS = 31; // 0~30ms

    private final ReentrantLock[] stripes;
    private final QuestDetailService questDetailService;
    private final QuestRepository questRepository;
    private final StarRecordRepository starRecordRepository;

    public QuestWriteFacade(QuestDetailService questDetailService,
                            QuestRepository questRepository,
                            StarRecordRepository starRecordRepository) {
        this.questDetailService = questDetailService;
        this.questRepository = questRepository;
        this.starRecordRepository = starRecordRepository;
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripes[i] = new ReentrantLock();
        }
    }

    private ReentrantLock lockFor(Long questId) {
        int index = Math.abs((int) (questId ^ (questId >>> 16))) % STRIPE_COUNT;
        return stripes[index];
    }

    /**
     * PUT /api/quests/{id}/star — 락 + 재시도 + 수렴 확인
     */
    public QuestDetailService.SaveStarResult saveStar(Long userId, Long questId,
                                                       String situation, String task,
                                                       String action, String result) {
        ReentrantLock lock = lockFor(questId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuestManageService.OptimisticLockConflictException("Interrupted while acquiring quest lock");
        }

        if (!acquired) {
            // 락 타임아웃 — 재시도 경로로 진행
            return saveStarWithRetry(userId, questId, situation, task, action, result);
        }

        try {
            return saveStarWithRetry(userId, questId, situation, task, action, result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * PATCH /api/quests/{id}/complete — 락 + 재시도 + 수렴 확인
     */
    public QuestDetailService.ToggleResult toggleComplete(Long userId, Long questId,
                                                           boolean completed,
                                                           QuestDetailService.StarDto star) {
        ReentrantLock lock = lockFor(questId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuestManageService.OptimisticLockConflictException("Interrupted while acquiring quest lock");
        }

        if (!acquired) {
            return toggleCompleteWithRetry(userId, questId, completed, star);
        }

        try {
            return toggleCompleteWithRetry(userId, questId, completed, star);
        } finally {
            lock.unlock();
        }
    }

    private QuestDetailService.SaveStarResult saveStarWithRetry(Long userId, Long questId,
                                                                  String situation, String task,
                                                                  String action, String result) {
        ObjectOptimisticLockingFailureException lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return questDetailService.saveStar(userId, questId, situation, task, action, result);
            } catch (ObjectOptimisticLockingFailureException | QuestManageService.OptimisticLockConflictException e) {
                lastEx = (e instanceof ObjectOptimisticLockingFailureException oe) ? oe
                        : new ObjectOptimisticLockingFailureException("wrapped", e);
                log.warn("saveStar optimistic lock conflict: questId={}, attempt={}/{}",
                        questId, attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES) {
                    sleepWithJitter();
                }
            }
        }

        // 조치 c: 수렴 확인 — 저장하려던 내용이 이미 DB에 있으면 성공으로 간주
        StarRecord existing = starRecordRepository.findByQuestId(questId).orElse(null);
        if (existing != null && fieldsMatch(existing, situation, task, action, result)) {
            log.warn("saveStar convergence check passed: questId={} — content already matches", questId);
            Quest quest = questRepository.findById(questId)
                    .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));
            return new QuestDetailService.SaveStarResult(quest.getStatus().toApiName(), quest.getVersion());
        }

        throw new QuestManageService.OptimisticLockConflictException(
                "Concurrent quest update detected after " + MAX_RETRIES + " retries");
    }

    private QuestDetailService.ToggleResult toggleCompleteWithRetry(Long userId, Long questId,
                                                                      boolean completed,
                                                                      QuestDetailService.StarDto star) {
        ObjectOptimisticLockingFailureException lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return questDetailService.toggleComplete(userId, questId, completed, star);
            } catch (ObjectOptimisticLockingFailureException | QuestManageService.OptimisticLockConflictException e) {
                lastEx = (e instanceof ObjectOptimisticLockingFailureException oe) ? oe
                        : new ObjectOptimisticLockingFailureException("wrapped", e);
                log.warn("toggleComplete optimistic lock conflict: questId={}, attempt={}/{}",
                        questId, attempt, MAX_RETRIES);
                if (attempt < MAX_RETRIES) {
                    sleepWithJitter();
                }
            }
        }

        // 조치 c: 수렴 확인 — 요청한 상태가 이미 달성되어 있으면 성공으로 간주
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        if (completed && quest.getStatus().isCompleted()) {
            log.warn("toggleComplete convergence check passed: questId={} — already completed ({})",
                    questId, quest.getStatus());
            return questDetailService.buildToggleResultFromCurrentState(userId, questId);
        }

        throw new QuestManageService.OptimisticLockConflictException(
                "Concurrent quest update detected after " + MAX_RETRIES + " retries");
    }

    private void sleepWithJitter() {
        try {
            long delay = RETRY_BASE_MS + ThreadLocalRandom.current().nextLong(RETRY_JITTER_BOUND_MS);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean fieldsMatch(StarRecord record, String situation, String task,
                                 String action, String result) {
        return normalizedEquals(record.getSituation(), situation)
                && normalizedEquals(record.getTask(), task)
                && normalizedEquals(record.getAction(), action)
                && normalizedEquals(record.getResult(), result);
    }

    private boolean normalizedEquals(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        return Objects.equals(na, nb);
    }

    private String normalize(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
