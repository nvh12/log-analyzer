package com.nvh12.reaction;

import com.nvh12.reaction.config.RabbitMqConfig;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;


import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "SMTP_USERNAME=test-user",
        "SMTP_PASSWORD=test-password",
        "ALERT_MAIL_FROM=alerts@test.com",
        "ALERT_MAIL_TO=admin@test.com"
})
public abstract class AbstractContainerIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5");
    static final RabbitMQContainer rabbit       = new RabbitMQContainer("rabbitmq:4.2.4-management");
    static final RedisContainer redis           = new RedisContainer(DockerImageName.parse("redis:7.4"));

    static {
        postgres.start();
        rabbit.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host",     rabbit::getHost);
        registry.add("spring.rabbitmq.port",     rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        stringRedisTemplate.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });
        if (rabbitAdmin != null) {
            rabbitAdmin.purgeQueue(RabbitMqConfig.QUEUE_DETECTION_RESULTS, false);
        }
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("TRUNCATE reaction.reaction_logs RESTART IDENTITY");
        }
    }
}
