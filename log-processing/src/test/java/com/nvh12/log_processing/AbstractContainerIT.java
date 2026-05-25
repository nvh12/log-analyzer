package com.nvh12.log_processing;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@TestPropertySource(properties = {
    "log-processing.retry-delay-ms=86400000",
    "log-processing.retry-jitter-ms=0",
    "log-processing.dlq-capacity=2",
    "log-processing.main-queue-capacity=10",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
public abstract class AbstractContainerIT {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17.5");

    static final RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:4.2.4-management");

    static final RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7.4"));

    static {
        postgres.start();
        rabbit.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void resetSharedState() {
        if (redisTemplate != null) {
            redisTemplate.delete("raw-log-queue");
        }
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("TRUNCATE log_processing.normalized_http, log_processing.normalized_flow, log_processing.drop_audit RESTART IDENTITY");
            } catch (Exception e) {
                // Ignore if tables don't exist yet
            }
        }
        if (rabbitAdmin != null) {
            rabbitAdmin.purgeQueue("log.raw", false);
            rabbitAdmin.purgeQueue("log.normalized.http", false);
            rabbitAdmin.purgeQueue("log.normalized.flow", false);
            rabbitAdmin.purgeQueue("log.raw.dlq", false);
        }
    }
}
