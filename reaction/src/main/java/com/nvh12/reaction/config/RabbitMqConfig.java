package com.nvh12.reaction.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
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

    @Bean
    public FanoutExchange detectionResultsExchange() {
        return new FanoutExchange(EXCHANGE_DETECTION_RESULTS);
    }

    @Bean
    public FanoutExchange reactionResultsExchange() {
        return new FanoutExchange(EXCHANGE_REACTION_RESULTS);
    }

    @Bean
    public Queue detectionResultsQueue() {
        return new Queue(QUEUE_DETECTION_RESULTS, true);
    }

    @Bean
    public Binding detectionResultsBinding(Queue detectionResultsQueue,
                                           FanoutExchange detectionResultsExchange) {
        return BindingBuilder.bind(detectionResultsQueue).to(detectionResultsExchange);
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
