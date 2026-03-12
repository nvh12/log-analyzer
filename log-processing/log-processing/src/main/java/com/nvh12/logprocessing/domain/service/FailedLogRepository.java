package com.nvh12.logprocessing.domain.service;

import com.nvh12.logprocessing.domain.model.FailedLogEntry;
import com.nvh12.logprocessing.domain.model.RawLog;
import java.util.List;

public interface FailedLogRepository {
  void save(RawLog raw, String reason);

  List<FailedLogEntry> getFailedLogEntries(int batchSize);
}
