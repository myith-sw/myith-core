package com.myith.core.application.export;

import com.myith.core.adapter.out.persistence.JobJpaRepository;
import com.myith.core.application.port.*;
import com.myith.core.application.roadmap.RoadmapQueryService;
import com.myith.core.domain.character.Character;
import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.star.StarRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExportService {

    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final StarRecordRepository starRecordRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final JobJpaRepository jobRepository;
    private final Map<String, ExportRenderer> renderers;

    public ExportService(RoadmapRepository roadmapRepository,
                         CharacterRepository characterRepository,
                         QuestRepository questRepository,
                         StarRecordRepository starRecordRepository,
                         DashboardSnapshotRepository snapshotRepository,
                         JobJpaRepository jobRepository,
                         List<ExportRenderer> rendererList) {
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.starRecordRepository = starRecordRepository;
        this.snapshotRepository = snapshotRepository;
        this.jobRepository = jobRepository;
        this.renderers = rendererList.stream()
                .collect(Collectors.toMap(ExportRenderer::fileExtension, r -> r));
    }

    public ExportResult export(Long userId, Long roadmapId, String format) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new RoadmapQueryService.RoadmapNotFoundException(roadmapId));
        RoadmapQueryService.validateOwnership(roadmap, userId);

        ExportRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new UnsupportedFormatException(format);
        }

        String jobName = jobRepository.findById(roadmap.getJobCode())
                .map(j -> j.getJobName()).orElse(roadmap.getJobCode());

        Character character = characterRepository.findByRoadmapId(roadmapId).orElse(null);
        DashboardSnapshotRepository.SnapshotData snapshot =
                snapshotRepository.findByRoadmapId(roadmapId).orElse(null);

        List<Quest> quests = questRepository.findByRoadmapId(roadmapId);

        // 레벨별 그룹핑 + STAR 조인
        Map<Integer, List<ExportData.QuestExport>> byLevel = new LinkedHashMap<>();
        quests.stream()
                .sorted(Comparator.comparingInt(Quest::getLevel).thenComparingInt(Quest::getOrderInLevel))
                .forEach(q -> {
                    ExportData.StarExport star = starRecordRepository.findByQuestId(q.getId())
                            .map(s -> new ExportData.StarExport(s.getSituation(), s.getTask(), s.getAction(), s.getResult()))
                            .orElse(null);
                    byLevel.computeIfAbsent(q.getLevel(), k -> new ArrayList<>())
                            .add(new ExportData.QuestExport(q.getTitle(), q.getAxisCode(), q.getStatus().name(), star));
                });

        List<ExportData.LevelExport> levels = byLevel.entrySet().stream()
                .map(e -> new ExportData.LevelExport(e.getKey(), e.getValue()))
                .toList();

        ExportData data = new ExportData(
                jobName,
                character != null ? character.getNickname() : null,
                snapshot != null ? snapshot.stage() : "시작",
                snapshot != null ? snapshot.completionRate().toPlainString() : "0",
                levels
        );

        byte[] content = renderer.render(data);
        String fileName = "roadmap_" + roadmapId + "." + renderer.fileExtension();
        return new ExportResult(content, renderer.contentType(), fileName);
    }

    public record ExportResult(byte[] content, String contentType, String fileName) {}

    public static class UnsupportedFormatException extends RuntimeException {
        public UnsupportedFormatException(String format) {
            super("Unsupported export format: " + format + ". Supported: md, pdf");
        }
    }
}
