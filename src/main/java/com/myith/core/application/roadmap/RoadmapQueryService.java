package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.JobReadRepository.JobData;
import com.myith.core.domain.character.Character;
import com.myith.core.domain.dashboard.GrowthStagePolicy;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.Roadmap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RoadmapQueryService {

    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final JobReadRepository jobRepository;
    private final JobProfileReadRepository jobProfileRepository;
    private final GrowthStagePolicy stagePolicy;
    private final ObjectMapper objectMapper;

    public RoadmapQueryService(RoadmapRepository roadmapRepository,
                               CharacterRepository characterRepository,
                               QuestRepository questRepository,
                               DashboardSnapshotRepository snapshotRepository,
                               JobReadRepository jobRepository,
                               JobProfileReadRepository jobProfileRepository,
                               GrowthStagePolicy stagePolicy,
                               ObjectMapper objectMapper) {
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.snapshotRepository = snapshotRepository;
        this.jobRepository = jobRepository;
        this.jobProfileRepository = jobProfileRepository;
        this.stagePolicy = stagePolicy;
        this.objectMapper = objectMapper;
    }

    public RoadmapDetailDto getDetail(Long userId, Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapNotFoundException(roadmapId));
        validateOwnership(roadmap, userId);

        Character character = characterRepository.findByRoadmapId(roadmapId).orElse(null);
        List<Quest> quests = questRepository.findByRoadmapId(roadmapId);
        DashboardSnapshotRepository.SnapshotData snapshot =
                snapshotRepository.findByRoadmapId(roadmapId).orElse(null);

        // job 이름, tagline 조회
        JobData job = jobRepository.findByJobCode(roadmap.getJobCode()).orElse(null);
        String jobName = job != null ? job.jobName() : roadmap.getJobCode();
        String tagline = job != null ? job.tagline() : null;

        // axisCode → axisName 매핑 (job_profile에서)
        Map<String, String> axisNameMap = buildAxisNameMap(roadmap.getJobCode(), roadmap.getProfileVersion());

        // 레벨별 그룹핑
        Map<Integer, List<QuestDto>> byLevel = quests.stream()
                .sorted(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                .collect(Collectors.groupingBy(Quest::getLevel, LinkedHashMap::new,
                        Collectors.mapping(q -> new QuestDto(
                                q.getId(), q.getTitle(),
                                q.getAxisCode(),
                                axisNameMap.getOrDefault(q.getAxisCode(), q.getAxisCode()),
                                q.getStatus().name(), q.getSource().name(),
                                q.getOrderInLevel(), q.getVersion()
                        ), Collectors.toList())));

        List<LevelDto> levels = byLevel.entrySet().stream()
                .map(e -> new LevelDto(e.getKey(), e.getValue()))
                .toList();

        CharacterDto charDto = null;
        if (character != null) {
            BigDecimal completionRate = snapshot != null ? snapshot.completionRate() : BigDecimal.ZERO;
            String stageLabel = snapshot != null ? snapshot.stage() : stagePolicy.initialStage();
            int stageNumber = stageToNumber(stageLabel);
            charDto = new CharacterDto(character.getId(), character.getSpecies(), character.getNickname(),
                    stageNumber, stageLabel, completionRate);
        }

        String updatedAt = roadmap.getUpdatedAt() != null ? roadmap.getUpdatedAt().toString() : null;

        return new RoadmapDetailDto(roadmapId, roadmap.getJobCode(), jobName, tagline,
                roadmap.getGenerationState().name(), roadmap.getStatus().name(),
                charDto, levels, updatedAt);
    }

    public List<CharacterListDto> getCharacters(Long userId) {
        return getCharacters(userId, "active");
    }

    public List<CharacterListDto> getCharacters(Long userId, String statusFilter) {
        List<Roadmap> activeRoadmaps;
        if ("archived".equals(statusFilter)) {
            activeRoadmaps = roadmapRepository.findByUserId(userId).stream()
                    .filter(r -> "ARCHIVED".equals(r.getStatus().name()))
                    .toList();
        } else if ("all".equals(statusFilter)) {
            activeRoadmaps = roadmapRepository.findByUserId(userId);
        } else {
            activeRoadmaps = roadmapRepository.findActiveByUserId(userId);
        }
        List<CharacterListDto> result = new ArrayList<>();

        for (Roadmap roadmap : activeRoadmaps) {
            Character character = characterRepository.findByRoadmapId(roadmap.getId()).orElse(null);
            if (character == null) continue;

            DashboardSnapshotRepository.SnapshotData snapshot =
                    snapshotRepository.findByRoadmapId(roadmap.getId()).orElse(null);

            JobData job = jobRepository.findByJobCode(roadmap.getJobCode()).orElse(null);

            // 다음 퀘스트: OPEN 상태 중 가장 낮은 레벨·순서
            List<Quest> quests = questRepository.findByRoadmapId(roadmap.getId());
            Quest nextQuest = quests.stream()
                    .filter(q -> q.getStatus().name().equals("OPEN"))
                    .min(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                    .orElse(null);

            // 현재 최고 레벨 (진행 중인 레벨)
            int currentLevel = quests.stream()
                    .filter(q -> q.getStatus().name().equals("OPEN") || q.getStatus().name().equals("DONE"))
                    .mapToInt(Quest::getLevel)
                    .max().orElse(1);

            result.add(new CharacterListDto(
                    character.getId(), roadmap.getId(),
                    character.getSpecies(), character.getNickname(),
                    roadmap.getJobCode(),
                    job != null ? job.jobName() : roadmap.getJobCode(),
                    job != null ? job.tagline() : null,
                    roadmap.getStatus().name(),
                    snapshot != null ? snapshot.completionRate() : BigDecimal.ZERO,
                    snapshot != null ? snapshot.stage() : stagePolicy.initialStage(),
                    currentLevel,
                    nextQuest != null ? new NextQuestDto(nextQuest.getId(), nextQuest.getTitle()) : null
            ));
        }
        return result;
    }

    /**
     * roadmapId → axisCode → axisName 매핑을 반환한다.
     */
    public Map<String, String> getAxisNameMap(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
        if (roadmap == null) return Collections.emptyMap();
        return buildAxisNameMap(roadmap.getJobCode(), roadmap.getProfileVersion());
    }

    private Map<String, String> buildAxisNameMap(String jobCode, int version) {
        return jobProfileRepository.findByJobCodeAndVersion(jobCode, version)
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

    private static int stageToNumber(String stageLabel) {
        return switch (stageLabel) {
            case "성장" -> 2;
            case "숙련" -> 3;
            case "완성" -> 4;
            default -> 1; // "시작"
        };
    }

    public static void validateOwnership(Roadmap roadmap, Long userId) {
        if (!roadmap.getUserId().equals(userId)) {
            throw new RoadmapAccessDeniedException(roadmap.getId());
        }
    }

    // ===== DTOs =====
    public record RoadmapDetailDto(Long roadmapId, String jobCode, String jobName, String tagline,
                                   String generationState, String roadmapStatus,
                                   CharacterDto character, List<LevelDto> levels,
                                   String updatedAt) {}
    public record CharacterDto(Long characterId, String species, String nickname,
                               int stageNumber, String stageLabel,
                               BigDecimal completionRate) {}
    public record LevelDto(int level, List<QuestDto> quests) {}
    public record QuestDto(Long questId, String title, String axisCode, String axisName,
                           String status, String source, int order, long version) {}

    public record CharacterListDto(Long characterId, Long roadmapId, String species, String nickname,
                                   String jobCode, String jobName, String tagline,
                                   String roadmapStatus, BigDecimal completionRate, String stage,
                                   int level, NextQuestDto nextQuest) {}
    public record NextQuestDto(Long questId, String title) {}

    public static class RoadmapNotFoundException extends RuntimeException {
        public RoadmapNotFoundException(Long id) { super("Roadmap not found: " + id); }
    }

    public static class RoadmapAccessDeniedException extends RuntimeException {
        public RoadmapAccessDeniedException(Long id) { super("Access denied to roadmap: " + id); }
    }
}
