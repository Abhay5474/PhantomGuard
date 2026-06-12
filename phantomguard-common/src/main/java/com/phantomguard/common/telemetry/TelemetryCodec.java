package com.phantomguard.common.telemetry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Shared JSON codec for {@link TelemetryEvent} so the data plane (producer)
 * and control plane (consumer) agree on the exact wire representation that
 * transits Redis.
 */
public final class TelemetryCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private TelemetryCodec() {
    }

    public static String encode(TelemetryEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize telemetry event", e);
        }
    }

    public static TelemetryEvent decode(String json) {
        try {
            return MAPPER.readValue(json, TelemetryEvent.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize telemetry event", e);
        }
    }
}
