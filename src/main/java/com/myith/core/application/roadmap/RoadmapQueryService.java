package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.adapter.out.persistence.JobJpaEntity;
import com.myith.core.adapter.out.persistence.JobJpaRepository;
import com.myith.core.adapter.out.persistence.JobProfileJpaEntity;
import com.myith.core.adapter.out.persistence.JobProfileJpaRepository;
import com.myith.core.application.port.*;
import com.myith.core.domain.character.Character;
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
    private final JobJpaRepository jobRepository;
    private final JobProfileJpaRepository jobProfileRepository;
    private final ObjectMapper objectMapper;

    public RoadmapQueryService(RoadmapRepository roadmapRepository,
                               CharacterRepository characterRepository,
                               QuestRepository questRepository,
                               DashboardSnapshotRepository snapshotRepository,
                               JobJpaRepository jobRepository,
                               JobProfileJpaRepository jobProfileRepository,
                               ObjectMapper objectMapper) {
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.snapshotRepository = snapshotRepository;
        this.jobRepository = jobRepository;
        this.jobProfileRepository = jobProfileRepository;
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
        JobJpaEntity job = jobRepository.findById(roadmap.getJobCode()).orElse(null);
        String jobName = job != null ? job.getJobName() : roadmap.getJobCode();
        String tagline = job != null ? job.getTagline() : null;

        // axisCode → axisName 매핑 (job_profile에서)
        Map<String, String> axisNameMap = buildAxisNameMap(roadmap.getJobCode(), roadmap.getProfileVersion());

        // 레벨별 그룹핑
        Map<Integer, List<QuestDto>> byLevel = quests.stream()
                .sorted(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                .collect(Collectors.groupingBy(Quest::getLevel, LinkedHashMap::new,
                        Collectors.mapping(q -> new QuestDto(
                                q.getId(), q.getTitle(),
                                axisNameMap.getOrDefault(q.getAxisCode(), q.getAxisCode()),
                                q.getStatus().name(), q.getSource().name()
                        ), Collectors.toList())));

        List<LevelDto> levels = byLevel.entrySet().stream()
                .map(e -> new LevelDto(e.getKey(), e.getValue()))
                .toList();

        CharacterDto charDto = null;
        if (character != null && snapshot != null) {
            charDto = new CharacterDto(character.getSpecies(), character.getNickname(),
                    snapshot.stage(), snapshot.completionRate());
        }

        return new RoadmapDetailDto(roadmapId, jobName, tagline,
                roadmap.getGenerationState().name(), charDto, levels);
    }

    public List<CharacterListDto> getCharacters(Long userId) {
        List<Roadmap> activeRoadmaps = roadmapRepository.findActiveByUserId(userId);
        List<CharacterListDto> result = new ArrayList<>();

        for (Roadmap roadmap : activeRoadmaps) {
            Character character = characterRepository.findByRoadmapId(roadmap.getId()).orElse(null);
            if (character == null) continue;

            DashboardSnapshotRepository.SnapshotData snapshot =
                    snapshotRepository.findByRoadmapId(roadmap.getId()).orElse(null);

            JobJpaEntity job = jobRepository.findById(roadmap.getJobCode()).orElse(null);

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
                    job != null ? job.getJobName() : roadmap.getJobCode(),
                    job != null ? job.getTagline() : null,
                    snapshot != null ? snapshot.completionRate() : BigDecimal.ZERO,
                    snapshot != null ? snapshot.stage() : "시작",
                    currentLevel,
                    nextQuest != null ? new NextQuestDto(nextQuest.getId(), nextQuest.getTitle()) : null
            ));
        }
        return result;
    }

    private Map<String, String> buildAxisNameMap(String jobCode, int version) {
        return jobProfileRepository.findById(new JobProfileJpaEntity.JobProfileId(jobCode, version))
                .map(p -> {
                    try {
                        List<Map<String, String>> axes = objectMapper.readValue(
                                p.getAxes(), new TypeReference<>() {});
                        return axes.stream().collect(Collectors.toMap(
                                a -> a.get("axisCode"), a -> a.get("axisName")));
                    } catch (JsonProcessingException e) {
                        return Collections.<String, String>emptyMap();
                    }
                }).orElse(Collections.emptyMap());
    }

    public static void validateOwnership(Roadmap roadmap, Long userId) {
        if (!roadmap.getUserId().equals(userId)) {
            throw new RoadmapAccessDeniedException(roadmap.getId());
        }
    }

    // ===== DTOs =====
    public record RoadmapDetailDto(Long roadmapId, String jobName, String tagline,
                                   String generationState, CharacterDto character,
                                   List<LevelDto> levels) {}
    public record CharacterDto(String species, String nickname, String stage, BigDecimal completionRate) {}
    public record LevelDto(int level, List<QuestDto> quests) {}
    public record QuestDto(Long questId, String title, String axisName, String status, String source) {}

    public record CharacterListDto(Long characterId, Long roadmapId, String species, String nickname,
                                   String jobCode, String jobName, String tagline,
                                   BigDecimal completionRate, String stage, int level,
                                   NextQuestDto nextQuest) {}
    public record NextQuestDto(Long questId, String title) {}

    public static class RoadmapNotFoundException extends RuntimeException {
        public RoadmapNotFoundException(Long id) { super("Roadmap not found: " + id); }
    }

    public static class RoadmapAccessDeniedException extends RuntimeException {
        public RoadmapAccessDeniedException(Long id) { super("Access denied to roadmap: " + id); }
    }
}
