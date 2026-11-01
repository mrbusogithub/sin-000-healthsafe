package co.wethinkcode.healthsafe.api;

import co.wethinkcode.healthsafe.application.StaffingScheduleService;
import co.wethinkcode.healthsafe.application.StaffingServiceException;
import io.javalin.Javalin;

import java.util.Map;

/**
 * Handles HTTP requests received by Staffing Service.
 *
 * This class is responsible for defining API endpoints and delegating
 * schedule operations to StaffingScheduleService.
 *
 * It does not contain scheduling calculations or ActiveMQ publishing logic.
 */
public class StaffingRoutes {

    // Application service responsible for generating staffing schedules.
    private final StaffingScheduleService scheduleService;

    /**
     * Constructor injection provides the application service needed
     * by the HTTP route handlers.
     */
    public StaffingRoutes(StaffingScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Registers the HTTP endpoints exposed by Staffing Service.
     */
    public void register(Javalin app) {

        // Confirms that the service is running.
        app.get("/health", ctx -> ctx.result("OK"));

        // Returns a schedule preview without publishing an event.
        app.get("/schedule/{wardId}", ctx ->
                ctx.json(scheduleService.preview(ctx.pathParam("wardId")))
        );

        // Generates a schedule and publishes its staffing event.
        app.post("/schedule/{wardId}", ctx ->
                ctx.status(201).json(
                        scheduleService.generate(ctx.pathParam("wardId"))
                )
        );

        // Converts known application errors into the appropriate HTTP response.
        app.exception(StaffingServiceException.class, (exception, ctx) ->
                ctx.status(exception.getStatus())
                        .json(Map.of("error", exception.getMessage()))
        );

        // Handles unexpected errors without exposing internal implementation details.
        app.exception(Exception.class, (exception, ctx) ->
                ctx.status(500)
                        .json(Map.of("error", "Internal server error"))
        );
    }
}