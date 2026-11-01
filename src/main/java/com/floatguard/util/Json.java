package com.floatguard.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floatguard.error.ValidationException;

/**
 * Single, shared Jackson configuration. Using one mapper (instead of Javalin's
 * default) keeps request parsing and response writing consistent, and lets us
 * decide explicitly how malformed input is reported.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // Unknown JSON fields are ignored rather than rejected: forwards-friendly
            // and avoids 400s when clients send extra metadata.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    /**
     * Parses a request body, turning malformed/missing JSON into a
     * {@link ValidationException} (HTTP 400) instead of a 500.
     */
    public static <T> T read(String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            throw new ValidationException("A JSON request body is required.");
        }
        try {
            T value = MAPPER.readValue(body, type);
            if (value == null) {
                throw new ValidationException("A JSON request body is required.");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new ValidationException("Malformed JSON request body: " + e.getOriginalMessage());
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Serialising our own DTOs should never fail; if it does it is a bug,
            // so surface it as an unexpected error (500).
            throw new IllegalStateException("Failed to serialise response body", e);
        }
    }
}
