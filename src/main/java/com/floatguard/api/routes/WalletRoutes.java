package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.CreateWalletRequest;
import com.floatguard.api.dto.WalletResponse;
import com.floatguard.model.integration.FloatWallet;
import com.floatguard.model.integration.WalletType;
import com.floatguard.service.FloatGuardService;
import com.floatguard.service.ServiceResults;
import com.floatguard.util.Json;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Route for the wallet resource:
 *   POST /agents/{id}/wallets - create a CASH or EFLOAT wallet for an agent
 */
public final class WalletRoutes {

    private WalletRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {

        app.post("/agents/{id}/wallets", ctx -> {
            String agentId = ctx.pathParam("id");
            CreateWalletRequest request = Json.read(ctx.body(), CreateWalletRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            WalletType type = WalletType.fromString(request.type());
            if (type == null) {
                errors.put("type", "type is required and must be one of CASH, EFLOAT.");
            }
            // The opening balance is optional; when omitted the wallet starts at zero.
            double openingBalance = request.openingBalance() == null ? 0.0 : request.openingBalance();
            if (!Double.isFinite(openingBalance) || openingBalance < 0) {
                errors.put("openingBalance", "openingBalance must be zero or greater.");
            }
            Validation.failIfAny(errors);

            ServiceResults.WalletCreation creation = service.createWallet(agentId, type, openingBalance);
            FloatWallet wallet = creation.wallet();

            Responses.send(ctx, 201, new WalletResponse(
                    wallet.getId(),
                    wallet.getAgentId(),
                    wallet.getType().name(),
                    wallet.getBalance(),
                    creation.createdAt().toString()));
        });
    }
}
