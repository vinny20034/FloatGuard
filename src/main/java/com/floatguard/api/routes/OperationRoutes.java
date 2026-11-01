package com.floatguard.api.routes;

import com.floatguard.api.Responses;
import com.floatguard.api.Validation;
import com.floatguard.api.dto.OperationRequest;
import com.floatguard.api.dto.OperationResponse;
import com.floatguard.model.integration.CustomerDeposit;
import com.floatguard.model.integration.CustomerWithdrawal;
import com.floatguard.model.integration.Operation;
import com.floatguard.service.FloatGuardService;
import com.floatguard.service.ServiceResults;
import com.floatguard.util.Json;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes for logging customer operations:
 *   POST /deposit  - money into the float
 *   POST /withdraw - money out of the float (may return 409 InsufficientFloat)
 *
 * Both endpoints share the same validation and response shape, so they differ
 * only by which domain {@link Operation} is constructed.
 */
public final class OperationRoutes {

    private OperationRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service) {
        app.post("/deposit", ctx -> handle(ctx, service, true));
        app.post("/withdraw", ctx -> handle(ctx, service, false));
    }

    private static void handle(Context ctx, FloatGuardService service, boolean deposit) {
        OperationRequest request = Json.read(ctx.body(), OperationRequest.class);

        Map<String, String> errors = new LinkedHashMap<>();
        Validation.requireNonBlank(errors, "walletId", request.walletId());
        Validation.requirePositiveAmount(errors, "amount", request.amount());
        Validation.failIfAny(errors);

        String walletId = request.walletId().trim();
        Instant timestamp = Instant.now();

        // Polymorphism: the concrete Operation decides the sign of the balance change.
        Operation operation = deposit
                ? new CustomerDeposit(request.amount(), timestamp)
                : new CustomerWithdrawal(request.amount(), timestamp);

        // May throw InsufficientFloatException -> mapped to 409 by ErrorMapper.
        ServiceResults.OperationLogEntry entry = service.logOperation(walletId, operation);

        // balanceBefore is derived: balanceAfter minus the signed effect of the operation.
        double balanceBefore = entry.balanceAfter() - operation.signedAmount();

        Responses.send(ctx, 200, new OperationResponse(
                entry.id(),
                walletId,
                operation.getOperationType(),
                operation.getAmount(),
                entry.balanceAfter(),
                balanceBefore,
                operation.getTimestamp().toString(),
                entry.rebalanceRequestId()));
    }
}
