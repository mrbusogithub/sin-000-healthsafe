package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.api.StaffingRoutes;
import co.wethinkcode.healthsafe.application.StaffingScheduleService;
import co.wethinkcode.healthsafe.infrastructure.http.HttpStaffingDataClient;
import co.wethinkcode.healthsafe.infrastructure.messaging.JmsStaffingEventPublisher;
import io.javalin.Javalin;

/**
 * Entry point for Staffing Service.
 *
 * This class is deliberately kept small. Its responsibility is to create
 * the required objects, connect their dependencies and start the HTTP server.
 *
 * Business logic belongs in StaffingScheduleService, HTTP integration belongs
 * in HttpStaffingDataClient, and ActiveMQ publishing belongs in
 * JmsStaffingEventPublisher.
 */
public class StaffingServiceApp {

    public static void main(String[] args) throws Exception {

        // Read service URLs from environment variables so they can be changed
        // between local development, testing and deployment.
        String ingestionWardsUrl = System.getenv()
                .getOrDefault(
                        "INGESTION_SERVICE_URL",
                        "http://localhost:7030/wards"
                );

        String alertLevelServiceUrl = System.getenv()
                .getOrDefault(
                        "ALERT_LEVEL_SERVICE_URL",
                        "http://localhost:7032/alert-level"
                );

        // Create the adapter that retrieves ward and alert-level data.
        HttpStaffingDataClient dataClient =
                new HttpStaffingDataClient(
                        ingestionWardsUrl,
                        alertLevelServiceUrl
                );

        /*
         * Load the ward catalogue before accepting schedule requests.
         * If this fails, startup stops instead of running with an empty
         * catalogue and incorrectly reporting every ward as unknown.
         */
        dataClient.loadWardCatalogue();

        // Create the ActiveMQ publisher after the required ward data is loaded.
        // The broker must be available for Staffing Service to publish events.
        JmsStaffingEventPublisher publisher =
                new JmsStaffingEventPublisher();

        // Inject the dependencies into the application service.
        StaffingScheduleService scheduleService =
                new StaffingScheduleService(dataClient, publisher);

        // Create the HTTP application and register its routes.
        Javalin app = Javalin.create();

        new StaffingRoutes(scheduleService).register(app);

        // Start listening for API requests on port 7033.
        app.start(7033);

        /*
         * Register cleanup to run when the JVM shuts down.
         * This stops the web server and releases the JMS resources.
         */
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            publisher.close();
        }));
    }
}