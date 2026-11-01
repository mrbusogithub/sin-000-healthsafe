package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.api.WardRoutes;
import co.wethinkcode.healthsafe.application.StaffingUpdateService;
import co.wethinkcode.healthsafe.application.WardCatalogService;
import co.wethinkcode.healthsafe.infrastructure.http.IngestionWardClient;
import co.wethinkcode.healthsafe.infrastructure.messaging.JmsStaffingEventConsumer;
import io.javalin.Javalin;

/**
 * Entry point for Ward Service.
 *
 * This class wires together the API, application services and infrastructure
 * adapters. It does not contain the actual ward lookup or event-processing
 * business rules.
 */
public class WardServiceApp {

    public static void main(String[] args) throws Exception {

        // Allow the Ingestion Service address to be changed without
        // changing application code.
        String ingestionUrl = System.getenv()
                .getOrDefault(
                        "INGESTION_SERVICE_URL",
                        "http://localhost:7030/wards"
                );

        // Create the client and application service responsible for ward data.
        WardCatalogService wardCatalog =
                new WardCatalogService(new IngestionWardClient(ingestionUrl));

        /*
         * Ingestion Service may take slightly longer to start than Ward Service.
         * Retry the initial catalogue load up to five times, with a two-second
         * pause between attempts, instead of immediately terminating.
         */
        boolean loaded = false;

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                wardCatalog.refresh();
                loaded = true;
                break;

            } catch (RuntimeException e) {
                System.err.printf(
                        "[ward-service] ingestion attempt %d/5 failed: %s%n",
                        attempt,
                        e.getMessage()
                );

                if (attempt < 5) {
                    try {
                        Thread.sleep(2_000);

                    } catch (InterruptedException interrupted) {
                        // Do not swallow a shutdown or cancellation signal.
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        /*
         * The existing startup behaviour allows the service to start even
         * when the catalogue could not be loaded. In that case, ward lookups
         * will not succeed until a later POST /admin/refresh works.
         */
        if (!loaded) {
            System.err.println(
                    "[ward-service] starting with an empty ward catalogue"
            );
        }

        // Create the application component that processes incoming events.
        StaffingUpdateService staffingUpdates =
                new StaffingUpdateService();

        /*
         * Connect to ActiveMQ and register the durable subscriber.
         * The broker must be running before Ward Service starts.
         */
        JmsStaffingEventConsumer consumer =
                new JmsStaffingEventConsumer(staffingUpdates);

        // Create the REST application and register its endpoints.
        Javalin app = Javalin.create();

        new WardRoutes(wardCatalog, staffingUpdates).register(app);

        // Start the Ward Service HTTP API on port 7031.
        app.start(7031);

        /*
         * Release the HTTP server and JMS resources during application shutdown.
         * This prevents the consumer connection from being left open.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            consumer.close();
        }));
    }
}