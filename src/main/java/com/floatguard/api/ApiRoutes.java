package com.floatguard.api;

import com.floatguard.api.routes.AdminRoutes;
import com.floatguard.api.routes.AgentRoutes;
import com.floatguard.api.routes.AuthRoutes;
import com.floatguard.api.routes.OperationRoutes;
import com.floatguard.api.routes.RebalanceRoutes;
import com.floatguard.api.routes.WalletRoutes;
import com.floatguard.service.AuthService;
import com.floatguard.service.FloatGuardService;
import io.javalin.Javalin;

/** Single entry point that wires exception handling and every route group. */
public final class ApiRoutes {

    private ApiRoutes() {
    }

    public static void register(Javalin app, FloatGuardService service, AuthService authService) {
        // Exception -> HTTP mapping must be registered before the routes so that
        // failures raised inside the handlers are translated consistently.
        ErrorMapper.register(app);

        // Authentication / role guard runs before any handler on a protected path.
        AuthMiddleware.register(app, authService);

        AuthRoutes.register(app, authService);
        AdminRoutes.register(app, service);
        AgentRoutes.register(app, service);
        WalletRoutes.register(app, service);
        OperationRoutes.register(app, service);
        RebalanceRoutes.register(app, service);

        // Unmatched paths return the same JSON error envelope instead of Javalin's
        // default plain-text body. NOTE: Javalin invokes this handler for *any* 404,
        // including the ones produced by the NotFoundException handler above. We use
        // the ERROR_WRITTEN_ATTR flag (set by Responses.error) to distinguish the two:
        // if the flag is present, a meaningful "Unknown agent ..." body already exists
        // and must be preserved; otherwise this is a genuinely unmatched route.
        app.error(404, ctx -> {
            if (ctx.attribute(Responses.ERROR_WRITTEN_ATTR) == null) {
                Responses.error(ctx, 404, "NOT_FOUND",
                        "No route matches " + ctx.method() + " " + ctx.path() + ".", java.util.Map.of());
            }
        });
    }
}
