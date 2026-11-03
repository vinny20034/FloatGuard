package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.AdminAgentResponse;
import com.floatguard.api.dto.AdminProfileResponse;
import com.floatguard.api.dto.AdminRebalanceRequestResponse;
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

        app.get("/admin/profile", ctx -> {
            var user = com.floatguard.api.AuthMiddleware.currentUser(ctx);
            ServiceResults.AgentRunway runway = service.getSuperAgentProfile(user.getAgentId());
            Agent profile = runway.agent();
            List<WalletRunwayResponse> wallets = runway.estimates().stream()
                    .map(estimate -> new WalletRunwayResponse(
                            estimate.walletId(), estimate.walletType().name(), estimate.balance(),
                            estimate.runwayDays(), estimate.warning()))
                    .toList();
            Responses.send(ctx, 200, new AdminProfileResponse(
                    profile.getId(), profile.getName(), profile.getLocation(),
                    profile.getPhoneNumber(), wallets, runway.overallWarning()));
        });

        app.get("/admin/rebalance-requests", ctx -> {
            var user = com.floatguard.api.AuthMiddleware.currentUser(ctx);
            List<AdminRebalanceRequestResponse> requests =
                    service.listRebalanceRequests(user.getId(), user.getAgentId()).stream()
                    .map(request -> new AdminRebalanceRequestResponse(
                            request.id(),
                            request.agentId(),
                            request.agentName(),
                            request.agentPhoneNumber(),
                            request.location(),
                            request.area(),
                            request.type().name(),
                            request.amount(),
                            request.status(),
                            request.handoffCount(),
                            request.createdAt(),
                            request.completedAt()))
                    .toList();
            Responses.send(ctx, 200, Map.of("requests", requests));
        });

        app.get("/admin/agents", ctx -> {
            var user = com.floatguard.api.AuthMiddleware.currentUser(ctx);
            List<AdminAgentResponse> agents = new ArrayList<>();
            for (ServiceResults.AgentRunway runway : service.listAgentRunways(user.getAgentId())) {
                Agent agent = runway.agent();

                List<WalletRunwayResponse> wallets = new ArrayList<>();
                runway.estimates().forEach(estimate -> wallets.add(new WalletRunwayResponse(
                        estimate.walletId(),
                        estimate.walletType().name(),
                        estimate.balance(),
                        estimate.runwayDays(),
                        estimate.warning())));

                agents.add(new AdminAgentResponse(
                        agent.getId(), agent.getName(), agent.getLocation(), agent.getPhoneNumber(),
                        wallets, runway.overallWarning()));
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

            var user = com.floatguard.api.AuthMiddleware.currentUser(ctx);
            ServiceResults.OperationLogEntry entry =
                    service.assignFloat(user.getAgentId(), agentId, type, request.amount());

            Responses.send(ctx, 200, new AssignFloatResponse(
                    agentId,
                    entry.wallet().getId(),
                    type.name(),
                    entry.operation().getAmount(),
                    entry.balanceAfter()));
        });
    }
}
