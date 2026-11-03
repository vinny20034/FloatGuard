package com.floatguard.api.dto;

/**
 * Request body for {@code POST /auth/register}.
 *
 * <p>{@code inviteCode} is only consulted for ADMIN registrations; {@code agentName}
 * and {@code location} are only required for AGENT registrations (the agent and its
 * two wallets are created automatically).</p>
 */
public record RegisterRequest(String username,
                              String password,
                              String role,
                              String inviteCode,
                              String agentName,
                              String location,
                              String nrcNumber,
                              String phoneNumber,
                              Double openingCash,
                              Double openingEFloat) {
}
