package com.floatguard.api.dto;

/**
 * Response body for the auth endpoints.
 *
 * @param token   opaque bearer token to send as {@code Authorization: Bearer <token>};
 *                {@code null} for {@code GET /auth/me}
 * @param role    ADMIN or AGENT
 * @param agentId the agent this account owns, or {@code null} for an ADMIN
 */
public record AuthResponse(String token, String username, String role, String agentId) {
}
