package com.floatguard.api.dto;

import java.util.List;

/** The signed-in super agent's profile and own wallet balances. */
public record AdminProfileResponse(String id,
                                   String name,
                                   String location,
                                   String phoneNumber,
                                   List<WalletRunwayResponse> wallets,
                                   String overallWarning) {
}
