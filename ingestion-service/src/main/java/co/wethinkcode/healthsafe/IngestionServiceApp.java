package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) {
        WardCsvLoader loader = new WardCsvLoader();
        List<WardRecord> wards;
        try {
            wards = loader.loadFromResource("/wards-outdated.csv");
            System.out.printf("[ingestion] loaded %d clean ward records%n", wards.size());
        } catch (Exception e) {
            System.err.println("[ingestion] CSV load failed: " + e.getMessage());
            wards = List.of();
        }

        List<WardRecord> finalWards = wards;

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(finalWards));

        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(java.util.Map.of("error", e.getMessage())));
    }
}