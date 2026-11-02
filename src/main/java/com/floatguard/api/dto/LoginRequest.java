package com.floatguard.api.dto;

/** Request body for {@code POST /auth/login}. */
public record LoginRequest(String username, String password) {
}
