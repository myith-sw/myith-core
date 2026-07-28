package com.myith.core.application.quest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.dashboard.SnapshotService;
import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.NcsReadRepository.CertificationData;
import com.myith.core.application.port.NcsReadRepository.NcsUnitData;
import com.myith.core.domain.roadmap.*;
import com.myith.core.domain.roadmap.RoadmapAssembler.Prerequisite;
import com.myith.core.domain.star.StarRecord;
import com.myith.core.application.roadmap.RoadmapQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final DashboardSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public QuestDetailService(QuestRepository questRepository,
                              RoadmapRepository roadmapRepository,
                              StarRecordRepository starRecordRepository,
                              OutboxRepository outboxRepository,
                              SnapshotService snapshotService,
                              NcsReadRepository ncsReadRepository,
                              JobProfileReadRepository jobProfileReadRepository,
                              DashboardSnapshotRepository snapshotRepository,
                              ObjectMapper objectMapper,
                              @Value("${policy.optimistic-lock.max-retries}") int maxRetries) {
        this.questRepository = questRepository;
        this.roadmapRepository = roadmapRepository;
        this.starRecordRepository = starRecordRepository;
        this.outboxRepository = outboxRepository;
        this.snapshotService = snapshotService;
        this.ncsReadRepository = ncsReadRepository;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.snapshotRepository = snapshotRepository;
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

        // axisCode -> axisName 매핑
        String axisName = resolveAxisName(roadmap.getJobCode(), roadmap.getProfileVersion(), quest.getAxisCode());
        String updatedAt = quest.getUpdatedAt() != null ? quest.getUpdatedAt().toString() : null;

        return new QuestDetailDto(quest.getId(), quest.getRoadmapId(), quest.getTitle(),
                quest.getAxisCode(), axisName, quest.getLevel(),
                quest.getStatus().toApiName(), quest.getSource().name(),
                quest.getOrderInLevel(), quest.getVersion(),
                ncsUnit, certifications, quest.getCompletionCriteria(), star, updatedAt);
    }

    /**
     * 퀘스트 완료 토글.
     * 사이드이펙트:
     *   1. QuestUnlockPolicy로 전체 퀘스트 LOCKED/OPEN 재계산
     *   2. dashboard_snapshot 재계산
     *
     * @return 컨트롤러가 응답을 조립할 수 있도록 결과를 반환한다.
     */
    @Transactional
    public ToggleResult toggleComplete(Long userId, Long questId, boolean completed,
                                       StarDto star) {
        Quest initialQuest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(initialQuest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(initialQuest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        if (initialQuest.getStatus() == QuestStatus.LOCKED) {
            throw new QuestManageService.QuestLockedException(questId);
        }

        // star가 함께 왔으면 같은 트랜잭션에서 STAR 저장 먼저 처리
        Quest quest;
        if (star != null) {
            saveStar(userId, questId, star.situation(), star.task(), star.action(), star.result());
            // saveStar가 version을 올릴 수 있으므로 최신 퀘스트를 다시 읽는다
            quest = questRepository.findById(questId)
                    .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));
        } else {
            quest = initialQuest;
        }

        // Bug B-2: completed=false일 때 STAR 존재 여부에 따라 PENDING 또는 OPEN
        QuestStatus newStatus;
        if (completed) {
            newStatus = QuestStatus.DONE;
        } else {
            newStatus = determineUncompleteStatus(questId);
        }
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

        // 사이드이펙트 1: QuestUnlockPolicy로 전체 퀘스트 상태 재계산
        List<Long> unlockedIds = recomputeQuestStatuses(roadmap);

        // 사이드이펙트 2: 스냅샷 재계산
        snapshotService.recalculate(quest.getRoadmapId());

        // 스냅샷 읽기
        DashboardSnapshotRepository.SnapshotData snapshot =
                snapshotRepository.findByRoadmapId(quest.getRoadmapId()).orElse(null);

        // 다음 퀘스트 (OPEN 상태 중 가장 낮은 레벨 순서)
        List<Quest> allQuests = questRepository.findByRoadmapId(quest.getRoadmapId());
        Quest nextQuest = allQuests.stream()
                .filter(q -> q.getStatus() == QuestStatus.OPEN)
                .min(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                .orElse(null);

        // 현재 진행 중인 레벨
        int currentLevel = allQuests.stream()
                .filter(q -> q.getStatus() == QuestStatus.OPEN || q.getStatus() == QuestStatus.DONE)
                .mapToInt(Quest::getLevel)
                .max().orElse(1);

        // 저장된 퀘스트 다시 읽기 (version 갱신됨)
        Quest savedQuest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        // axisCode -> axisName 매핑
        Map<String, String> axisNameMap = buildAxisNameMap(roadmap.getJobCode(), roadmap.getProfileVersion());

        // 레이더 파싱
        List<ToggleResult.RadarEntry> radar = parseRadar(snapshot, axisNameMap);

        return new ToggleResult(
                savedQuest.getId(), savedQuest.getStatus(), savedQuest.getVersion(),
                savedQuest.getCompletedAt(),
                unlockedIds,
                snapshot != null ? snapshot.completionRate() : BigDecimal.ZERO,
                snapshot != null ? snapshot.stage() : null,
                currentLevel,
                nextQuest != null ? new ToggleResult.NextQuest(nextQuest.getId(), nextQuest.getTitle()) : null,
                radar
        );
    }

    /**
     * completed=false 시 STAR 존재 여부에 따른 상태 결정 (Bug B-2).
     */
    private QuestStatus determineUncompleteStatus(Long questId) {
        Optional<StarRecord> starOpt = starRecordRepository.findByQuestId(questId);
        if (starOpt.isPresent()) {
            StarRecord star = starOpt.get();
            boolean hasContent = (star.getSituation() != null && !star.getSituation().isBlank())
                    || (star.getTask() != null && !star.getTask().isBlank())
                    || (star.getAction() != null && !star.getAction().isBlank())
                    || (star.getResult() != null && !star.getResult().isBlank());
            if (hasContent) {
                return QuestStatus.PENDING;
            }
        }
        return QuestStatus.OPEN;
    }

    /**
     * QuestUnlockPolicy를 사용하여 전체 퀘스트 LOCKED/OPEN 상태를 재계산한다.
     *
     * @return 새로 해금된 퀘스트 ID 목록
     */
    List<Long> recomputeQuestStatuses(Roadmap roadmap) {
        List<Quest> allQuests = questRepository.findByRoadmapId(roadmap.getId());

        JobProfileData profile = jobProfileReadRepository
                .findByJobCodeAndVersion(roadmap.getJobCode(), roadmap.getProfileVersion())
                .orElse(null);
        if (profile == null) return List.of();

        List<Prerequisite> prerequisites = parsePrerequisites(profile.prerequisites());

        List<QuestUnlockPolicy.StatusChange> changes =
                QuestUnlockPolicy.recompute(allQuests, prerequisites);

        List<Long> unlockedIds = new ArrayList<>();
        for (QuestUnlockPolicy.StatusChange change : changes) {
            Quest q = allQuests.stream()
                    .filter(quest -> quest.getId().equals(change.questId()))
                    .findFirst().orElse(null);
            if (q == null) continue;

            if (change.newStatus() == QuestStatus.OPEN) {
                unlockedIds.add(q.getId());
            }

            Quest changed = Quest.restore(q.getId(), q.getRoadmapId(), q.getSkillCode(), q.getAxisCode(),
                    q.getLevel(), q.getOrderInLevel(), q.getTitle(), q.getCompletionCriteria(),
                    q.getNcsUnitCode(), q.getSource(), change.newStatus(), q.getCompletedAt(),
                    q.getVersion(), q.getCreatedAt(), Instant.now());
            questRepository.save(changed);
        }
        return unlockedIds;
    }

    private String resolveAxisName(String jobCode, int profileVersion, String axisCode) {
        return jobProfileReadRepository.findByJobCodeAndVersion(jobCode, profileVersion)
                .map(p -> {
                    try {
                        List<Map<String, String>> axes = objectMapper.readValue(
                                p.axes(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                        return axes.stream()
                                .filter(a -> axisCode.equals(a.get("axisCode")))
                                .map(a -> a.get("axisName"))
                                .findFirst()
                                .orElse(axisCode);
                    } catch (JsonProcessingException e) {
                        return axisCode;
                    }
                }).orElse(axisCode);
    }

    private Map<String, String> buildAxisNameMap(String jobCode, int profileVersion) {
        return jobProfileReadRepository.findByJobCodeAndVersion(jobCode, profileVersion)
                .map(p -> {
                    try {
                        List<Map<String, String>> axes = objectMapper.readValue(
                                p.axes(), new TypeReference<>() {});
                        return axes.stream().collect(Collectors.toMap(
                                a -> a.get("axisCode"), a -> a.get("axisName")));
                    } catch (JsonProcessingException e) {
                        return Collections.<String, String>emptyMap();
                    }
                }).orElse(Collections.emptyMap());
    }

    private List<ToggleResult.RadarEntry> parseRadar(DashboardSnapshotRepository.SnapshotData snapshot,
                                                      Map<String, String> axisNameMap) {
        if (snapshot == null || snapshot.radarJson() == null) return List.of();
        try {
            List<Map<String, Object>> radarRaw = objectMapper.readValue(
                    snapshot.radarJson(), new TypeReference<>() {});
            return radarRaw.stream().map(r -> {
                String axisCode = (String) r.get("axisCode");
                BigDecimal percent = r.get("percent") instanceof Number n
                        ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
                String axisName = axisNameMap.getOrDefault(axisCode, axisCode);
                return new ToggleResult.RadarEntry(axisCode, axisName, percent);
            }).toList();
        } catch (JsonProcessingException e) {
            return List.of();
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
    public SaveStarResult saveStar(Long userId, Long questId,
                                   String situation, String task, String action, String result) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(quest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(quest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        if (quest.getStatus() == QuestStatus.LOCKED) {
            throw new QuestManageService.QuestLockedException(questId);
        }

        StarRecord existing = starRecordRepository.findByQuestId(questId).orElse(null);
        if (existing != null) {
            existing.update(situation, task, action, result);
            starRecordRepository.save(existing);
        } else {
            StarRecord record = StarRecord.create(questId, userId, situation, task, action, result);
            starRecordRepository.save(record);
        }

        // D-15: OPEN→PENDING when STAR has content, PENDING→OPEN when all empty
        boolean hasContent = (situation != null && !situation.isBlank())
                || (task != null && !task.isBlank())
                || (action != null && !action.isBlank())
                || (result != null && !result.isBlank());

        QuestStatus currentStatus = quest.getStatus();
        QuestStatus newStatus = currentStatus;
        if (currentStatus == QuestStatus.OPEN && hasContent) {
            newStatus = QuestStatus.PENDING;
        } else if (currentStatus == QuestStatus.PENDING && !hasContent) {
            newStatus = QuestStatus.OPEN;
        }

        if (newStatus != currentStatus) {
            Quest updated = Quest.restore(quest.getId(), quest.getRoadmapId(), quest.getSkillCode(),
                    quest.getAxisCode(), quest.getLevel(), quest.getOrderInLevel(), quest.getTitle(),
                    quest.getCompletionCriteria(), quest.getNcsUnitCode(), quest.getSource(),
                    newStatus, quest.getCompletedAt(), quest.getVersion(),
                    quest.getCreatedAt(), Instant.now());
            questRepository.save(updated);
        }

        // 저장 후 최신 상태를 읽어 반환
        Quest saved = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));
        return new SaveStarResult(saved.getStatus().toApiName(), saved.getVersion());
    }

    // STAR AI 보완 요청 -> Outbox 발행 -> 202
    @Transactional
    public UUID requestAiEnhancement(Long userId, Long questId,
                                      String situation, String task, String action, String result,
                                      String locale, String style) {
        Quest quest = questRepository.findById(questId)
                .orElseThrow(() -> new QuestManageService.QuestNotFoundException(questId));

        Roadmap roadmap = roadmapRepository.findById(quest.getRoadmapId())
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(quest.getRoadmapId()));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        if (quest.getStatus() == QuestStatus.LOCKED) {
            throw new QuestManageService.QuestLockedException(questId);
        }

        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", eventId.toString());
        payload.put("questId", questId);
        payload.put("roadmapId", quest.getRoadmapId());
        payload.put("userId", userId);
        payload.put("star", Map.of(
                "situation", situation != null ? situation : "",
                "task", task != null ? task : "",
                "action", action != null ? action : "",
                "result", result != null ? result : ""
        ));
        if (locale != null) payload.put("locale", locale);
        if (style != null) payload.put("style", style);

        try {
            outboxRepository.save("StarRecord", String.valueOf(questId),
                    eventId, "AiEnhancementRequested", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AI enhancement payload", e);
        }
        return eventId;
    }

    // ===== DTOs =====
    public record QuestDetailDto(Long questId, Long roadmapId, String title,
                                 String axisCode, String axisName, int level,
                                 String status, String source, int order, long version,
                                 NcsUnitDto ncsUnit, List<CertDto> certifications,
                                 String completionCriteria, StarDto star, String updatedAt) {}
    public record NcsUnitDto(String code, String name, String description) {}
    public record CertDto(String name) {}
    public record StarDto(String situation, String task, String action, String result) {}

    public record SaveStarResult(String status, long version) {}

    public record ToggleResult(Long questId, QuestStatus newStatus, long newVersion,
                               Instant completedAt, List<Long> unlockedQuestIds,
                               BigDecimal completionRate, String stage,
                               int currentLevel, NextQuest nextQuest,
                               List<RadarEntry> radar) {
        public record NextQuest(Long questId, String title) {}
        public record RadarEntry(String axisCode, String axisName, BigDecimal percent) {}
    }
}
