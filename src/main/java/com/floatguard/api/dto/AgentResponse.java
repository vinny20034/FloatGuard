package com.floatguard.api.dto;

/** Response body for {@code POST /agents}. */
public record AgentResponse(String id, String name, String location, String createdAt) {
}
