package com.nvh12.log_processing.infrastructure.service;

import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.service.QueueService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@AllArgsConstructor
public class RedisQueueService implements QueueService {
  private static final String QUEUE_KEY = "raw-log-queue";
  private static final int MAIN_QUEUE_CAPACITY = 10_000;

  @Autowired
  private StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public boolean enqueue(RawLog rawLog) {
    Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
    if (size != null && size >= MAIN_QUEUE_CAPACITY) {
      return false;
    }

    try {
      double score = rawLog.getReceivedAt().toEpochMilli();
      String value = objectMapper.writeValueAsString(rawLog);

      return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(QUEUE_KEY, value, score));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<RawLog> dequeueBatch(int batchSize) {
    Set<ZSetOperations.TypedTuple<String>> values =
        redisTemplate.opsForZSet().popMin(QUEUE_KEY, batchSize);

    if (values == null) {
      return List.of();
    }

    return values.stream()
        .map(ZSetOperations.TypedTuple::getValue)
        .filter(Objects::nonNull)
        .map(
            v -> {
              try {
                return objectMapper.readValue(v, RawLog.class);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  @Override
  public RawLog dequeue() {
    ZSetOperations.TypedTuple<String> tuple = redisTemplate.opsForZSet().popMin(QUEUE_KEY);

    if (tuple == null) {
      return null;
    }

    try {
      return objectMapper.readValue(tuple.getValue(), RawLog.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
