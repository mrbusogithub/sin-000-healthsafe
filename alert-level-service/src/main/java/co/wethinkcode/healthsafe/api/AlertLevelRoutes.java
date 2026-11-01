package co.wethinkcode.healthsafe.api;

import co.wethinkcode.healthsafe.application.AlertLevelService;
import co.wethinkcode.healthsafe.domain.AlertLevel;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

/**
 * Defines the HTTP endpoints for the Alert Level Service.
 *
 * This class translates HTTP requests and responses.
 * Business validation remains inside AlertLevelService.
 */
public class AlertLevelRoutes {

    private final AlertLevelService alertLevelService;

    public AlertLevelRoutes(AlertLevelService alertLevelService) {
        this.alertLevelService = alertLevelService;
    }

    /**
     * Registers all Alert Level HTTP endpoints.
     */
    public void register(Javalin app) {

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> {
            AlertLevel current =
                    alertLevelService.getCurrentLevel();

            ctx.json(Map.of(
                    "level",
                    current.value()
            ));
        });

        // Both methods are supported for compatibility with
        // the existing REST contract.
        app.post("/alert-level", this::handleUpdate);
        app.put("/alert-level", this::handleUpdate);
    }

    /**
     * Parses the request, delegates validation to the
     * application service and returns the updated value.
     */
    private void handleUpdate(Context ctx) {

        AlertLevelRequest request;

        try {
            request = ctx.bodyAsClass(AlertLevelRequest.class);

        } catch (Exception exception) {
            ctx.status(400).json(Map.of(
                    "error",
                    "invalid JSON body"
            ));
            return;
        }

        if (request == null || request.level == null) {
            ctx.status(400).json(Map.of(
                    "error",
                    "missing 'level'"
            ));
            return;
        }

        try {
            AlertLevel updated =
                    alertLevelService.updateLevel(request.level);

            ctx.json(Map.of(
                    "level",
                    updated.value()
            ));

        } catch (IllegalArgumentException exception) {
            ctx.status(400).json(Map.of(
                    "error",
                    exception.getMessage()
            ));
        }
    }

    /**
     * Request DTO used to deserialize:
     *
     * { "level": 5 }
     */
    public static class AlertLevelRequest {
        public Integer level;
    }
}