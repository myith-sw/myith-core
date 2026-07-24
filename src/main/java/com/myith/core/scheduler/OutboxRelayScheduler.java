package com.myith.core.scheduler;

import com.myith.core.adapter.out.persistence.OutboxJpaEntity;
import com.myith.core.adapter.out.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 릴레이: PENDING 이벤트를 RabbitMQ로 발행.
 * 비즈니스 트랜잭션과 분리된 별도 스케줄러.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private static final String EXCHANGE = "myith.core.events";

    private final OutboxJpaRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelayScheduler(OutboxJpaRepository outboxRepository,
                                RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${policy.outbox.relay-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxJpaEntity> pending = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (OutboxJpaEntity event : pending) {
            try {
                String routingKey = event.getEventType();
                rabbitTemplate.send(EXCHANGE, routingKey,
                        MessageBuilder.withBody(event.getPayload().getBytes())
                                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                                .setHeader("eventId", event.getEventId().toString())
                                .setHeader("eventType", event.getEventType())
                                .setHeader("traceId", org.slf4j.MDC.get("traceId"))
                                .build());

                event.markPublished();
                outboxRepository.save(event);
                log.info("Outbox relayed: eventId={}, type={}", event.getEventId(), event.getEventType());
            } catch (Exception e) {
                event.incrementRetry();
                if (event.getRetryCount() > 5) {
                    event.markFailed();
                }
                outboxRepository.save(event);
                log.error("Outbox relay failed: eventId={}, retries={}", event.getEventId(), event.getRetryCount(), e);
            }
        }
    }
}
