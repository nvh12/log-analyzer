package com.nvh12.log_processing.domain.model;

import lombok.Builder;
import lombok.Value;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Map;

@Value
@Builder
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PUBLIC)
@JsonDeserialize(builder = RawLog.RawLogBuilder.class)
public class RawLog {
    String id;
    String rawMessage;
    LogSource source;
    Instant receivedAt;
    Map<String, String> headers;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class RawLogBuilder {
    }
}
