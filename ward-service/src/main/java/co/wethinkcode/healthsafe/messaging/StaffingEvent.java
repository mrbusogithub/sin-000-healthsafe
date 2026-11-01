package co.wethinkcode.healthsafe.messaging;

/**
 * Represents a staffing-update message received from ActiveMQ.
 *
 * This record intentionally mirrors the publisher's event contract.
 * Ward Service uses its own local class rather than importing code from
 * Staffing Service, preserving independent service deployment.
 *
 * If the event format changes, update and test both copies together.
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

    // The consumer accepts only this event type.
    public static final String EVENT_TYPE = "STAFFING_SCHEDULE_UPDATED";

    // Reject messages using a contract version this consumer does not support.
    public static final int SCHEMA_VERSION = 1;
}