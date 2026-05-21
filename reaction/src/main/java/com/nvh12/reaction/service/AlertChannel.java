package com.nvh12.reaction.service;

import com.nvh12.reaction.service.dto.alert.Alert;

public interface AlertChannel {

    void alert(Alert alert);
}
