package com.floatguard.api;

import com.floatguard.api.dto.ErrorResponse;
import com.floatguard.util.Json;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.Map;

/** Helpers for writing JSON responses with a consistent shape. */
public final class Responses {

    /**
     * Context attribute flag set whenever {@link #error} writes an envelope.
     * The global 404 handler (see {@code ApiRoutes}) uses it to tell an
     * intentional 404 ("Unknown agent ...") apart from an unmatched route, so it
     * only fills in a body for the latter.
     */
    public static final String ERROR_WRITTEN_ATTR = "floatguard.errorWritten";

    private Responses() {
    }

    public static void send(Context ctx, int status, Object body) {
        ctx.status(status);
        ctx.contentType("application/json");
        ctx.result(Json.write(body));
    }

    public static void error(Context ctx, int status, String code, String message, Map<String, String> details) {
        // Flag first: Javalin may invoke the registered 404 handler *after* this,
        // and it must be able to detect that a specific body was already produced.
        ctx.attribute(ERROR_WRITTEN_ATTR, Boolean.TRUE);
        send(ctx, status, new ErrorResponse(code, message, details, ctx.path(), Instant.now().toString()));
    }
}
