package co.wethinkcode.healthsafe.messaging;

import co.wethinkcode.healthsafe.domain.StaffingSchedule;

import java.util.UUID;

/**
 * Defines the message contract shared between Staffing Service and
 * Ward Service.
 *
 * This object is converted to JSON before being sent to ActiveMQ.
 * Ward Service has its own copy of this record because the two services
 * are independent Maven applications.
 *
 * Keep the field names and types consistent in both services. Changing
 * this contract may require changes to both the publisher and consumer.
 */
public record StaffingEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        String wardId,
        String department,
        int alertLevel,
        int doctorsOnCall,
        String scheduleFor,
        String generatedAt
) {

    // Identifies the kind of event so consumers can reject unsupported types.
    public static final String EVENT_TYPE = "STAFFING_SCHEDULE_UPDATED";

    // Allows consumers to identify which version of the event contract they received.
    public static final int SCHEMA_VERSION = 1;

    /**
     * Converts a calculated schedule into an event that can be broadcast.
     *
     * A new event ID is generated for every publication. This allows
     * consumers and logs to identify individual messages.
     */
    public static StaffingEvent from(StaffingSchedule schedule) {
        return new StaffingEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                SCHEMA_VERSION,
                schedule.wardId(),
                schedule.department(),
                schedule.alertLevel(),
                schedule.doctorsOnCall(),
                schedule.scheduleFor(),
                schedule.generatedAt()
        );
    }
}