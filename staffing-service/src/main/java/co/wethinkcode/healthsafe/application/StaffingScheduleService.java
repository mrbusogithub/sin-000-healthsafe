package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.StaffingSchedule;
import co.wethinkcode.healthsafe.domain.WardSummary;
import co.wethinkcode.healthsafe.messaging.StaffingEvent;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contains the business rules for generating doctor staffing schedules.
 *
 * This is part of the application layer. It coordinates the required data
 * lookup, calculates the staffing requirement and asks the publisher to
 * broadcast the completed schedule.
 *
 * It does not contain Javalin routes or JMS implementation details.
 */
public class StaffingScheduleService {

    // Ward IDs are expected to contain letters, numbers and hyphens only.
    // This also prevents arbitrary path content from being used as an ID.
    private static final Pattern WARD_ID_PATTERN =
            Pattern.compile("[A-Z0-9-]{1,20}");

    private final StaffingDataClient dataClient;
    private final StaffingEventPublisher eventPublisher;

    /**
     * Dependencies are passed in through the constructor.
     *
     * This is constructor injection. It allows tests to replace the real
     * HTTP client and JMS publisher with simple test doubles.
     */
    public StaffingScheduleService(
            StaffingDataClient dataClient,
            StaffingEventPublisher eventPublisher
    ) {
        this.dataClient = dataClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Calculates a staffing schedule without publishing an event.
     *
     * This is used by GET /schedule/{wardId}, which should remain a
     * read-only operation.
     */
    public StaffingSchedule preview(String rawWardId) {

        // Validate and normalize the ID before looking up the ward.
        String wardId = normalizeWardId(rawWardId);

        // Get the ward details from the local snapshot.
        WardSummary ward = dataClient.findWard(wardId);

        // Emergency status is retrieved from Alert Level Service because
        // it may change while Staffing Service is running.
        int alertLevel = dataClient.getCurrentAlertLevel();

        // Demo scheduling assumption: the base requirement is two doctors,
        // with one additional doctor for each alert-level point.
        int doctorsOnCall = 2 + alertLevel;

        String department = ward.department().trim();

        // Normalize department casing before applying department-specific rules.
        // Locale.ROOT avoids casing behaviour depending on the machine's locale.
        String normalizedDepartment =
                department.toUpperCase(Locale.ROOT);

        switch (normalizedDepartment) {
            case "ICU" ->
                // ICU receives two additional doctors because it is treated
                // as a higher-support department in this demo calculation.
                    doctorsOnCall += 2;

            case "CARDIOLOGY", "MATERNITY" ->
                // These departments receive one additional doctor.
                    doctorsOnCall += 1;

            default -> {
                // All other departments use the base calculation.
            }
        }

        // Create the completed schedule with the time it was calculated.
        return new StaffingSchedule(
                ward.wardId(),
                department,
                alertLevel,
                doctorsOnCall,
                "next-24h",
                Instant.now().toString()
        );
    }

    /**
     * Generates a schedule and publishes the corresponding event.
     *
     * Publishing happens only after the schedule has been calculated
     * successfully. A failed ward lookup or alert-level lookup therefore
     * cannot produce an incomplete staffing event.
     */
    public StaffingSchedule generate(String wardId) {

        StaffingSchedule schedule = preview(wardId);

        try {
            // Convert the schedule into the shared event contract and publish it.
            eventPublisher.publish(StaffingEvent.from(schedule));

        } catch (RuntimeException e) {
            // Do not report successful generation if the event could not
            // be sent to the broker. The caller receives an HTTP 503.
            throw new StaffingServiceException(
                    503,
                    "Schedule could not be published because ActiveMQ is unavailable",
                    e
            );
        }

        return schedule;
    }

    /**
     * Normalizes and validates a ward ID received from an API request.
     *
     * Normalizing casing means callers can use either "w-05" or "W-05".
     */
    private String normalizeWardId(String rawWardId) {
        if (rawWardId == null || rawWardId.isBlank()) {
            throw new StaffingServiceException(400, "Ward ID is required");
        }

        String wardId = rawWardId.trim().toUpperCase(Locale.ROOT);

        if (!WARD_ID_PATTERN.matcher(wardId).matches()) {
            throw new StaffingServiceException(400, "Invalid ward ID");
        }

        return wardId;
    }
}