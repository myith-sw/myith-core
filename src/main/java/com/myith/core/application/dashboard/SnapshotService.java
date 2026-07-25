package com.myith.core.application.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.DashboardSnapshotRepository;
import com.myith.core.application.port.QuestRepository;
import com.myith.core.domain.dashboard.AxisAggregator;
import com.myith.core.domain.dashboard.GrowthStagePolicy;
import com.myith.core.domain.dashboard.SnapshotCalculator;
import com.myith.core.domain.roadmap.Quest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 스냅샷 재계산 공통 서비스.
 * 퀘스트 완료 토글, 추가·삭제, 조립 완료 시 호출된다.
 * 멱등: 같은 퀘스트 상태로 호출하면 같은 결과.
 */
@Service
public class SnapshotService {

    private final QuestRepository questRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final GrowthStagePolicy stagePolicy;
    private final AxisAggregator axisAggregator;
    private final ObjectMapper objectMapper;

    public SnapshotService(QuestRepository questRepository,
                           DashboardSnapshotRepository snapshotRepository,
                           GrowthStagePolicy stagePolicy,
                           AxisAggregator axisAggregator,
                           ObjectMapper objectMapper) {
        this.questRepository = questRepository;
        this.snapshotRepository = snapshotRepository;
        this.stagePolicy = stagePolicy;
        this.axisAggregator = axisAggregator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recalculate(Long roadmapId) {
        List<Quest> quests = questRepository.findByRoadmapId(roadmapId);

        // 현재 max_stage 조회
        String currentMaxStage = snapshotRepository.findByRoadmapId(roadmapId)
                .map(DashboardSnapshotRepository.SnapshotData::maxStage)
                .orElse(stagePolicy.initialStage());

        SnapshotCalculator calculator = new SnapshotCalculator(stagePolicy, axisAggregator);
        SnapshotCalculator.SnapshotResult result = calculator.calculate(quests, currentMaxStage);

        String radarJson;
        try {
            radarJson = objectMapper.writeValueAsString(result.radar());
        } catch (JsonProcessingException e) {
            radarJson = "[]";
        }

        // version은 기존 +1 또는 0
        long nextVersion = snapshotRepository.findByRoadmapId(roadmapId)
                .map(s -> s.version() + 1)
                .orElse(0L);

        snapshotRepository.save(roadmapId, result.completionRate(),
                result.stage(), result.maxStage(), radarJson, nextVersion);
    }
}
