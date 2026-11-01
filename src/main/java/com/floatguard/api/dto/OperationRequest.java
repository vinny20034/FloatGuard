package com.floatguard.api.dto;

/** Request body for {@code POST /deposit} and {@code POST /withdraw}. */
public record OperationRequest(String walletId, Double amount) {
}
