package co.wethinkcode.healthsafe.domain;

/**
 * Contains the ward information required by Staffing Service.
 *
 * Staffing Service does not need to copy the entire Ward model from
 * Ingestion Service. It only needs the ward ID and department to calculate
 * its schedule.
 *
 * Keeping this smaller model also prevents Staffing Service from depending
 * on another service's internal Java classes.
 */
public record WardSummary(
        String wardId,
        String department
) {
}