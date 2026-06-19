package com.nvh12.log_processing.domain.service;

import com.nvh12.log_processing.domain.model.HttpMethod;
import com.nvh12.log_processing.domain.model.NormalizedFlowRecord;
import com.nvh12.log_processing.domain.model.NormalizedLog;
import com.nvh12.log_processing.domain.model.ProcessingResult;
import com.nvh12.log_processing.domain.model.RawLog;
import com.nvh12.log_processing.domain.model.ValidationLimits;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogProcessingService {

    // Matches both CLF and Combined Log Format (with optional referer + user-agent)
    private static final Pattern CLF_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"(\\S+) (\\S+)[^\"]*\" (\\d+) (\\S+)" +
                    "(?:\\s+\"([^\"]*)\" \"([^\"]*)\")?$"
    );

    private static final DateTimeFormatter CLF_DATE =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private final ObjectMapper objectMapper;
    private final ValidationLimits validation;

    public LogProcessingService(ObjectMapper objectMapper, ValidationLimits validation) {
        this.objectMapper = objectMapper;
        this.validation = validation;
    }

    public ProcessingResult process(RawLog rawLog) {
        return switch (rawLog.getSource()) {
            case FLOW -> new ProcessingResult.Flow(parseFlow(rawLog));
            case HTTP -> new ProcessingResult.Http(parseHttp(rawLog));
        };
    }

    private NormalizedLog parseHttp(RawLog rawLog) {
        String rawMessage = rawLog.getRawMessage();
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Raw message is null or blank for log id=" + rawLog.getId());
        }

        Matcher m = CLF_PATTERN.matcher(rawMessage.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Unparseable CLF entry for log id=" + rawLog.getId());
        }

        String ip      = m.group(1);
        String fullUrl = m.group(4);
        String statusStr = m.group(5);
        String userAgent = m.group(8);

        if (ip.length() > validation.maxIpLength()) {
            throw new IllegalArgumentException("IP field too long in log id=" + rawLog.getId());
        }
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(m.group(3));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown HTTP method '" + m.group(3) + "' in log id=" + rawLog.getId());
        }
        if (fullUrl.length() > validation.maxUrlLength()) {
            throw new IllegalArgumentException("URL exceeds max length in log id=" + rawLog.getId());
        }
        if (userAgent != null && userAgent.length() > validation.maxUaLength()) {
            userAgent = userAgent.substring(0, validation.maxUaLength());
        }

        int statusCode = Integer.parseInt(statusStr);
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid status code " + statusCode + " in log id=" + rawLog.getId());
        }

        int q = fullUrl.indexOf('?');
        String url = q >= 0 ? fullUrl.substring(0, q) : fullUrl;
        String queryString = q >= 0 ? fullUrl.substring(q + 1) : "";

        double timestamp = ZonedDateTime.parse(m.group(2), CLF_DATE)
                .toInstant().toEpochMilli() / 1000.0;

        int responseSize = "-".equals(m.group(6)) ? 0 : Integer.parseInt(m.group(6));

        return new NormalizedLog(
                rawLog.getId(),
                timestamp,
                ip,
                method,
                url,
                statusCode,
                responseSize,
                queryString,
                rawLog.getHeaders() != null ? rawLog.getHeaders() : Map.of(),
                userAgent,
                m.group(7)
        );
    }

    @SuppressWarnings("unchecked")
    private NormalizedFlowRecord parseFlow(RawLog rawLog) {
        String rawMessage = rawLog.getRawMessage();
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Raw message is null or blank for log id=" + rawLog.getId());
        }
        try {
            Map<String, Object> root = objectMapper.readValue(rawMessage, Map.class);

            double timestamp = toDouble(root.get("timestamp"));
            String sourceIp = root.get("source_ip") != null ? String.valueOf(root.get("source_ip")) : "";
            String destIp = root.get("dest_ip") != null ? String.valueOf(root.get("dest_ip")) : "";
            int sourcePort = root.get("source_port") instanceof Number n ? n.intValue() : 0;
            int destPort = root.get("dest_port") instanceof Number n ? n.intValue() : 0;

            Object featuresVal = root.get("features");
            Map<String, Object> rawFeatures = featuresVal instanceof Map<?, ?> fm
                    ? (Map<String, Object>) fm : Map.of();
            Map<String, Double> features = new LinkedHashMap<>(rawFeatures.size());
            for (Map.Entry<String, Object> entry : rawFeatures.entrySet()) {
                features.put(entry.getKey(), sanitize(entry.getValue()));
            }

            return new NormalizedFlowRecord(rawLog.getId(), timestamp, sourceIp, destIp, sourcePort, destPort, features);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse flow record: " + e.getMessage(), e);
        }
    }

    private double toDouble(Object val) {
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    private double sanitize(Object val) {
        double d = toDouble(val);
        return (Double.isNaN(d) || Double.isInfinite(d)) ? 0.0 : d;
    }
}
