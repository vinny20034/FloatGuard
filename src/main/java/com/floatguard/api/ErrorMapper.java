package com.floatguard.api;

import com.floatguard.error.ConflictException;
import com.floatguard.error.NotFoundException;
import com.floatguard.error.ValidationException;
import com.floatguard.model.integration.InsufficientFloatException;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Central translation of exceptions to HTTP status codes. Keeping this in one
 * place means routes can just let exceptions bubble up and never repeat
 * try/catch -> status mapping logic.
 *
 * <p>Status contract:</p>
 * <ul>
 *   <li>ValidationException        -> 400 Bad Request</li>
 *   <li>IllegalArgumentException   -> 400 Bad Request (defensive; guards domain constructors)</li>
 *   <li>NotFoundException          -> 404 Not Found</li>
 *   <li>ConflictException          -> 409 Conflict</li>
 *   <li>InsufficientFloatException -> 409 Conflict (domain rule)</li>
 *   <li>anything else              -> 500 Internal Server Error (logged, not leaked)</li>
 * </ul>
 */
public final class ErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {
    }

    public static void register(Javalin app) {
        app.exception(ValidationException.class, (exception, ctx) ->
                Responses.error(ctx, 400, "VALIDATION_ERROR", exception.getMessage(), exception.getDetails()));

        // The domain rejects invalid values (e.g. a negative opening balance) with
        // IllegalArgumentException - treat that as bad input, not a server fault.
        app.exception(IllegalArgumentException.class, (exception, ctx) ->
                Responses.error(ctx, 400, "INVALID_ARGUMENT", exception.getMessage(), Map.of()));

        app.exception(NotFoundException.class, (exception, ctx) ->
                Responses.error(ctx, 404, "NOT_FOUND", exception.getMessage(), Map.of()));

        app.exception(ConflictException.class, (exception, ctx) ->
                Responses.error(ctx, 409, "CONFLICT", exception.getMessage(), Map.of()));

        app.exception(InsufficientFloatException.class, (exception, ctx) ->
                Responses.error(ctx, 409, "INSUFFICIENT_FLOAT", exception.getMessage(), Map.of()));

        // Catch-all: log the full stack trace server-side but return a generic body
        // so internal details (SQL, file paths) are never exposed to clients.
        app.exception(Exception.class, (exception, ctx) -> {
            log.error("Unhandled error handling {} {}", ctx.method(), ctx.path(), exception);
            Responses.error(ctx, 500, "INTERNAL_ERROR", "An unexpected error occurred.", Map.of());
        });
    }
}
