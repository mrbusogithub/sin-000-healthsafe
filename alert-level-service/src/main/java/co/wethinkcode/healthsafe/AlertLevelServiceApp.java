package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.api.AlertLevelRoutes;
import co.wethinkcode.healthsafe.application.AlertLevelService;
import io.javalin.Javalin;

/**
 * Application entry point for the Alert Level Service.
 *
 * Creates the application dependencies, registers the routes
 * and starts the HTTP server.
 */
public class AlertLevelServiceApp {

    private static final int PORT = 7032;

    public static void main(String[] args) {

        AlertLevelService alertLevelService =
                new AlertLevelService();

        Javalin app = Javalin.create();

        new AlertLevelRoutes(alertLevelService)
                .register(app);

        app.start(PORT);
    }
}