package com.floatguard.api.routes;

import com.floatguard.api.AuthMiddleware;
import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.AuthResponse;
import com.floatguard.api.dto.LoginRequest;
import com.floatguard.api.dto.RegisterRequest;
import com.floatguard.model.auth.Role;
import com.floatguard.model.auth.User;
import com.floatguard.service.AuthService;
import com.floatguard.service.ServiceResults;
import com.floatguard.util.Json;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication routes:
 *   POST /auth/register - create an ADMIN (needs invite code) or AGENT account
 *   POST /auth/login    - exchange credentials for a bearer token
 *   POST /auth/logout   - revoke the current token
 *   GET  /auth/me       - the signed-in account
 *
 * Register/login are public; logout and me require a token (see
 * {@link AuthMiddleware}).
 */
public final class AuthRoutes {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private AuthRoutes() {
    }

    public static void register(Javalin app, AuthService authService) {

        app.post("/auth/register", ctx -> {
            RegisterRequest request = Json.read(ctx.body(), RegisterRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            Validation.requireNonBlank(errors, "username", request.username());
            Validation.requireNonBlank(errors, "password", request.password());
            if (request.password() != null && request.password().length() < MIN_PASSWORD_LENGTH) {
                errors.put("password", "password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            }

            Role role = Role.fromString(request.role());
            if (role == null) {
                errors.put("role", "role is required and must be one of ADMIN, AGENT.");
            } else {
                Validation.requireNonBlank(errors, "agentName", request.agentName());
                Validation.requireNonBlank(errors, "location", request.location());
                Validation.requireNonBlank(errors, "nrcNumber", request.nrcNumber());
                Validation.requireNonBlank(errors, "phoneNumber", request.phoneNumber());
                Validation.requireNonNegativeAmount(errors, "openingCash", request.openingCash());
                Validation.requireNonNegativeAmount(errors, "openingEFloat", request.openingEFloat());
                if (role == Role.ADMIN) {
                    Validation.requireNonBlank(errors, "inviteCode", request.inviteCode());
                }
            }
            Validation.failIfAny(errors);

            ServiceResults.Authenticated auth = authService.register(
                    request.username(), request.password(), role, request.inviteCode(),
                    request.agentName(), request.location(), request.nrcNumber(), request.phoneNumber(),
                    request.openingCash(), request.openingEFloat());

            Responses.send(ctx, 201, toResponse(auth.user(), auth.token()));
        });

        app.post("/auth/login", ctx -> {
            LoginRequest request = Json.read(ctx.body(), LoginRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            Validation.requireNonBlank(errors, "username", request.username());
            Validation.requireNonBlank(errors, "password", request.password());
            Validation.failIfAny(errors);

            // May throw UnauthorizedException -> mapped to 401.
            ServiceResults.Authenticated auth = authService.login(request.username(), request.password());
            Responses.send(ctx, 200, toResponse(auth.user(), auth.token()));
        });

        app.post("/auth/logout", ctx -> {
            authService.logout(AuthMiddleware.bearerToken(ctx));
            Responses.send(ctx, 200, Map.of("status", "logged_out"));
        });

        app.get("/auth/me", ctx -> {
            User user = AuthMiddleware.currentUser(ctx);
            Responses.send(ctx, 200, toResponse(user, null));
        });
    }

    private static AuthResponse toResponse(User user, String token) {
        return new AuthResponse(token, user.getUsername(), user.getRole().name(), user.getAgentId());
    }
}
