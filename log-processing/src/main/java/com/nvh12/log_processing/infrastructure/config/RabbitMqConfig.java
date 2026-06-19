package com.nvh12.log_processing.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMqConfig {

    public static final String QUEUE_RAW = "log.raw";
    public static final String QUEUE_NORMALIZED_HTTP = "log.normalized.http";
    public static final String QUEUE_NORMALIZED_FLOW = "log.normalized.flow";
    public static final String QUEUE_RAW_DLQ = "log.raw.dlq";
    public static final String QUEUE_NORMALIZED_HTTP_DLQ = "log.normalized.http.dlq";
    public static final String QUEUE_NORMALIZED_FLOW_DLQ = "log.normalized.flow.dlq";

    private static final String DLX_NAME = "log.dlx";

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Queue rawLogQueue() {
        return QueueBuilder.durable(QUEUE_RAW)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", QUEUE_RAW_DLQ)
                .build();
    }

    @Bean
    public Queue rawLogDlq() {
        return new Queue(QUEUE_RAW_DLQ, true);
    }

    @Bean
    public Binding rawLogDlqBinding(Queue rawLogDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(rawLogDlq).to(deadLetterExchange).with(QUEUE_RAW_DLQ);
    }

    // This service publishes to these queues but log-analysis (Python) consumes from them.
    // The DLX must be declared here, not on the consumer side: queue arguments are fixed at
    // creation and whoever connects first "wins" the declaration — a consumer-side declare
    // with different arguments would hit a 406 PRECONDITION_FAILED against this one.
    @Bean
    public Queue normalizedHttpQueue() {
        return QueueBuilder.durable(QUEUE_NORMALIZED_HTTP)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", QUEUE_NORMALIZED_HTTP_DLQ)
                .build();
    }

    @Bean
    public Queue normalizedFlowQueue() {
        return QueueBuilder.durable(QUEUE_NORMALIZED_FLOW)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", QUEUE_NORMALIZED_FLOW_DLQ)
                .build();
    }

    @Bean
    public Queue normalizedHttpDlq() {
        return new Queue(QUEUE_NORMALIZED_HTTP_DLQ, true);
    }

    @Bean
    public Queue normalizedFlowDlq() {
        return new Queue(QUEUE_NORMALIZED_FLOW_DLQ, true);
    }

    @Bean
    public Binding normalizedHttpDlqBinding(Queue normalizedHttpDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(normalizedHttpDlq).to(deadLetterExchange).with(QUEUE_NORMALIZED_HTTP_DLQ);
    }

    @Bean
    public Binding normalizedFlowDlqBinding(Queue normalizedFlowDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(normalizedFlowDlq).to(deadLetterExchange).with(QUEUE_NORMALIZED_FLOW_DLQ);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter((JsonMapper) objectMapper);
        // Required for messages from non-Java senders (e.g. Python Simulation) that
        // don't include a __TypeId__ header — fall back to the @RabbitListener parameter type.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        // Route failed messages to DLX instead of requeueing indefinitely
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    // DLQ consumer must not use Jackson — the body may be non-JSON garbage that caused the
    // original failure, and re-running Jackson would trigger a second fatal conversion error
    // (Spring AMQP then silently discards messages that have both x-death and a fatal exception).
    @Bean("dlqContainerFactory")
    public SimpleRabbitListenerContainerFactory dlqContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
