package com.myith.core.config;

import com.myith.core.application.dashboard.SnapshotService;
import com.myith.core.application.quest.QuestDetailService;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.domain.roadmap.Roadmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기동 시 모든 활성 로드맵의 퀘스트 해금 상태 + 대시보드 스냅샷을 재계산한다.
 * 완료율·레이더 공식이 바뀌었을 때 기존 스냅샷을 갱신하기 위한 것이다.
 */
@Component
public class SnapshotRecalcRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRecalcRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final SnapshotService snapshotService;
    private final QuestDetailService questDetailService;
    private final RoadmapRepository roadmapRepository;

    public SnapshotRecalcRunner(JdbcTemplate jdbcTemplate,
                                SnapshotService snapshotService,
                                QuestDetailService questDetailService,
                                RoadmapRepository roadmapRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.snapshotService = snapshotService;
        this.questDetailService = questDetailService;
        this.roadmapRepository = roadmapRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> roadmapIds = jdbcTemplate.queryForList(
                "SELECT id FROM roadmap WHERE status = 'ACTIVE' AND deleted_at IS NULL",
                Long.class);

        if (roadmapIds.isEmpty()) {
            log.info("활성 로드맵 없음 — 스냅샷 재계산 건너뜀");
            return;
        }

        log.info("기동 시 스냅샷 재계산 시작: 활성 로드맵 {}개", roadmapIds.size());
        int success = 0;
        for (Long roadmapId : roadmapIds) {
            try {
                Roadmap roadmap = roadmapRepository.findById(roadmapId).orElse(null);
                if (roadmap != null) {
                    questDetailService.recomputeQuestStatuses(roadmap);
                }
                snapshotService.recalculate(roadmapId);
                success++;
            } catch (Exception e) {
                log.warn("로드맵 {} 스냅샷 재계산 실패: {}", roadmapId, e.getMessage());
            }
        }
        log.info("스냅샷 재계산 완료: {}/{}", success, roadmapIds.size());
    }
}
