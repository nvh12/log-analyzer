package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.alert.Alert;

import java.util.List;

public interface AlertChannel {

    void alert(Alert alert);

    default void alertBatch(List<Alert> alerts) {
        alerts.forEach(this::alert);
    }
}
