package com.nvh12.log_processing.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
//    public static final String EXCHANGE_NAME = "normalized_logs";
//    public static final String QUEUE_NAME = "normalized_logs_test_queue";
//    public static final String ROUTING_KEY = "log.normalized";
//
//    @Bean
//    public TopicExchange logExchange() {
//        return new TopicExchange(EXCHANGE_NAME);
//    }
//
//    @Bean
//    public Queue testQueue() {
//        return new Queue(QUEUE_NAME, true);
//    }
//
//    @Bean
//    public Binding binding(Queue testQueue, TopicExchange logExchange) {
//        return BindingBuilder.bind(testQueue).to(logExchange).with(ROUTING_KEY);
//    }
//
//    @Bean
//    public Jackson2JsonMessageConverter messageConverter() {
//        return new Jackson2JsonMessageConverter();
//    }
}
