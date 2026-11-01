package co.wethinkcode.healthsafe.api;

import co.wethinkcode.healthsafe.application.StaffingUpdateService;
import co.wethinkcode.healthsafe.application.WardCatalogService;
import io.javalin.Javalin;

import java.util.Locale;
import java.util.Map;

/**
 * Registers the REST endpoints exposed by Ward Service.
 *
 * The routes delegate to application services:
 * - WardCatalogService handles ward data.
 * - StaffingUpdateService handles the event-derived staffing state.
 *
 * Keeping the route handlers thin means HTTP concerns do not become mixed
 * with catalogue management or message processing rules.
 */
public class WardRoutes {

    private final WardCatalogService wardCatalog;
    private final StaffingUpdateService staffingUpdates;

    public WardRoutes(
            WardCatalogService wardCatalog,
            StaffingUpdateService staffingUpdates
    ) {
        this.wardCatalog = wardCatalog;
        this.staffingUpdates = staffingUpdates;
    }

    /**
     * Registers the ward and staffing-update API endpoints.
     */
    public void register(Javalin app) {

        // Basic health endpoint for local checks and service monitoring.
        app.get("/health", ctx -> ctx.result("OK"));

        // Return the current ward catalogue.
        app.get("/wards", ctx ->
                ctx.json(wardCatalog.findAll())
        );

        // Return one ward or HTTP 404 when its ID is not found.
        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id").trim().toUpperCase(Locale.ROOT);

            wardCatalog.findById(id).ifPresentOrElse(
                    // Javalin serializes the Ward object as JSON.
                    ctx::json,

                    // An unknown ward is a missing resource, not a server error.
                    () -> ctx.status(404).json(
                            Map.of("error", "Unknown ward: " + id)
                    )
            );
        });

        /*
         * Refresh modifies the catalogue, so POST is used instead of GET.
         * A failed call to Ingestion Service is reported as HTTP 503.
         */
        app.post("/admin/refresh", ctx -> {
            try {
                int size = wardCatalog.refresh();
                ctx.json(Map.of("refreshed", true, "size", size));

            } catch (RuntimeException e) {
                ctx.status(503).json(
                        Map.of("error", "Could not refresh wards from ingestion-service")
                );
            }
        });

        /*
         * These read-only endpoints expose the state updated by the
         * asynchronous JMS subscriber. They are useful for consumers,
         * acceptance tests and demonstrating the event flow.
         */
        app.get("/staffing-updates", ctx ->
                ctx.json(staffingUpdates.findAll())
        );

        app.get("/staffing-updates/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId");

            staffingUpdates.findByWardId(wardId).ifPresentOrElse(
                    ctx::json,
                    () -> ctx.status(404).json(
                            Map.of("error", "No staffing update for ward: " + wardId)
                    )
            );
        });
    }
}