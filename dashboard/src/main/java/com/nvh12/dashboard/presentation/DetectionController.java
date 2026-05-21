package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.application.DetectionDetailView;
import com.nvh12.dashboard.application.DetectionSummaryView;
import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.port.DetectionRepository;
import com.nvh12.dashboard.domain.DetectionType;
import com.nvh12.dashboard.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/detections")
@RequiredArgsConstructor
public class DetectionController {

    private final DetectionRepository detectionRepository;

    @GetMapping
    public PageView<DetectionSummaryView> listDetections(
            @RequestParam(required = false) DetectionType detectionType,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return detectionRepository.findFiltered(detectionType, severity, from, to, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetectionDetailView> getDetection(@PathVariable Long id) {
        return detectionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
