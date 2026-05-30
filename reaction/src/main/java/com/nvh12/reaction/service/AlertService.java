package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.alert.Alert;

public interface AlertService {

    void enqueue(Alert alert);
}
