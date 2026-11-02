package com.floatguard.api.dto;

/** Request body for {@code POST /admin/agents/{id}/assign-float}. */
public record AssignFloatRequest(String walletType, Double amount) {
}
