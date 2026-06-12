package com.nvh12.dashboard.infrastructure.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_DETECTION_RESULTS = "detection.results";
    public static final String EXCHANGE_REACTION_RESULTS  = "reaction.results";

    @Bean
    public FanoutExchange detectionResultsExchange() {
        return new FanoutExchange(EXCHANGE_DETECTION_RESULTS);
    }

    @Bean
    public FanoutExchange reactionResultsExchange() {
        return new FanoutExchange(EXCHANGE_REACTION_RESULTS);
    }

    @Bean
    public Queue detectionDashboardQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Queue reactionDashboardQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding detectionBinding(Queue detectionDashboardQueue, FanoutExchange detectionResultsExchange) {
        return BindingBuilder.bind(detectionDashboardQueue).to(detectionResultsExchange);
    }

    @Bean
    public Binding reactionBinding(Queue reactionDashboardQueue, FanoutExchange reactionResultsExchange) {
        return BindingBuilder.bind(reactionDashboardQueue).to(reactionResultsExchange);
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
        return factory;
    }
}
