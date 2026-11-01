package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.domain.WardRecord;
import co.wethinkcode.healthsafe.infrastructure.csv.WardCsvLoader;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;

/**
 * Starts the Ingestion Service after the required ward dataset
 * has been loaded successfully.
 */
public class IngestionServiceApp {

    private static final int PORT = 7030;
    private static final String CSV_RESOURCE = "/wards-outdated.csv";

    public static void main(String[] args) {

        WardCsvLoader loader = new WardCsvLoader();

        List<WardRecord> wards;

        try {
            wards = loader.loadFromResource(CSV_RESOURCE);

        } catch (Exception exception) {
            // Do not start the service with an incomplete dataset.
            throw new IllegalStateException(
                    "Ingestion Service cannot start because the required "
                            + "ward CSV could not be loaded.",
                    exception
            );
        }

        List<WardRecord> loadedWards = List.copyOf(wards);

        System.out.printf(
                "[ingestion] successfully loaded %d ward records%n",
                loadedWards.size()
        );

        Javalin app = Javalin.create().start(PORT);

        // This endpoint is available only after the required CSV
        // has been loaded successfully.
        app.get("/health", ctx -> ctx.result("OK"));

        // Expose the cleaned and deduplicated ward catalogue.
        app.get("/wards", ctx -> ctx.json(loadedWards));

        app.exception(Exception.class, (exception, ctx) ->
                ctx.status(500).json(
                        Map.of("error", "Internal ingestion error")
                )
        );
    }
}