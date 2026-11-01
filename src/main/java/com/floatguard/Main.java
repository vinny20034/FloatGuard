package com.floatguard;

import com.floatguard.api.ApiRoutes;
import com.floatguard.config.AppConfig;
import com.floatguard.persistence.Database;
import com.floatguard.persistence.SchemaInitializer;
import com.floatguard.persistence.dao.AgentDao;
import com.floatguard.persistence.dao.OperationDao;
import com.floatguard.persistence.dao.RebalanceDao;
import com.floatguard.persistence.dao.WalletDao;
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
        FloatGuardService service = new FloatGuardService(
                database,
                new AgentDao(),
                new WalletDao(),
                new OperationDao(),
                new RebalanceDao());

        // 3. REST server + static files.
        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
            // Serves the single-page UI from src/main/resources/public at "/".
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        });

        ApiRoutes.register(app, service);
        app.start(config.port());
        log.info("FloatGuard listening on http://localhost:{}", config.port());

        // 4. Graceful shutdown: stop accepting requests and let in-flight ones finish.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down FloatGuard...");
            app.stop();
        }, "floatguard-shutdown"));
    }
}
