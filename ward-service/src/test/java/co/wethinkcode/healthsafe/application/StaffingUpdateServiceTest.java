package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the application logic that processes staffing events.
 *
 * The tests pass JSON directly to StaffingUpdateService. This isolates
 * event validation and state updates from the ActiveMQ connection itself.
 */
class StaffingUpdateServiceTest {

    // Used to create JSON messages from test event objects.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsValidStaffingEvent() throws Exception {
        StaffingUpdateService service = new StaffingUpdateService();

        // Arrange: create a valid event for ward W-05.
        StaffingEvent event = event(
                "event-1",
                "W-05",
                4,
                6,
                "2026-11-01T10:00:00Z"
        );

        // Act: serialize the event and pass it to the application service.
        service.accept(objectMapper.writeValueAsString(event));

        // Assert: the event is now available in the ward's stored state.
        StaffingEvent stored = service.findByWardId("W-05").orElseThrow();

        assertEquals("event-1", stored.eventId());
        assertEquals(4, stored.alertLevel());
        assertEquals(6, stored.doctorsOnCall());
    }

    @Test
    void ignoresDuplicateEvent() throws Exception {
        StaffingUpdateService service = new StaffingUpdateService();

        StaffingEvent event = event(
                "event-1",
                "W-05",
                4,
                6,
                "2026-11-01T10:00:00Z"
        );

        String json = objectMapper.writeValueAsString(event);

        // Act: deliver the exact same event twice.
        service.accept(json);
        boolean secondApplication = service.accept(json);

        // Assert: the second copy must not replace the stored event.
        assertFalse(secondApplication);
        assertEquals(1, service.findAll().size());

        assertEquals(
                "event-1",
                service.findByWardId("W-05").orElseThrow().eventId()
        );
    }

    @Test
    void ignoresOlderEvent() throws Exception {
        StaffingUpdateService service = new StaffingUpdateService();

        // The newer event is received first.
        StaffingEvent newer = event(
                "event-newer",
                "W-05",
                5,
                7,
                "2026-11-01T12:00:00Z"
        );

        // Simulate a delayed message arriving after the newer event.
        StaffingEvent older = event(
                "event-older",
                "W-05",
                3,
                5,
                "2026-11-01T10:00:00Z"
        );

        service.accept(objectMapper.writeValueAsString(newer));
        boolean olderApplied = service.accept(
                objectMapper.writeValueAsString(older)
        );

        // Assert: an older schedule must not overwrite the newer schedule.
        assertFalse(olderApplied);

        StaffingEvent stored = service.findByWardId("W-05").orElseThrow();

        assertEquals("event-newer", stored.eventId());
        assertEquals(5, stored.alertLevel());
    }

    @Test
    void rejectsInvalidAlertLevel() throws Exception {
        StaffingUpdateService service = new StaffingUpdateService();

        // Alert level 9 is outside the allowed range of 0 to 8.
        StaffingEvent event = event(
                "event-invalid",
                "W-05",
                9,
                7,
                "2026-11-01T10:00:00Z"
        );

        // The invalid event must be rejected before it changes stored state.
        assertThrows(
                StaffingUpdateService.InvalidStaffingEventException.class,
                () -> service.accept(objectMapper.writeValueAsString(event))
        );

        assertTrue(service.findAll().isEmpty());
    }

    @Test
    void rejectsMalformedJson() {
        StaffingUpdateService service = new StaffingUpdateService();

        // This message cannot be converted into the StaffingEvent contract.
        assertThrows(
                StaffingUpdateService.InvalidStaffingEventException.class,
                () -> service.accept("{ invalid json")
        );
    }

    @Test
    void returnsEmptyForUnknownWard() {
        StaffingUpdateService service = new StaffingUpdateService();

        // No event has been received for this ward.
        assertTrue(service.findByWardId("W-99").isEmpty());
    }

    /**
     * Builds a valid event with the supplied values.
     *
     * Using a helper keeps individual tests focused on the behaviour
     * being tested rather than repeating the event constructor.
     */
    private StaffingEvent event(
            String eventId,
            String wardId,
            int alertLevel,
            int doctorsOnCall,
            String generatedAt
    ) {
        return new StaffingEvent(
                eventId,
                StaffingEvent.EVENT_TYPE,
                StaffingEvent.SCHEMA_VERSION,
                wardId,
                "Paediatrics",
                alertLevel,
                doctorsOnCall,
                "next-24h",
                generatedAt
        );
    }
}