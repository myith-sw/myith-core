package com.myith.core.application.presence;

import com.myith.core.application.port.CharacterRepository;
import com.myith.core.application.port.DashboardSnapshotRepository;
import com.myith.core.application.port.RoadmapRepository;
import com.myith.core.application.port.UserRepository;
import com.myith.core.domain.character.Character;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class HeartbeatService {

    private final UserRepository userRepository;
    private final RoadmapRepository roadmapRepository;
    private final CharacterRepository characterRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final DemoNudgeRegistry demoNudgeRegistry;

    @Value("${myith.nudge.messages.ABSENCE_48H:이틀 동안 못 봤어요. 오늘 퀘스트 하나만 해볼까요?}")
    private String absence48hMessage;

    public HeartbeatService(UserRepository userRepository,
                            RoadmapRepository roadmapRepository,
                            CharacterRepository characterRepository,
                            DashboardSnapshotRepository snapshotRepository,
                            @Autowired(required = false) DemoNudgeRegistry demoNudgeRegistry) {
        this.userRepository = userRepository;
        this.roadmapRepository = roadmapRepository;
        this.characterRepository = characterRepository;
        this.snapshotRepository = snapshotRepository;
        this.demoNudgeRegistry = demoNudgeRegistry;
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
        if (!activeRoadmaps.isEmpty()) {
            Roadmap latest = activeRoadmaps.getFirst();
            Character character = characterRepository.findByRoadmapId(latest.getId()).orElse(null);
            DashboardSnapshotRepository.SnapshotData snapshot =
                    snapshotRepository.findByRoadmapId(latest.getId()).orElse(null);
            if (character != null && snapshot != null) {
                charState = new CharacterStateDto(character.getSpecies(),
                        snapshot.stage(), snapshot.completionRate());
            }
        }

        // 데모 넛지 확인 (기존 판정보다 먼저)
        if (demoNudgeRegistry != null && demoNudgeRegistry.consume(userId)) {
            return new HeartbeatResult(true, "ABSENCE_48H", absence48hMessage, charState);
        }

        // nudge 여부
        boolean nudge = user.getLastNudgeSentAt() != null;
        if (nudge) {
            return new HeartbeatResult(true, "ABSENCE_48H", absence48hMessage, charState);
        }

        return new HeartbeatResult(false, null, null, charState);
    }

    public record HeartbeatResult(boolean nudge, String nudgeType, String nudgeMessage, CharacterStateDto characterState) {}
    public record CharacterStateDto(String species, String stage, BigDecimal completionRate) {}
}
