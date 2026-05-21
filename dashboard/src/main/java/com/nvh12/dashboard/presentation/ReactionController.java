package com.nvh12.dashboard.presentation;

import com.nvh12.dashboard.application.PageView;
import com.nvh12.dashboard.application.ReactionSummaryView;
import com.nvh12.dashboard.application.port.IpBlockPort;
import com.nvh12.dashboard.application.port.ReactionRepository;
import com.nvh12.dashboard.domain.ReactionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionRepository reactionRepository;
    private final IpBlockPort ipBlockPort;

    @GetMapping
    public PageView<ReactionSummaryView> listReactions(
            @RequestParam(required = false) ReactionAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reactionRepository.findFiltered(action, from, to, page, size);
    }

    @GetMapping("/active")
    public Map<String, List<Map<String, Object>>> activeReactions() {
        return Map.of(
                "blocklist", ipBlockPort.listBlockedIps(),
                "rate_limits", ipBlockPort.listRateLimitedIps()
        );
    }

    @PostMapping("/{id}/lift")
    public ResponseEntity<?> liftBlock(@PathVariable Long id) {
        return reactionRepository.findById(id)
                .map(log -> {
                    if (log.sourceIp() != null) {
                        ipBlockPort.liftBlock(log.sourceIp());
                    }
                    return ResponseEntity.<Void>ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
