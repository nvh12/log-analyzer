package com.nvh12.reaction.infrastructure.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_DETECTION_RESULTS = "detection.results";
    public static final String EXCHANGE_REACTION_RESULTS = "reaction.results";
    public static final String QUEUE_DETECTION_RESULTS = "detection.results.reaction";

    public static final String EXCHANGE_REACTION_DLX = "reaction.dlx";
    public static final String QUEUE_DETECTION_RESULTS_DLQ = "detection.results.reaction.dlq";
    public static final String ROUTING_KEY_DETECTION_RESULTS_DLQ = "detection.results.reaction.dlq";

    @Bean
    public FanoutExchange detectionResultsExchange() {
        return new FanoutExchange(EXCHANGE_DETECTION_RESULTS);
    }

    @Bean
    public FanoutExchange reactionResultsExchange() {
        return new FanoutExchange(EXCHANGE_REACTION_RESULTS);
    }

    @Bean
    public DirectExchange reactionDlx() {
        return new DirectExchange(EXCHANGE_REACTION_DLX);
    }

    @Bean
    public Queue detectionResultsQueue() {
        return QueueBuilder.durable(QUEUE_DETECTION_RESULTS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_REACTION_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DETECTION_RESULTS_DLQ)
                .build();
    }

    @Bean
    public Queue detectionResultsDlq() {
        return QueueBuilder.durable(QUEUE_DETECTION_RESULTS_DLQ).build();
    }

    @Bean
    public Binding detectionResultsBinding(Queue detectionResultsQueue,
                                           FanoutExchange detectionResultsExchange) {
        return BindingBuilder.bind(detectionResultsQueue).to(detectionResultsExchange);
    }

    @Bean
    public Binding detectionResultsDlqBinding(Queue detectionResultsDlq, DirectExchange reactionDlx) {
        return BindingBuilder.bind(detectionResultsDlq)
                .to(reactionDlx)
                .with(ROUTING_KEY_DETECTION_RESULTS_DLQ);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
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
        factory.setDefaultRequeueRejected(false);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    // Separate auto-ack factory for the DLQ audit consumer: a failure while auditing a
    // dead-lettered message has nowhere further to route to, so it just logs and acks —
    // unlike the main listener, there's no live retry/escalation behavior to protect here.
    @Bean
    public SimpleRabbitListenerContainerFactory reactionDlqContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
