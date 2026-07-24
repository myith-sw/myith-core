package com.myith.core.scheduler;

import com.myith.core.adapter.out.persistence.UserJpaEntity;
import com.myith.core.adapter.out.persistence.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 48h 미접속 스캔 스케줄러 (D-7).
 * 하루 1회 last_active_at + 48시간이 지난 사용자를 스캔해 nudge 대상으로 표시.
 */
@Component
public class InactivityScheduler {

    private static final Logger log = LoggerFactory.getLogger(InactivityScheduler.class);

    private final UserJpaRepository userRepository;
    private final long thresholdHours;
    private final long cooldownHours;

    public InactivityScheduler(UserJpaRepository userRepository,
                               @Value("${policy.inactivity.threshold-hours}") long thresholdHours,
                               @Value("${policy.nudge.cooldown-hours}") long cooldownHours) {
        this.userRepository = userRepository;
        this.thresholdHours = thresholdHours;
        this.cooldownHours = cooldownHours;
    }

    @Scheduled(cron = "0 0 9 * * *") // 매일 09:00
    @Transactional
    public void scan() {
        Instant cutoff = Instant.now().minusSeconds(thresholdHours * 3600);
        Instant nudgeCutoff = Instant.now().minusSeconds(cooldownHours * 3600);

        List<UserJpaEntity> inactive = userRepository.findInactiveUsers(cutoff, nudgeCutoff);
        for (UserJpaEntity user : inactive) {
            user.setLastNudgeSentAt(Instant.now());
            userRepository.save(user);
            log.info("Nudge marked for user {}", user.getId());
            // HANDOFF(worker): 실제 알림 전송은 NotificationSender 구현 시 추가
        }
        if (!inactive.isEmpty()) {
            log.info("Inactivity scan: {} users marked for nudge", inactive.size());
        }
    }
}
