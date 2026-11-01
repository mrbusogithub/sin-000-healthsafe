package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.StaffingSchedule;
import co.wethinkcode.healthsafe.domain.WardSummary;
import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StaffingScheduleService.
 *
 * These tests focus on the scheduling rules and event-publication behaviour.
 * They use fake dependencies so they do not need a running HTTP service
 * or ActiveMQ broker.
 */
class StaffingScheduleServiceTest {

    @Test
    void calculatesStaffingForIcu() {
        // Arrange: simulate an ICU ward with emergency level 4.
        FakeDataClient data = new FakeDataClient(
                new WardSummary("W-05", "ICU"), 4
        );

        FakePublisher publisher = new FakePublisher();

        StaffingScheduleService service =
                new StaffingScheduleService(data, publisher);

        // Act: request a schedule preview.
        StaffingSchedule schedule = service.preview("w-05");

        // Assert: base 2 + alert level 4 + ICU adjustment 2 = 8.
        assertEquals(8, schedule.doctorsOnCall());
        assertEquals("W-05", schedule.wardId());
        assertEquals("ICU", schedule.department());
        assertEquals(4, schedule.alertLevel());

        // A preview must not publish a message.
        assertTrue(publisher.events.isEmpty());
    }

    @Test
    void addsOneDoctorForCardiology() {
        // Arrange: Cardiology receives one additional doctor.
        FakeDataClient data = new FakeDataClient(
                new WardSummary("W-01", "Cardiology"), 2
        );

        StaffingScheduleService service =
                new StaffingScheduleService(data, new FakePublisher());

        // Act: calculate the schedule.
        StaffingSchedule schedule = service.preview("W-01");

        // Assert: base 2 + alert level 2 + Cardiology adjustment 1 = 5.
        assertEquals(5, schedule.doctorsOnCall());
    }

    @Test
    void generatesScheduleAndPublishesEvent() {
        // Arrange: simulate a Paediatrics ward at alert level 3.
        FakeDataClient data = new FakeDataClient(
                new WardSummary("W-05", "Paediatrics"), 3
        );

        FakePublisher publisher = new FakePublisher();

        StaffingScheduleService service =
                new StaffingScheduleService(data, publisher);

        // Act: generate the schedule, which should also publish an event.
        StaffingSchedule schedule = service.generate("W-05");

        // Assert: verify the generated schedule.
        assertEquals(5, schedule.doctorsOnCall());

        // Only one event should have been published for this generation.
        assertEquals(1, publisher.events.size());

        StaffingEvent event = publisher.events.get(0);

        // Verify that the event contains the expected contract information.
        assertEquals(StaffingEvent.EVENT_TYPE, event.eventType());
        assertEquals(StaffingEvent.SCHEMA_VERSION, event.schemaVersion());
        assertEquals("W-05", event.wardId());
        assertEquals(3, event.alertLevel());
        assertEquals(5, event.doctorsOnCall());

        // Every published event must have an ID and generation timestamp.
        assertNotNull(event.eventId());
        assertNotNull(event.generatedAt());
    }

    @Test
    void rejectsInvalidWardId() {
        // Arrange: provide valid dependencies so only ID validation is tested.
        StaffingScheduleService service =
                new StaffingScheduleService(
                        new FakeDataClient(
                                new WardSummary("W-01", "Cardiology"), 0
                        ),
                        new FakePublisher()
                );

        // Act: an invalid path-like ID should produce an application error.
        StaffingServiceException exception = assertThrows(
                StaffingServiceException.class,
                () -> service.preview("../invalid")
        );

        // Assert: invalid request data maps to HTTP 400.
        assertEquals(400, exception.getStatus());
    }

    @Test
    void doesNotPublishWhenWardLookupFails() {
        /*
         * Arrange: simulate an unknown ward.
         * The fake client throws the same type of application error that
         * the real data client would throw when the ward cannot be found.
         */
        StaffingDataClient failingClient = new StaffingDataClient() {
            @Override
            public WardSummary findWard(String wardId) {
                throw new StaffingServiceException(404, "Unknown ward");
            }

            @Override
            public int getCurrentAlertLevel() {
                return 0;
            }
        };

        FakePublisher publisher = new FakePublisher();

        StaffingScheduleService service =
                new StaffingScheduleService(failingClient, publisher);

        // Act and assert: schedule generation must fail.
        assertThrows(
                StaffingServiceException.class,
                () -> service.generate("W-99")
        );

        // A failed schedule must never be broadcast as a successful update.
        assertTrue(publisher.events.isEmpty());
    }

    /**
     * Test implementation of StaffingDataClient.
     *
     * It returns predefined ward and alert-level data instead of making
     * real HTTP requests to other microservices.
     */
    private static class FakeDataClient implements StaffingDataClient {

        private final WardSummary ward;
        private final int alertLevel;

        private FakeDataClient(WardSummary ward, int alertLevel) {
            this.ward = ward;
            this.alertLevel = alertLevel;
        }

        @Override
        public WardSummary findWard(String wardId) {
            return ward;
        }

        @Override
        public int getCurrentAlertLevel() {
            return alertLevel;
        }
    }

    /**
     * Test implementation of StaffingEventPublisher.
     *
     * Instead of sending messages to ActiveMQ, it stores published events
     * in a list so the tests can verify whether an event was published.
     */
    private static class FakePublisher implements StaffingEventPublisher {

        private final List<StaffingEvent> events = new ArrayList<>();

        @Override
        public void publish(StaffingEvent event) {
            events.add(event);
        }
    }
}