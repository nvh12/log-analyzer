# Log Formats

This document outlines the different logging formats supported or encountered by our application.

## 1. Normal (Legacy) Log Format

This represents traditional, line-based logs often found in standard output or `.log` files. It is optimized for human readability but requires complex Regex or parsing to be machine-readable.

### Example (Common Log Format)

```bash
2026-03-02T21:44:02Z INFO [user-service] 10.0.1.5 - "User login successful" trace_id=5b8efff798038103 span_id=eee19b7bc3c192d4
```

### Structure Breakdown

| Field | Description | Example |
| --- | --- | --- |
| Timestamp | ISO 8601 or RFC3339 date/time | `2026-03-02T21:44:02Z` |
| Severity | Human-readable log level | `INFO` |
| Source | The application or component name | `[user-service]` |
| Message | The unstructured text body | `User login successful` |
| Context | Key-value pairs often appended at the end | `trace_id=5b8e...` |

## 2. OTel JSON (OTLP) Log Format

This is the OpenTelemetry Protocol (OTLP) JSON representation. It is highly structured, hierarchical, and designed for interoperability between different observability backends.

### Example (OTLP JSON)

```json
{
  "resourceLogs": [{
    "resource": {
      "attributes": [{ "key": "service.name", "value": { "stringValue": "user-service" } }]
    },
    "scopeLogs": [{
      "scope": { "name": "auth-logger" },
      "logRecords": [{
        "timeUnixNano": "1772487842000000000",
        "severityNumber": 9,
        "severityText": "INFO",
        "body": { "stringValue": "User login successful" },
        "traceId": "5b8efff798038103d269b633813fc60c",
        "spanId": "eee19b7bc3c192d4",
        "attributes": [{ "key": "client.ip", "value": { "stringValue": "10.0.1.5" } }]
      }]
    }]
  }]
}
```

### Key OTLP Fields

- `timeUnixNano`: Precision timestamp in nanoseconds (e.g., 1772...).
- `severityNumber`: An integer mapping (1-24) for standardized filtering.
- `body`: A polymorphic field that can contain a stringValue, intValue, or a nested kvlistValue (map).
- `traceId` / `spanId`: Hex-encoded strings used to link the log to a specific request trace.
- `resource`: Metadata about the entity producing the log (e.g., host name, container ID, k8s pod).