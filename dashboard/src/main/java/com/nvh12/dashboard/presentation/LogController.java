package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.application.FlowLogView;
import com.nvh12.dashboard.application.HttpLogView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.port.FlowLogRepository;
import com.nvh12.dashboard.application.port.HttpLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final HttpLogRepository httpLogRepository;
    private final FlowLogRepository flowLogRepository;

    @GetMapping("/http")
    public PageView<HttpLogView> listHttpLogs(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return httpLogRepository.findFiltered(ip, status, from, to, page, size);
    }

    @GetMapping("/http/{id}")
    public ResponseEntity<HttpLogView> getHttpLog(@PathVariable Long id) {
        return httpLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/flow")
    public PageView<FlowLogView> listFlowLogs(
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) Integer dstPort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return flowLogRepository.findFiltered(srcIp, dstPort, from, to, page, size);
    }

    @GetMapping("/flow/{id}")
    public ResponseEntity<FlowLogView> getFlowLog(@PathVariable Long id) {
        return flowLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
