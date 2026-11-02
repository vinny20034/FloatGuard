package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.AdminAgentResponse;
import com.floatguard.api.dto.AssignFloatRequest;
import com.floatguard.api.dto.AssignFloatResponse;
import com.floatguard.api.dto.WalletRunwayResponse;
import com.floatguard.model.integration.Agent;
import com.floatguard.model.integration.WalletType;
import com.floatguard.service.FloatGuardService;
import com.floatguard.service.ServiceResults;
import com.floatguard.util.Json;
import io.javalin.Javalin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrator-only routes (only ADMIN tokens reach these; see
 * {@link com.floatguard.api.AuthMiddleware}):
 *   GET  /admin/agents                    - every agent with balances + warnings
 *   POST /admin/agents/{id}/assign-float  - top up one of an agent's wallets
 */
public final class AdminRoutes {

    private AdminRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {

        app.get("/admin/agents", ctx -> {
            List<AdminAgentResponse> agents = new ArrayList<>();
            for (ServiceResults.AgentRunway runway : service.listAgentRunways()) {
                Agent agent = runway.agent();

                List<WalletRunwayResponse> wallets = new ArrayList<>();
                runway.estimates().forEach(estimate -> wallets.add(new WalletRunwayResponse(
                        estimate.walletId(),
                        estimate.walletType().name(),
                        estimate.balance(),
                        estimate.runwayDays(),
                        estimate.warning())));

                agents.add(new AdminAgentResponse(
                        agent.getId(), agent.getName(), agent.getLocation(), wallets, runway.overallWarning()));
            }
            Responses.send(ctx, 200, Map.of("agents", agents));
        });

        app.post("/admin/agents/{id}/assign-float", ctx -> {
            String agentId = ctx.pathParam("id");
            AssignFloatRequest request = Json.read(ctx.body(), AssignFloatRequest.class);

            Map<String, String> errors = new LinkedHashMap<>();
            WalletType type = WalletType.fromString(request.walletType());
            if (type == null) {
                errors.put("walletType", "walletType is required and must be one of CASH, EFLOAT.");
            }
            Validation.requirePositiveAmount(errors, "amount", request.amount());
            Validation.failIfAny(errors);

            ServiceResults.OperationLogEntry entry = service.assignFloat(agentId, type, request.amount());

            Responses.send(ctx, 200, new AssignFloatResponse(
                    agentId,
                    entry.wallet().getId(),
                    type.name(),
                    entry.operation().getAmount(),
                    entry.balanceAfter()));
        });
    }
}
