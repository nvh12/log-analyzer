package com.nvh12.logprocessing.infrastructure.service;

import com.nvh12.logprocessing.domain.model.FailedLogEntry;
import com.nvh12.logprocessing.domain.model.RawLog;
import com.nvh12.logprocessing.domain.service.FailedLogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InMemoryFailedLogRepository implements FailedLogRepository {

  private static final int DLQ_CAPACITY = 2_000;

  private final BlockingQueue<FailedLogEntry> deadLetterQueue =
      new LinkedBlockingQueue<>(DLQ_CAPACITY);

  @Override
  public void save(RawLog rawLog, String reason) {
    FailedLogEntry entry = FailedLogEntry.of(rawLog, reason);

    if (!deadLetterQueue.offer(entry)) {
      // DLQ itself is full — last resort logging so nothing is silently lost
      log.error(
          "DLQ full — dropping log. id={}, receivedAt={}, reason={}",
          rawLog.getId(),
          rawLog.getReceivedAt(),
          reason);
    }
  }

  @Override
  public List<FailedLogEntry> getFailedLogEntries(int batchSize) {
    List<FailedLogEntry> batch = new ArrayList<>(batchSize);
    deadLetterQueue.drainTo(batch, batchSize);
    return batch;
  }

  // --- Observability ---

  public int deadLetterQueueSize() {
    return deadLetterQueue.size();
  }
}
