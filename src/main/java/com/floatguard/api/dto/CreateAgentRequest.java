package com.floatguard.api.dto;

/** Request body for {@code POST /agents}. */
public record CreateAgentRequest(String name, String location, String nrcNumber, String phoneNumber) {
}
