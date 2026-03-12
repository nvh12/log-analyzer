//package com.nvh12.logprocessing.infrastructure.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
//import org.springframework.data.redis.core.StringRedisTemplate;
//
//@Configuration
//public class RedisConfig {
//
//    @Bean
//    public JedisConnectionFactory redisConnectionFactory() {
//
//        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
//        config.setHostName("classic-snapper-29073.upstash.io");
//        config.setPort(6379);
//        config.setPassword("AXGRAAIncDI0ZjI1MjgyYTliNzE0NzgyODI0NjA5MWExNmU4YTkzNnAyMjkwNzM");
//
//        return new JedisConnectionFactory(config);
//    }
//
//    @Bean
//    public StringRedisTemplate stringRedisTemplate(JedisConnectionFactory factory) {
//        return new StringRedisTemplate(factory);
//    }
//}
