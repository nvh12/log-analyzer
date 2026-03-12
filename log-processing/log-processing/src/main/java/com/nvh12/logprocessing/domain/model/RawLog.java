package com.nvh12.logprocessing.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class RawLog {
  String id; // generated UUID
  String rawMessage; // original log text or JSON string
  String source; // service name or log origin
  Instant receivedAt; // ingestion timestamp
  Map<String, Object> headers; // optional transport metadata
}
