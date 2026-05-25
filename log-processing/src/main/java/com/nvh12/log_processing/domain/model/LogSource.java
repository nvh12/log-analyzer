package com.nvh12.log_processing.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum LogSource {
    HTTP, FLOW;

    @JsonCreator
    public static LogSource fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
