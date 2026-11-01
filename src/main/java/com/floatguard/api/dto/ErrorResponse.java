package com.floatguard.api.dto;

import java.util.Map;

/**
 * Uniform error envelope returned by every failing endpoint.
 *
 * @param error     machine-readable code, e.g. VALIDATION_ERROR / NOT_FOUND /
 *                  CONFLICT / INSUFFICIENT_FLOAT / INTERNAL_ERROR
 * @param message   human-readable explanation
 * @param details   optional field-level messages (validation errors)
 * @param path      request path that produced the error
 * @param timestamp when the error was produced
 */
public record ErrorResponse(String error,
                            String message,
                            Map<String, String> details,
                            String path,
                            String timestamp) {
}
