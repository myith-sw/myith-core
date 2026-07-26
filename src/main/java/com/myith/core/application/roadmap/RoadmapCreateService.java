package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.*;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;
import com.myith.core.domain.character.Character;
import com.myith.core.domain.dashboard.*;
import com.myith.core.domain.diagnosis.UserDiagnosis;
import com.myith.core.domain.roadmap.*;
import com.myith.core.domain.roadmap.RoadmapAssembler.ProfileData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class RoadmapCreateService {

    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final QuestRepository questRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final OutboxRepository outboxRepository;
    private final JobProfileReadRepository jobProfileRepository;
    private final UserCompetencyReadRepository userCompetencyReadRepository;
    private final ObjectMapper objectMapper;
    private final GrowthStagePolicy stagePolicy;
    private final AxisAggregator axisAggregator;
    private final BigDecimal alreadyKnownThreshold;
    private final int maxExperiences;

    public RoadmapCreateService(RoadmapRepository roadmapRepository,
                                CharacterRepository characterRepository,
                                QuestRepository questRepository,
                                DiagnosisRepository diagnosisRepository,
                                DashboardSnapshotRepository snapshotRepository,
                                OutboxRepository outboxRepository,
                                JobProfileReadRepository jobProfileRepository,
                                UserCompetencyReadRepository userCompetencyReadRepository,
                                ObjectMapper objectMapper,
                                GrowthStagePolicy stagePolicy,
                                AxisAggregator axisAggregator,
                                @Value("${policy.mastery.already-known-threshold}") BigDecimal alreadyKnownThreshold,
                                @Value("${policy.roadmap.max-experiences}") int maxExperiences) {
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.questRepository = questRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.snapshotRepository = snapshotRepository;
        this.outboxRepository = outboxRepository;
        this.jobProfileRepository = jobProfileRepository;
        this.userCompetencyReadRepository = userCompetencyReadRepository;
        this.objectMapper = objectMapper;
        this.stagePolicy = stagePolicy;
        this.axisAggregator = axisAggregator;
        this.alreadyKnownThreshold = alreadyKnownThreshold;
        this.maxExperiences = maxExperiences;
    }

    @Transactional
    public CreateResult create(Long userId, CreateCommand cmd) {
        // 0. experiences 상한 검증
        if (cmd.experiences() != null && cmd.experiences().size() > maxExperiences) {
            throw new ExperiencesLimitExceededException(maxExperiences);
        }

        // 1. job_profile 조회
        JobProfileData profile = jobProfileRepository
                .findByJobCodeAndVersion(cmd.jobCode(), cmd.profileVersion())
                .orElseThrow(() -> new JobQueryService.JobProfileNotFoundException(cmd.jobCode()));

        // 2. 기존 ACTIVE 로드맵 아카이브 (D-8)
        List<Roadmap> activeRoadmaps = roadmapRepository.findActiveByUserIdAndJobCode(userId, cmd.jobCode());
        for (Roadmap existing : activeRoadmaps) {
            existing.archive();
            roadmapRepository.save(existing);
        }

        // 3. species 중복 검증
        List<String> ownedSpecies = characterRepository.findSpeciesByUserId(userId);
        if (ownedSpecies.contains(cmd.species())) {
            throw new DuplicateSpeciesException(cmd.species());
        }

        // 4. 비정형 입력 존재 여부 판단
        boolean hasNarrative = cmd.narrative() != null
                || (cmd.experiences() != null && !cmd.experiences().isEmpty());

        GenerationState initialState = hasNarrative ? GenerationState.ANALYZING : GenerationState.READY;

        // 5. 로드맵, 캐릭터, 자가진단을 단일 트랜잭션으로 생성
        Roadmap roadmap = roadmapRepository.save(
                Roadmap.create(userId, cmd.jobCode(), cmd.profileVersion(), initialState));
        Long roadmapId = roadmap.getId();

        Character character = Character.create(userId, roadmapId, cmd.species(), cmd.nickname());
        characterRepository.save(character);

        List<UserDiagnosis> diagnoses = cmd.answers().stream()
                .map(a -> UserDiagnosis.create(roadmapId, a.skillCode(), a.mastery()))
                .toList();
        diagnosisRepository.saveAll(diagnoses);

        // 6. 선택형만 -> 즉시 조립
        if (!hasNarrative) {
            assembleAndSnapshot(roadmap, profile, cmd.answers());
        } else {
            // 비정형 -> Outbox 이벤트 발행, Worker에 위임
            publishRoadmapGenerationEvent(roadmap, cmd);
            // 빈 스냅샷 초기화 (조립 전이라도 조회 가능하도록)
            String initial = stagePolicy.initialStage();
            snapshotRepository.save(roadmap.getId(), BigDecimal.ZERO, initial, initial, "[]", 0);
        }

        return new CreateResult(roadmap.getId(), hasNarrative);
    }

    /**
     * 퀘스트 조립 + 스냅샷 계산.
     * 정합성 스케줄러(D-13)에서도 호출하므로 public.
     */
    public void assembleAndSnapshot(Roadmap roadmap, JobProfileData profile,
                                    List<AnswerDto> answers) {
        ProfileDataParser parser = new ProfileDataParser(objectMapper);
        ProfileData profileData = parser.parse(
                profile.skills(), profile.levels(), profile.prerequisites(),
                profile.questTemplates(), profile.activityQuests());

        // 자가진단 -> Map
        Map<String, BigDecimal> selfAssessment = new HashMap<>();
        for (AnswerDto a : answers) {
            selfAssessment.put(a.skillCode(), a.mastery());
        }

        // user_competency 조회 (비어있으면 null 전달 -> 자가진단만 사용)
        Map<String, CompetencyEntry> aiAssessment = userCompetencyReadRepository.findByRoadmapId(roadmap.getId());
        Map<String, CompetencyEntry> aiParam = aiAssessment.isEmpty() ? null : aiAssessment;

        List<Quest> quests = RoadmapAssembler.assemble(
                roadmap.getId(), profileData, selfAssessment, aiParam,
                alreadyKnownThreshold);

        questRepository.saveAll(quests);

        // 스냅샷 계산
        SnapshotCalculator calculator = new SnapshotCalculator(stagePolicy, axisAggregator);
        SnapshotCalculator.SnapshotResult result = calculator.calculate(quests, stagePolicy.initialStage());

        String radarJson = serializeRadar(result.radar());
        snapshotRepository.save(roadmap.getId(), result.completionRate(),
                result.stage(), result.maxStage(), radarJson, 0);

        // 로드맵 상태 갱신
        roadmap.markReady();
        roadmapRepository.save(roadmap);
    }

    private void publishRoadmapGenerationEvent(Roadmap roadmap, CreateCommand cmd) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roadmapId", roadmap.getId());
        payload.put("userId", roadmap.getUserId());
        payload.put("jobCode", cmd.jobCode());
        payload.put("profileVersion", cmd.profileVersion());
        payload.put("answers", cmd.answers());
        if (cmd.narrative() != null) payload.put("narrative", cmd.narrative());
        if (cmd.experiences() != null && !cmd.experiences().isEmpty()) payload.put("experiences", cmd.experiences());

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            outboxRepository.save("Roadmap", String.valueOf(roadmap.getId()),
                    eventId, "RoadmapGenerationRequested", payloadJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }

    private String serializeRadar(List<SnapshotCalculator.RadarEntry> radar) {
        try {
            return objectMapper.writeValueAsString(radar);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    // ===== DTOs =====

    public record CreateCommand(String jobCode, int profileVersion, String species, String nickname,
                                List<AnswerDto> answers, NarrativeDto narrative,
                                List<ExperienceDto> experiences) {}

    public record AnswerDto(String skillCode, BigDecimal mastery) {}

    public record NarrativeDto(String strength, String difficulty) {}

    public record ExperienceDto(String content, String repoUrl, String fileKey) {}

    public record CreateResult(Long roadmapId, boolean async) {}

    public static class DuplicateSpeciesException extends RuntimeException {
        public DuplicateSpeciesException(String species) {
            super("Species already owned: " + species);
        }
    }

    public static class ExperiencesLimitExceededException extends RuntimeException {
        public ExperiencesLimitExceededException(int max) {
            super("경험 카드는 최대 " + max + "개까지 등록할 수 있습니다.");
        }
    }
}
