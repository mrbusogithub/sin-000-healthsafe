package co.wethinkcode.healthsafe.domain;

/**
 * Represents the staffing schedule calculated for one hospital ward.
 *
 * This is a domain object. It contains the result of the scheduling
 * calculation and does not know anything about HTTP or ActiveMQ.
 *
 * Records are suitable here because a generated schedule is a simple
 * data carrier whose fields are not expected to change after creation.
 */
public record StaffingSchedule(
        String wardId,
        String department,
        int alertLevel,
        int doctorsOnCall,
        String scheduleFor,
        String generatedAt
) {
}