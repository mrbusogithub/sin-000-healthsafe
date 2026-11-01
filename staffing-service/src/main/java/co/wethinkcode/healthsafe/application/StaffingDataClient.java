package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.WardSummary;

/**
 * Defines the external data that the scheduling application needs.
 *
 * The interface hides where this data comes from. The implementation
 * retrieves ward information from an Ingestion Service snapshot and
 * retrieves the current alert level from Alert Level Service.
 *
 * The scheduling service therefore does not need to know how HTTP works.
 */
public interface StaffingDataClient {

    /**
     * Finds a ward in Staffing Service's local ward catalogue.
     *
     * @param wardId normalized ward identifier
     * @return the ward information needed for scheduling
     */
    WardSummary findWard(String wardId);

    /**
     * Retrieves the hospital's current emergency status.
     *
     * @return alert level between 0 and 8
     */
    int getCurrentAlertLevel();
}