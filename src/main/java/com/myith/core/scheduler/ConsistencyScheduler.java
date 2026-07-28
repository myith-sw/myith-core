package com.myith.core.scheduler;

import com.myith.core.application.port.DiagnosisRepository;
import com.myith.core.application.port.JobProfileReadRepository;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.application.roadmap.RoadmapCreateService;
import com.myith.core.adapter.in.sse.SseRegistry;
import com.myith.core.common.IdCodec;
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
 * 정합성 스케줄러 (D-13).
 * Worker가 결과를 DB에 쓴 뒤 이벤트 발행 전에 죽으면 Core는 영원히 대기.
 * 이 스케줄러가 DB만 보고 폴백 조립을 수행한다.
 */
@Component
public class ConsistencyScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyScheduler.class);

    private final RoadmapRepository roadmapRepository;
    private final JobProfileReadRepository jobProfileReadRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final RoadmapCreateService roadmapCreateService;
    private final SseRegistry sseRegistry;
    private final int maxRetries;
    private final int timeoutSeconds;

    public ConsistencyScheduler(RoadmapRepository roadmapRepository,
                                JobProfileReadRepository jobProfileReadRepository,
                                DiagnosisRepository diagnosisRepository,
                                RoadmapCreateService roadmapCreateService,
                                SseRegistry sseRegistry,
                                @Value("${policy.consistency.max-retries:3}") int maxRetries,
                                @Value("${policy.analysis.timeout-seconds}") int timeoutSeconds) {
        this.roadmapRepository = roadmapRepository;
        this.jobProfileReadRepository = jobProfileReadRepository;
        this.diagnosisRepository = diagnosisRepository;
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
            if (roadmap.getRetryCount() >= maxRetries) {
                roadmap.markFailed();
                roadmapRepository.save(roadmap);
                log.warn("Roadmap {} marked FAILED after {} retries", roadmap.getId(), maxRetries);

                if (sseRegistry.hasConnection(roadmap.getId())) {
                    sseRegistry.send(roadmap.getId(), "error", Map.of(
                            "code", "GENERATION_FAILED", "message", "로드맵 생성에 실패했습니다."));
                    sseRegistry.complete(roadmap.getId());
                }
                continue;
            }

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

            // 자가진단 조회
            List<RoadmapCreateService.AnswerDto> answers = diagnosisRepository
                    .findByRoadmapId(roadmap.getId()).stream()
                    .map(d -> new RoadmapCreateService.AnswerDto(d.getSkillCode(), d.getMastery()))
                    .toList();

            // 폴백 조립 (user_competency 유무와 무관하게 자가진단만으로 조립)
            // TODO: user_competency가 있으면 보정값 반영해서 조립 (Worker 연동 후)
            roadmapCreateService.assembleAndSnapshot(roadmap, profile, answers);

            log.info("Consistency scheduler: roadmap {} fallback assembled (retry={})",
                    roadmap.getId(), roadmap.getRetryCount());

            if (sseRegistry.hasConnection(roadmap.getId())) {
                sseRegistry.send(roadmap.getId(), "done",
                        Map.of("roadmapId", IdCodec.encode(roadmap.getId(), "rmp_")));
                sseRegistry.complete(roadmap.getId());
            }
        }
    }
}
