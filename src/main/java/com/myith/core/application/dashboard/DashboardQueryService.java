package com.myith.core.application.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.*;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.star.StarRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final RoadmapRepository roadmapRepository;
    private final QuestRepository questRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final StarRecordRepository starRecordRepository;
    private final ObjectMapper objectMapper;

    public DashboardQueryService(RoadmapRepository roadmapRepository,
                                 QuestRepository questRepository,
                                 DashboardSnapshotRepository snapshotRepository,
                                 StarRecordRepository starRecordRepository,
                                 ObjectMapper objectMapper) {
        this.roadmapRepository = roadmapRepository;
        this.questRepository = questRepository;
        this.snapshotRepository = snapshotRepository;
        this.starRecordRepository = starRecordRepository;
        this.objectMapper = objectMapper;
    }

    public DashboardDto getDashboard(Long userId, Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        // 스냅샷에서 읽기 (조회 시점에 집계하지 않음)
        DashboardSnapshotRepository.SnapshotData snapshot = snapshotRepository.findByRoadmapId(roadmapId)
                .orElse(new DashboardSnapshotRepository.SnapshotData(
                        roadmapId, BigDecimal.ZERO, "시작", "시작", "[]", 0));

        List<RadarDto> radar = parseRadar(snapshot.radarJson());

        // skillTree: 레벨별 퀘스트
        List<Quest> quests = questRepository.findByRoadmapId(roadmapId);
        Map<Integer, List<SkillTreeQuestDto>> byLevel = quests.stream()
                .sorted(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                .collect(Collectors.groupingBy(Quest::getLevel, LinkedHashMap::new,
                        Collectors.mapping(q -> new SkillTreeQuestDto(
                                q.getId(), q.getTitle(), q.getAxisCode(), q.getStatus().toApiName()
                        ), Collectors.toList())));
        List<SkillTreeDto> skillTree = byLevel.entrySet().stream()
                .map(e -> new SkillTreeDto(e.getKey(), e.getValue()))
                .toList();

        // experienceCards: STAR가 있는 완료 퀘스트
        List<ExperienceCardDto> experienceCards = new ArrayList<>();
        for (Quest q : quests) {
            if (!q.getStatus().isCompleted()) continue;
            starRecordRepository.findByQuestId(q.getId()).ifPresent(star ->
                    experienceCards.add(new ExperienceCardDto(
                            q.getId(), q.getTitle(), q.getAxisCode(), q.getNcsUnitCode(),
                            star.getSituation(), star.getTask(), star.getAction(), star.getResult()
                    )));
        }

        return new DashboardDto(snapshot.completionRate(), snapshot.stage(),
                radar, skillTree, experienceCards);
    }

    private List<RadarDto> parseRadar(String radarJson) {
        try {
            return objectMapper.readValue(radarJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    // ===== DTOs =====
    public record DashboardDto(BigDecimal completionRate, String stage,
                               List<RadarDto> radar, List<SkillTreeDto> skillTree,
                               List<ExperienceCardDto> experienceCards) {}
    public record RadarDto(String axisCode, BigDecimal percent) {}
    public record SkillTreeDto(int level, List<SkillTreeQuestDto> quests) {}
    public record SkillTreeQuestDto(Long questId, String title, String axisCode, String status) {}
    public record ExperienceCardDto(Long questId, String questTitle, String axisCode, String ncsUnitName,
                                    String situation, String task, String action, String result) {}
}
