package com.nvh12.dashboard;

import com.nvh12.dashboard.application.ThroughputService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
public abstract class AbstractContainerIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5");
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.2.4-management");
    static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4"));

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
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "true");
        registry.add("dashboard.rabbitmq-management-url",
                () -> "http://" + rabbit.getHost() + ":" + rabbit.getMappedPort(15672));
        registry.add("dashboard.rabbitmq-management-user", rabbit::getAdminUsername);
        registry.add("dashboard.rabbitmq-management-password", rabbit::getAdminPassword);
    }

    @MockitoBean
    ThroughputService throughputService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("TRUNCATE log_processing.normalized_http RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE log_processing.normalized_flow RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE detection_results RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE reaction.reaction_logs RESTART IDENTITY CASCADE");
        stringRedisTemplate.execute((RedisCallback<Void>) conn -> {
            conn.serverCommands().flushDb();
            conn.serverCommands().resetConfigStats();
            return null;
        });
    }
}
