package com.myith.core.scheduler;

import com.myith.core.application.port.OutboxRepository;
import com.myith.core.application.port.OutboxRepository.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Outbox 릴레이: PENDING 이벤트를 RabbitMQ로 발행.
 * 비즈니스 트랜잭션과 분리된 별도 스케줄러.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final int maxRetries;

    public OutboxRelayScheduler(OutboxRepository outboxRepository,
                                RabbitTemplate rabbitTemplate,
                                @Value("${policy.messaging.core-exchange}") String exchange,
                                @Value("${policy.outbox.max-retries}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${policy.outbox.relay-interval-ms:1000}")
    public void relay() {
        List<OutboxEvent> pending = outboxRepository.findPending();
        for (OutboxEvent event : pending) {
            try {
                String routingKey = event.eventType();
                rabbitTemplate.send(exchange, routingKey,
                        MessageBuilder.withBody(event.payload().getBytes())
                                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                                .setHeader("eventId", event.eventId().toString())
                                .setHeader("eventType", event.eventType())
                                .setHeader("traceId", org.slf4j.MDC.get("traceId"))
                                .build());

                outboxRepository.markPublished(event.id());
                log.info("Outbox relayed: eventId={}, type={}", event.eventId(), event.eventType());
            } catch (Exception e) {
                outboxRepository.incrementRetry(event.id());
                if (event.retryCount() + 1 >= maxRetries) {
                    outboxRepository.markFailed(event.id());
                }
                log.error("Outbox relay failed: eventId={}, retries={}", event.eventId(), event.retryCount() + 1, e);
            }
        }
    }
}
