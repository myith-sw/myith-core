package com.myith.core.application.quest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.dashboard.SnapshotService;
import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.NcsReadRepository.CertificationData;
import com.myith.core.application.port.NcsReadRepository.NcsUnitData;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestStatus;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.roadmap.RoadmapAssembler.Prerequisite;
import com.myith.core.domain.star.StarRecord;
import com.myith.core.application.roadmap.RoadmapQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestDetailService {

    private final QuestRepository questRepository;
    private final RoadmapRepository roadmapRepository;
    private final StarRecordRepository starRecordRepository;
    private final OutboxRepository outboxRepository;
    private final SnapshotService snapshotService;
    private final NcsReadRepository ncsReadRepository;
    private final JobProfileReadRepository jobProfileReadRepository;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public QuestDetailService(QuestRepository questRepository,
                              RoadmapRepository roadmapRepository,
                              StarRecordRepository starRecordRepository,
                              OutboxRepository outboxRepository,
                              SnapshotService snapshotService,
                              NcsReadRepository ncsReadRepository,
                              JobProfileReadRepository jobProfileReadRepository,
                              ObjectMapper objectMapper,
                              @Value("${policy.optimistic-lock.max-retries}") int maxRetries) {
        this.questRepository = questRepository;
        this.roadmapRepository = roadmapRepository;
        this.starRecordRepository = starRecordRepository;
        this.outboxRepository = outboxRepository;
        this.snapshotService = snapshotService;
        this.ncsReadRepository = ncsReadRepository;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
    }

    @Transactional(readOnly = true)
    public QuestDetailDto getDetail(Long userId, Long questId) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(quest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(quest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        // NCS 단위 + 자격 조회 (DB에서만, NCS API 호출 금지 C-1)
        NcsUnitDto ncsUnit = null;
        List<CertDto> certifications = List.of();
        if (quest.getNcsUnitCode() != null) {
            ncsUnit = ncsReadRepository.findUnitByCode(quest.getNcsUnitCode())
                    .map(e -> new NcsUnitDto(e.code(), e.name(), e.description()))
                    .orElse(null);
            certifications = ncsReadRepository.findCertificationsByUnitCode(quest.getNcsUnitCode()).stream()
                    .map(e -> new CertDto(e.certName()))
                    .toList();
        }

        // STAR 조회
        StarDto star = starRecordRepository.findByQuestId(questId)
                .map(r -> new StarDto(r.getSituation(), r.getTask(), r.getAction(), r.getResult()))
                .orElse(null);

        return new QuestDetailDto(quest.getId(), quest.getTitle(), quest.getAxisCode(),
                quest.getLevel(), quest.getStatus().name(), ncsUnit, certifications,
                quest.getCompletionCriteria(), star);
    }

    /**
     * 퀘스트 완료 토글.
     * 사이드이펙트:
     *   1. 완료 시 선행관계의 후행 퀘스트 LOCKED → OPEN 해제
     *   2. dashboard_snapshot 재계산
     */
    @Transactional
    public void toggleComplete(Long userId, Long questId, boolean completed, long version) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(quest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(quest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        // 낙관적 락 검증
        if (quest.getVersion() != version) {
            throw new QuestManageService.OptimisticLockConflictException(
                    "Quest version mismatch: expected " + version + ", actual " + quest.getVersion());
        }

        QuestStatus newStatus = completed ? QuestStatus.DONE : QuestStatus.OPEN;
        Instant completedAt = completed ? Instant.now() : null;

        Quest updated = Quest.restore(
                quest.getId(), quest.getRoadmapId(), quest.getSkillCode(), quest.getAxisCode(),
                quest.getLevel(), quest.getOrderInLevel(), quest.getTitle(),
                quest.getCompletionCriteria(), quest.getNcsUnitCode(),
                quest.getSource(), newStatus, completedAt,
                quest.getVersion(), quest.getCreatedAt(), Instant.now()
        );

        try {
            questRepository.save(updated);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new QuestManageService.OptimisticLockConflictException("Concurrent quest update detected");
        }

        // 사이드이펙트 1: 완료 시 후행 퀘스트 LOCKED 해제
        if (completed) {
            unlockDependentQuests(roadmap);
        }

        // 사이드이펙트 2: 스냅샷 재계산
        snapshotService.recalculate(quest.getRoadmapId());
    }

    /**
     * 선행 퀘스트가 완료되면 후행 LOCKED 퀘스트를 OPEN으로 전환.
     * job_profile의 prerequisites를 읽어서 실제 선후관계를 확인한다.
     */
    private void unlockDependentQuests(Roadmap roadmap) {
        List<Quest> allQuests = questRepository.findByRoadmapId(roadmap.getId());

        // 선후관계 조회
        JobProfileData profile = jobProfileReadRepository
                .findByJobCodeAndVersion(roadmap.getJobCode(), roadmap.getProfileVersion())
                .orElse(null);
        if (profile == null) return;

        List<Prerequisite> prerequisites = parsePrerequisites(profile.prerequisites());
        if (prerequisites.isEmpty()) return;

        // skillCode → 선행으로 필요한 스킬 목록
        Map<String, Set<String>> prereqMap = new HashMap<>();
        for (Prerequisite p : prerequisites) {
            prereqMap.computeIfAbsent(p.to(), k -> new HashSet<>()).add(p.from());
        }

        // 현재 완료된 스킬 목록
        Set<String> completedSkills = allQuests.stream()
                .filter(q -> q.getSkillCode() != null && q.getStatus().isCompleted())
                .map(Quest::getSkillCode)
                .collect(Collectors.toSet());

        // LOCKED 퀘스트 중 선행이 모두 완료된 것만 OPEN으로 전환
        for (Quest q : allQuests) {
            if (q.getStatus() != QuestStatus.LOCKED || q.getSkillCode() == null) continue;

            Set<String> required = prereqMap.getOrDefault(q.getSkillCode(), Set.of());
            if (completedSkills.containsAll(required)) {
                Quest unlocked = Quest.restore(
                        q.getId(), q.getRoadmapId(), q.getSkillCode(), q.getAxisCode(),
                        q.getLevel(), q.getOrderInLevel(), q.getTitle(),
                        q.getCompletionCriteria(), q.getNcsUnitCode(),
                        q.getSource(), QuestStatus.OPEN, null,
                        q.getVersion(), q.getCreatedAt(), Instant.now()
                );
                questRepository.save(unlocked);
            }
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

    // STAR 저장/갱신
    @Transactional
    public void saveStar(Long userId, Long questId,
                         String situation, String task, String action, String result) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(quest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(quest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        StarRecord existing = starRecordRepository.findByQuestId(questId).orElse(null);
        if (existing != null) {
            existing.update(situation, task, action, result);
            starRecordRepository.save(existing);
        } else {
            StarRecord record = StarRecord.create(questId, userId, situation, task, action, result);
            starRecordRepository.save(record);
        }
    }

    // STAR 피드백 요청 → Outbox 발행 → 202
    @Transactional
    public UUID requestStarFeedback(Long userId, Long starRecordId) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "starRecordId", starRecordId,
                "userId", userId
        );
        try {
            outboxRepository.save("StarRecord", String.valueOf(starRecordId),
                    eventId, "StarFeedbackRequested", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize star feedback payload", e);
        }
        return eventId;
    }

    // ===== DTOs =====
    public record QuestDetailDto(Long questId, String title, String axisName, int level,
                                 String status, NcsUnitDto ncsUnit, List<CertDto> certifications,
                                 String completionCriteria, StarDto star) {}
    public record NcsUnitDto(String code, String name, String description) {}
    public record CertDto(String name) {}
    public record StarDto(String situation, String task, String action, String result) {}
}
