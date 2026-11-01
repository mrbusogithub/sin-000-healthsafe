package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.messaging.StaffingEvent;

/**
 * Defines how the application publishes a staffing event.
 *
 * This interface keeps the scheduling logic independent of ActiveMQ.
 * The application works with the interface instead of directly creating
 * JMS connections.
 *
 * This also makes unit testing easier because tests can provide a fake
 * publisher without requiring a running message broker.
 */
public interface StaffingEventPublisher {

    /**
     * Publishes a completed staffing event to the configured destination.
     *
     * Implementations are responsible for serializing and transporting it.
     */
    void publish(StaffingEvent event);
}