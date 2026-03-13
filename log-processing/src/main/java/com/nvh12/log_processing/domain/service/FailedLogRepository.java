package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.FailedLogEntry;
import com.nvh12.log_processing.domain.model.RawLog;
import java.util.List;

public interface FailedLogRepository {
  void save(RawLog raw, String reason);

  List<FailedLogEntry> getFailedLogEntries(int batchSize);
}
