package com.nvh12.log_processing.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String QUEUE_RAW = "log.raw";
    public static final String QUEUE_NORMALIZED_HTTP = "log.normalized.http";
    public static final String QUEUE_NORMALIZED_FLOW = "log.normalized.flow";
    public static final String QUEUE_RAW_DLQ = "log.raw.dlq";

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

    @Bean
    public Queue normalizedHttpQueue() {
        return new Queue(QUEUE_NORMALIZED_HTTP, true);
    }

    @Bean
    public Queue normalizedFlowQueue() {
        return new Queue(QUEUE_NORMALIZED_FLOW, true);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
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
}
