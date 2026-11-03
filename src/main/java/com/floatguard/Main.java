package com.floatguard;

import com.floatguard.api.ApiRoutes;
import com.floatguard.config.AppConfig;
import com.floatguard.persistence.Database;
import com.floatguard.persistence.SchemaInitializer;
import com.floatguard.persistence.dao.AgentDao;
import com.floatguard.persistence.dao.OperationDao;
import com.floatguard.persistence.dao.RebalanceDao;
import com.floatguard.persistence.dao.SessionDao;
import com.floatguard.persistence.dao.UserDao;
import com.floatguard.persistence.dao.WalletDao;
import com.floatguard.service.AuthService;
import com.floatguard.service.FloatGuardService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point: wires configuration -> persistence -> service -> routes
 * and starts the Javalin server.
 *
 * <p>Boot order matters:</p>
 * <ol>
 *   <li>read configuration and make sure the storage directory exists</li>
 *   <li>initialise the schema (idempotent)</li>
 *   <li>build the service graph</li>
 *   <li>register routes and start listening</li>
 *   <li>install a shutdown hook for a graceful stop</li>
 * </ol>
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        config.ensureStorageDirectory();

        // 1. Persistence: open the database and make sure the schema exists.
        Database database = new Database(config.jdbcUrl());
        SchemaInitializer.initialize(database);
        log.info("Database ready at {}", config.jdbcUrl());

        // 2. Service layer: DAOs are stateless, so they are plain instances.
        AgentDao agentDao = new AgentDao();
        WalletDao walletDao = new WalletDao();

        FloatGuardService service = new FloatGuardService(
                database,
                agentDao,
                walletDao,
                new OperationDao(),
                new RebalanceDao());

        // 3. Authentication: accounts, opaque bearer tokens, admin invite code.
        AuthService authService = new AuthService(
                database,
                new UserDao(),
                new SessionDao(),
                agentDao,
                walletDao,
                config.adminInviteCodes());

        // 4. REST server + static files.
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
            javalinConfig.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.allowHost(
                    "http://localhost:5500",
                    "http://127.0.0.1:5500",
                    "http://localhost:5501",
                    "http://127.0.0.1:5501")));
            // Serves the single-page UI from src/main/resources/public at "/".
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                // Rebuilt HTML/CSS/JS must never be reused from the browser cache, or a
                // tab left open across a rebuild keeps running the previous JavaScript.
                staticFiles.headers = java.util.Map.of("Cache-Control", "no-store");
            });
        });

        ApiRoutes.register(app, service, authService);
        app.start(config.port());
        log.info("FloatGuard listening on http://localhost:{}", config.port());

        // 5. Graceful shutdown: stop accepting requests and let in-flight ones finish.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down FloatGuard...");
            app.stop();
        }, "floatguard-shutdown"));
    }
}
