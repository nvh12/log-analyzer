package com.nvh12.dashboard.application;

import com.nvh12.dashboard.application.port.BroadcastPort;
import com.nvh12.dashboard.application.port.FlowLogRepository;
import com.nvh12.dashboard.application.port.HttpLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThroughputService {

    private final HttpLogRepository httpLogRepository;
    private final FlowLogRepository flowLogRepository;
    private final BroadcastPort broadcastPort;

    @Scheduled(fixedDelay = 2_000)
    public void computeAndBroadcast() {
        try {
            Instant since = Instant.now().minus(2, ChronoUnit.SECONDS);
            long httpCount = httpLogRepository.countSince(since);
            long flowCount = flowLogRepository.countSince(since);
            broadcastPort.broadcast("log_throughput", Map.of(
                    "http_per_sec", httpCount / 2.0,
                    "flow_per_sec", flowCount / 2.0,
                    "ts", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.debug("Throughput query failed: {}", e.getMessage());
        }
    }
}
