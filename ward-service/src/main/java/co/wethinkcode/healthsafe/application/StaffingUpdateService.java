package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Applies staffing events received by Ward Service.
 *
 * This class contains the application rules for processing an event:
 * - Convert the JSON message into a StaffingEvent.
 * - Validate its fields and supported contract version.
 * - Ignore duplicate or older updates.
 * - Store the latest accepted schedule for each ward.
 *
 * It does not know how ActiveMQ delivers messages. That responsibility
 * belongs to JmsStaffingEventConsumer.
 */
public class StaffingUpdateService {

    // Ward IDs must use the agreed identifier format.
    private static final Pattern WARD_ID_PATTERN =
            Pattern.compile("[A-Z0-9-]{1,20}");

    // Reuse the mapper when converting incoming JSON messages.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /*
     * Stores the latest accepted event for each ward.
     *
     * ConcurrentHashMap allows API request threads to read the state while
     * the JMS listener updates it.
     *
     * This is an in-memory projection, so it is reset when the service restarts.
     */
    private final ConcurrentMap<String, StaffingEvent> latestByWard =
            new ConcurrentHashMap<>();

    /**
     * Parses, validates and applies an incoming staffing event.
     *
     * @param json JSON body received from the ActiveMQ message
     * @return true if the event replaced the stored schedule; false if it
     *         was an older or duplicate update
     */
    public boolean accept(String json) {
        StaffingEvent event;

        try {
            // Convert the message body into the local event contract.
            event = objectMapper.readValue(json, StaffingEvent.class);

        } catch (JsonProcessingException e) {
            // Invalid JSON cannot be processed by this service.
            throw new InvalidStaffingEventException(
                    "Message is not valid staffing-event JSON", e
            );
        }

        // Validate the event before allowing it to change application state.
        validate(event);

        Instant incomingTime = Instant.parse(event.generatedAt());

        /*
         * compute() performs the comparison and replacement for one ward
         * atomically. Two simultaneous updates for the same ward therefore
         * cannot overwrite each other halfway through the operation.
         */
        AtomicBoolean applied = new AtomicBoolean(false);

        latestByWard.compute(event.wardId(), (wardId, current) -> {

            // The first event received for a ward becomes its current schedule.
            if (current == null) {
                applied.set(true);
                return event;
            }

            Instant currentTime = Instant.parse(current.generatedAt());

            /*
             * Only accept a newer timestamp.
             *
             * An identical timestamp is treated as a duplicate or stale event,
             * so it does not replace the currently stored schedule.
             */
            if (incomingTime.isAfter(currentTime)) {
                applied.set(true);
                return event;
            }

            // Keep the current schedule when the incoming event is not newer.
            return current;
        });

        return applied.get();
    }

    /**
     * Returns the latest accepted staffing event for every ward.
     *
     * Sorting by ward ID makes the API output predictable and easier to test.
     */
    public List<StaffingEvent> findAll() {
        return latestByWard.values()
                .stream()
                .sorted(Comparator.comparing(StaffingEvent::wardId))
                .toList();
    }

    /**
     * Finds the latest staffing event for a particular ward.
     *
     * Ward IDs are normalized so callers can use either lowercase or
     * uppercase identifiers.
     */
    public Optional<StaffingEvent> findByWardId(String wardId) {
        if (wardId == null || wardId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                latestByWard.get(wardId.trim().toUpperCase(Locale.ROOT))
        );
    }

    /**
     * Ensures that an event meets the agreed messaging contract.
     *
     * Validation is performed in the consumer service as well as at the
     * publisher boundary. A message should never be trusted simply because
     * it came from the expected topic.
     */
    private void validate(StaffingEvent event) {

        if (event == null) {
            throw new InvalidStaffingEventException("Event is required");
        }

        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new InvalidStaffingEventException("eventId is required");
        }

        if (!StaffingEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidStaffingEventException("Unsupported eventType");
        }

        if (event.schemaVersion() != StaffingEvent.SCHEMA_VERSION) {
            throw new InvalidStaffingEventException("Unsupported schemaVersion");
        }

        if (event.wardId() == null
                || !WARD_ID_PATTERN.matcher(event.wardId()).matches()) {
            throw new InvalidStaffingEventException("Invalid wardId");
        }

        if (event.department() == null || event.department().isBlank()) {
            throw new InvalidStaffingEventException("department is required");
        }

        if (event.alertLevel() < 0 || event.alertLevel() > 8) {
            throw new InvalidStaffingEventException(
                    "alertLevel must be between 0 and 8"
            );
        }

        if (event.doctorsOnCall() < 1) {
            throw new InvalidStaffingEventException(
                    "doctorsOnCall must be positive"
            );
        }

        if (event.scheduleFor() == null || event.scheduleFor().isBlank()) {
            throw new InvalidStaffingEventException("scheduleFor is required");
        }

        if (event.generatedAt() == null || event.generatedAt().isBlank()) {
            throw new InvalidStaffingEventException("generatedAt is required");
        }

        // Instant.parse also checks that the timestamp uses a valid
        // ISO-8601 instant representation.
        try {
            Instant.parse(event.generatedAt());
        } catch (Exception e) {
            throw new InvalidStaffingEventException(
                    "generatedAt must be an ISO-8601 timestamp", e
            );
        }
    }

    /**
     * Represents an event that cannot be accepted because it violates
     * the agreed message contract.
     *
     * The JMS consumer handles this separately from unexpected processing
     * failures so invalid messages are not retried indefinitely.
     */
    public static class InvalidStaffingEventException
            extends RuntimeException {

        public InvalidStaffingEventException(String message) {
            super(message);
        }

        public InvalidStaffingEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}