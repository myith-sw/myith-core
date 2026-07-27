package com.myith.core.application.quest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.dashboard.SnapshotService;
import com.myith.core.application.port.JobProfileReadRepository;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.QuestRepository;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.domain.roadmap.*;
import com.myith.core.domain.roadmap.RoadmapAssembler.Prerequisite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class QuestManageService {

    private final QuestRepository questRepository;
    private final RoadmapRepository roadmapRepository;
    private final SnapshotService snapshotService;
    private final JobProfileReadRepository jobProfileReadRepository;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public QuestManageService(QuestRepository questRepository,
                              RoadmapRepository roadmapRepository,
                              SnapshotService snapshotService,
                              JobProfileReadRepository jobProfileReadRepository,
                              ObjectMapper objectMapper,
                              @Value("${policy.optimistic-lock.max-retries}") int maxRetries) {
        this.questRepository = questRepository;
        this.roadmapRepository = roadmapRepository;
        this.snapshotService = snapshotService;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
    }

    /**
     * 퀘스트 순서 변경 (낙관적 락 + 재시도).
     * targetLevel과 targetIndex로 이동한다.
     */
    @Transactional
    public void reorderQuest(Long userId, Long roadmapId, Long questId,
                             int targetLevel, int targetIndex) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                doReorder(roadmapId, questId, targetLevel, targetIndex);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == maxRetries) {
                    throw new OptimisticLockConflictException("Quest order update conflict after " + maxRetries + " retries");
                }
            }
        }
    }

    private void doReorder(Long roadmapId, Long questId, int targetLevel, int targetIndex) {
        List<Quest> quests = questRepository.findByRoadmapId(roadmapId);
        Quest target = quests.stream()
                .filter(q -> q.getId().equals(questId))
                .findFirst()
                .orElseThrow(() -> new QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));

        // 같은 레벨 퀘스트들을 순서대로 정렬
        List<Quest> levelQuests = quests.stream()
                .filter(q -> q.getLevel() == targetLevel && !q.getId().equals(questId))
                .sorted(Comparator.comparingInt(Quest::getOrderInLevel))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        // 타겟 퀘스트를 새 위치에 삽입
        Quest moved = Quest.restore(
                target.getId(), target.getRoadmapId(), target.getSkillCode(), target.getAxisCode(),
                targetLevel, targetIndex, target.getTitle(), target.getCompletionCriteria(),
                target.getNcsUnitCode(), target.getSource(), target.getStatus(),
                target.getCompletedAt(), target.getVersion(), target.getCreatedAt(), target.getUpdatedAt()
        );

        int insertAt = Math.min(targetIndex, levelQuests.size());
        levelQuests.add(insertAt, moved);

        // order_in_level 재부여
        for (int i = 0; i < levelQuests.size(); i++) {
            Quest q = levelQuests.get(i);
            Quest updated = Quest.restore(
                    q.getId(), q.getRoadmapId(), q.getSkillCode(), q.getAxisCode(),
                    targetLevel, i, q.getTitle(), q.getCompletionCriteria(),
                    q.getNcsUnitCode(), q.getSource(), q.getStatus(),
                    q.getCompletedAt(), q.getVersion(), q.getCreatedAt(), q.getUpdatedAt()
            );
            questRepository.save(updated);
        }

        // 퀘스트 상태 재계산
        recomputeQuestStatuses(roadmapId, roadmap.getJobCode(), roadmap.getProfileVersion());
    }

    /**
     * 사용자 정의 퀘스트 추가.
     * 추가 후 스냅샷 재계산 (완료율 변동).
     */
    @Transactional
    public Quest addCustomQuest(Long userId, Long roadmapId, String title, String axisCode, int level) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        // 해당 레벨 마지막 순서
        List<Quest> levelQuests = questRepository.findByRoadmapId(roadmapId).stream()
                .filter(q -> q.getLevel() == level)
                .toList();
        int nextOrder = levelQuests.size();

        Quest quest = Quest.createCustomQuest(roadmapId, axisCode, level, nextOrder, title);
        Quest saved = questRepository.save(quest);

        // 사이드이펙트: 퀘스트 추가 -> 완료율 변동 -> 스냅샷 재계산
        snapshotService.recalculate(roadmapId);
        recomputeQuestStatuses(roadmapId, roadmap.getJobCode(), roadmap.getProfileVersion());

        return saved;
    }

    /**
     * 퀘스트 삭제 (CUSTOM만).
     * 삭제 후 스냅샷 재계산.
     */
    @Transactional
    public void deleteQuest(Long userId, Long roadmapId, Long questId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestNotFoundException(questId));

        if (!quest.getRoadmapId().equals(roadmapId)) {
            throw new QuestNotFoundException(questId);
        }

        if (quest.getSource() != QuestSource.CUSTOM) {
            throw new CannotDeleteNonCustomQuestException(questId);
        }

        questRepository.delete(quest);

        // 사이드이펙트: 퀘스트 삭제 -> 완료율 변동 -> 스냅샷 재계산
        snapshotService.recalculate(roadmapId);
        recomputeQuestStatuses(roadmapId, roadmap.getJobCode(), roadmap.getProfileVersion());
    }

    private void recomputeQuestStatuses(Long roadmapId, String jobCode, int profileVersion) {
        List<Quest> allQuests = questRepository.findByRoadmapId(roadmapId);

        JobProfileData profile = jobProfileReadRepository
                .findByJobCodeAndVersion(jobCode, profileVersion)
                .orElse(null);
        if (profile == null) return;

        List<Prerequisite> prerequisites = parsePrerequisites(profile.prerequisites());

        List<QuestUnlockPolicy.StatusChange> changes =
                QuestUnlockPolicy.recompute(allQuests, prerequisites);

        for (QuestUnlockPolicy.StatusChange change : changes) {
            Quest q = allQuests.stream()
                    .filter(quest -> quest.getId().equals(change.questId()))
                    .findFirst().orElse(null);
            if (q == null) continue;

            Quest changed = Quest.restore(q.getId(), q.getRoadmapId(), q.getSkillCode(), q.getAxisCode(),
                    q.getLevel(), q.getOrderInLevel(), q.getTitle(), q.getCompletionCriteria(),
                    q.getNcsUnitCode(), q.getSource(), change.newStatus(), q.getCompletedAt(),
                    q.getVersion(), q.getCreatedAt(), Instant.now());
            questRepository.save(changed);
        }
    }

    private List<Prerequisite> parsePrerequisites(String prerequisitesJson) {
        if (prerequisitesJson == null || prerequisitesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(prerequisitesJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    // ===== Exceptions =====

    public static class QuestNotFoundException extends RuntimeException {
        public QuestNotFoundException(Long id) { super("Quest not found: " + id); }
    }

    public static class CannotDeleteNonCustomQuestException extends RuntimeException {
        public CannotDeleteNonCustomQuestException(Long id) { super("Only CUSTOM quests can be deleted: " + id); }
    }

    public static class OptimisticLockConflictException extends RuntimeException {
        public OptimisticLockConflictException(String msg) { super(msg); }
    }

    public static class QuestLockedException extends RuntimeException {
        public QuestLockedException(Long id) { super("Quest is locked: " + id); }
    }
}
