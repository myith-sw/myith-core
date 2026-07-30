package com.myith.core.scheduler;

import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.common.IdCodec;
import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;
import com.myith.core.domain.roadmap.Roadmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 정합성 스케줄러 (D-13) — 안전망.
 * 정상 경로: CompetencyExtracted 수신 즉시 조립 (WorkerEventConsumer).
 * 이 스케줄러는 Worker가 이벤트를 발행하지 못한 경우에만 개입한다.
 * max-retries 도달 시 자가진단만으로 조립해 READY로 확정한다(FAILED로 만들지 않는다).
 */
@Component
public class ConsistencyScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyScheduler.class);

    private final RoadmapRepository roadmapRepository;
    private final JobProfileReadRepository jobProfileReadRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final UserCompetencyReadRepository userCompetencyReadRepository;
    private final RoadmapCreateService roadmapCreateService;
    private final SseRegistry sseRegistry;
    private final int maxRetries;
    private final int timeoutSeconds;

    public ConsistencyScheduler(RoadmapRepository roadmapRepository,
                                JobProfileReadRepository jobProfileReadRepository,
                                DiagnosisRepository diagnosisRepository,
                                UserCompetencyReadRepository userCompetencyReadRepository,
                                RoadmapCreateService roadmapCreateService,
                                SseRegistry sseRegistry,
                                @Value("${policy.consistency.max-retries:3}") int maxRetries,
                                @Value("${policy.analysis.timeout-seconds}") int timeoutSeconds) {
        this.roadmapRepository = roadmapRepository;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.userCompetencyReadRepository = userCompetencyReadRepository;
        this.roadmapCreateService = roadmapCreateService;
        this.sseRegistry = sseRegistry;
        this.maxRetries = maxRetries;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void scan() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        List<Roadmap> stuck = roadmapRepository.findStuckAnalyzing(cutoff);

        for (Roadmap roadmap : stuck) {
            roadmap.incrementRetry();

            // job_profile 조회
            JobProfileData profile = jobProfileReadRepository
                    .findByJobCodeAndVersion(roadmap.getJobCode(), roadmap.getProfileVersion())
                    .orElse(null);

            if (profile == null) {
                log.error("Job profile not found for roadmap {}", roadmap.getId());
                roadmap.markFailed();
                roadmapRepository.save(roadmap);
                continue;
            }

            // user_competency 존재 여부 확인
            Map<String, CompetencyEntry> competencies =
                    userCompetencyReadRepository.findByRoadmapId(roadmap.getId());
            boolean hasCompetency = !competencies.isEmpty();

            // competency 없고 max-retries 미도달 → Worker를 더 기다린다
            if (!hasCompetency && roadmap.getRetryCount() < maxRetries) {
                roadmapRepository.save(roadmap);
                log.info("Consistency scheduler: roadmap {} waiting for competency (retry={}/{})",
                        roadmap.getId(), roadmap.getRetryCount(), maxRetries);
                continue;
            }

            // competency가 있거나 max-retries 도달 → 조립 확정
            // max-retries 도달 시 competency 없이 자가진단만으로 조립 (FAILED로 만들지 않는다)
            List<RoadmapCreateService.AnswerDto> answers = diagnosisRepository
                    .findByRoadmapId(roadmap.getId()).stream()
                    .map(d -> new RoadmapCreateService.AnswerDto(d.getSkillCode(), d.getMastery()))
                    .toList();

            roadmapCreateService.assembleAndSnapshot(roadmap, profile, answers);

            log.info("Consistency scheduler: roadmap {} assembled (retry={}, competency={})",
                    roadmap.getId(), roadmap.getRetryCount(), hasCompetency);

            if (sseRegistry.hasConnection(roadmap.getId())) {
                sseRegistry.send(roadmap.getId(), "done",
                        Map.of("roadmapId", IdCodec.encode(roadmap.getId(), "rmp_")));
                sseRegistry.complete(roadmap.getId());
            }
        }
    }
}
