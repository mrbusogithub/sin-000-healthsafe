package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    private static final int MIN = 0;
    private static final int MAX = 8;

    private static final AtomicInteger LEVEL = new AtomicInteger(0);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> ctx.json(Map.of("level", LEVEL.get())));

        // POST and PUT both accepted; body = { "level": <int 0-8> }
        app.post("/alert-level", ctx -> handleUpdate(ctx));
        app.put("/alert-level",  ctx -> handleUpdate(ctx));

        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("error", e.getMessage())));
    }

    private static void handleUpdate(io.javalin.http.Context ctx) {
        AlertLevelRequest req;
        try {
            req = ctx.bodyAsClass(AlertLevelRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "invalid JSON body: " + e.getMessage()));
            return;
        }
        if (req == null || req.level == null) {
            ctx.status(400).json(Map.of("error", "missing 'level'"));
            return;
        }
        if (req.level < MIN || req.level > MAX) {
            ctx.status(400).json(Map.of(
                    "error", "level must be between " + MIN + " and " + MAX,
                    "received", req.level));
            return;
        }
        LEVEL.set(req.level);
        ctx.json(Map.of("level", LEVEL.get()));
    }

    /** Jackson-friendly request DTO. */
    public static class AlertLevelRequest {
        public Integer level;
    }
}