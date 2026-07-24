package com.myith.core.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class RabbitConfig {

    /** Core → Worker 이벤트 발행용 exchange */
    @Bean
    public TopicExchange coreEventsExchange() {
        return new TopicExchange("myith.core.events", true, false);
    }

    /** Worker → Core fanout exchange (D-12) */
    @Bean
    public FanoutExchange workerFanoutExchange() {
        return new FanoutExchange("myith.worker.fanout", true, false);
    }

    /** 인스턴스별 임시 큐 (auto-delete, exclusive) */
    @Bean
    public Queue instanceQueue(@Value("${spring.application.name:core}") String appName) {
        String queueName = "core.sse." + UUID.randomUUID().toString().substring(0, 8);
        return QueueBuilder.nonDurable(queueName)
                .autoDelete()
                .exclusive()
                .build();
    }

    @Bean
    public Binding workerFanoutBinding(Queue instanceQueue, FanoutExchange workerFanoutExchange) {
        return BindingBuilder.bind(instanceQueue).to(workerFanoutExchange);
    }
}
