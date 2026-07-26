package com.myith.core.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.JobReadRepository;
import com.myith.core.application.port.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "policy.collect.enabled", havingValue = "true", matchIfMissing = true)
public class JobProfileRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobProfileRefreshScheduler.class);

    private final JobReadRepository jobReadRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public JobProfileRefreshScheduler(JobReadRepository jobReadRepository,
                                      OutboxRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.jobReadRepository = jobReadRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${policy.collect.cron}")
    @Transactional
    public void triggerWeeklyRefresh() {
        log.info("JobProfileRefreshScheduler: starting weekly refresh");
        List<String> jobCodes = jobReadRepository.findAllJobCodes();

        Instant oneHourAgo = Instant.now().minusSeconds(3600);

        for (String jobCode : jobCodes) {
            if (outboxRepository.existsRecentEvent(jobCode, "JobProfileBuildRequested", oneHourAgo)) {
                log.debug("Skipping {} — recent JobProfileBuildRequested exists", jobCode);
                continue;
            }

            UUID eventId = UUID.randomUUID();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobCode", jobCode);

            try {
                outboxRepository.save("Job", jobCode, eventId,
                        "JobProfileBuildRequested", objectMapper.writeValueAsString(payload));
                log.info("JobProfileBuildRequested published for jobCode={}", jobCode);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize JobProfileBuildRequested for {}", jobCode, e);
            }
        }

        log.info("JobProfileRefreshScheduler: completed, processed {} job codes", jobCodes.size());
    }
}
