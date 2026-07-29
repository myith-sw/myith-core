package com.myith.core.application.presence;

import com.myith.core.application.port.CharacterRepository;
import com.myith.core.application.port.DashboardSnapshotRepository;
import com.myith.core.application.port.QuestRepository;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.application.port.UserRepository;
import com.myith.core.domain.character.Character;
import com.myith.core.domain.roadmap.QuestStatus;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class HeartbeatService {

    private final UserRepository userRepository;
    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final QuestRepository questRepository;
    private final DemoNudgeRegistry demoNudgeRegistry;

    private final long annoyingHours;
    private final long annoyingOpenQuests;
    private final long upsetHours;
    private final long absenceHours;
    private final Map<String, String> nudgeMessages;

    public HeartbeatService(UserRepository userRepository,
                            RoadmapRepository roadmapRepository,
                            CharacterRepository characterRepository,
                            DashboardSnapshotRepository snapshotRepository,
                            QuestRepository questRepository,
                            @Autowired(required = false) DemoNudgeRegistry demoNudgeRegistry,
                            @Value("${myith.nudge.thresholds.annoying-hours}") long annoyingHours,
                            @Value("${myith.nudge.thresholds.annoying-open-quests}") long annoyingOpenQuests,
                            @Value("${myith.nudge.thresholds.upset-hours}") long upsetHours,
                            @Value("${myith.nudge.thresholds.absence-hours}") long absenceHours,
                            @Value("${myith.nudge.messages.ANNOYING}") String annoyingMessage,
                            @Value("${myith.nudge.messages.UPSET}") String upsetMessage,
                            @Value("${myith.nudge.messages.ABSENCE_48H}") String absence48hMessage) {
        this.userRepository = userRepository;
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.snapshotRepository = snapshotRepository;
        this.questRepository = questRepository;
        this.demoNudgeRegistry = demoNudgeRegistry;
        this.annoyingHours = annoyingHours;
        this.annoyingOpenQuests = annoyingOpenQuests;
        this.upsetHours = upsetHours;
        this.absenceHours = absenceHours;
        this.nudgeMessages = Map.of(
                "ANNOYING", annoyingMessage,
                "UPSET", upsetMessage,
                "ABSENCE_48H", absence48hMessage
        );
    }

    @Transactional
    public HeartbeatResult heartbeat(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // last_heartbeat_at만 갱신 (last_active_at은 건드리지 않음 — D-7)
        user.touchHeartbeat(Instant.now());
        userRepository.save(user);

        // 최신 ACTIVE 로드맵의 캐릭터 상태
        List<Roadmap> activeRoadmaps = roadmapRepository.findActiveByUserId(userId);
        CharacterStateDto charState = null;
        Roadmap latestRoadmap = null;
        if (!activeRoadmaps.isEmpty()) {
            latestRoadmap = activeRoadmaps.getFirst();
            Character character = characterRepository.findByRoadmapId(latestRoadmap.getId()).orElse(null);
            DashboardSnapshotRepository.SnapshotData snapshot =
                    snapshotRepository.findByRoadmapId(latestRoadmap.getId()).orElse(null);
            if (character != null && snapshot != null) {
                charState = new CharacterStateDto(character.getSpecies(),
                        snapshot.stage(), snapshot.completionRate());
            }
        }

        // 데모 넛지 확인 (기존 판정보다 먼저, 쿨다운 우회)
        if (demoNudgeRegistry != null) {
            String demoType = demoNudgeRegistry.consume(userId);
            if (demoType != null) {
                return new HeartbeatResult(true, demoType, messageOf(demoType), charState);
            }
        }

        // 넛지 1회성: lastNudgeSentAt > lastHeartbeatAt이면 아직 미확인 넛지
        boolean pendingNudge = user.getLastNudgeSentAt() != null
                && (user.getLastHeartbeatAt() == null
                    || user.getLastNudgeSentAt().isAfter(user.getLastHeartbeatAt()));
        if (pendingNudge) {
            String type = determineNudgeType(user, latestRoadmap);
            return new HeartbeatResult(true, type, messageOf(type), charState);
        }

        return new HeartbeatResult(false, null, null, charState);
    }

    /**
     * 우선순위: ABSENCE_48H > UPSET > ANNOYING.
     * 미접속 시간 = now - last_active_at (D-7).
     */
    private String determineNudgeType(User user, Roadmap latestRoadmap) {
        Instant lastActive = user.getLastActiveAt();
        if (lastActive == null) {
            return "ABSENCE_48H";
        }

        long inactiveHours = Duration.between(lastActive, Instant.now()).toHours();

        if (inactiveHours >= absenceHours) {
            return "ABSENCE_48H";
        }
        if (inactiveHours >= upsetHours) {
            return "UPSET";
        }
        if (inactiveHours >= annoyingHours && latestRoadmap != null) {
            long openCount = questRepository.countByRoadmapIdAndStatus(
                    latestRoadmap.getId(), QuestStatus.OPEN)
                    + questRepository.countByRoadmapIdAndStatus(
                    latestRoadmap.getId(), QuestStatus.PENDING);
            if (openCount >= annoyingOpenQuests) {
                return "ANNOYING";
            }
        }

        // 스케줄러가 표시했지만 조건 미달 — 폴백
        return "ABSENCE_48H";
    }

    public String messageOf(String nudgeType) {
        return nudgeMessages.getOrDefault(nudgeType, nudgeMessages.get("ABSENCE_48H"));
    }

    public record HeartbeatResult(boolean nudge, String nudgeType, String nudgeMessage, CharacterStateDto characterState) {}
    public record CharacterStateDto(String species, String stage, BigDecimal completionRate) {}
}
