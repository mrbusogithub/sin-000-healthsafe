package co.wethinkcode.healthsafe.infrastructure.messaging;

import co.wethinkcode.healthsafe.application.StaffingUpdateService;
import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JmsStaffingEventConsumer.
 *
 * The test starts an embedded broker, subscribes Ward Service to a topic,
 * publishes a staffing event and checks that the application's internal
 * state is updated.
 *
 * This verifies the actual JMS receive-and-process path rather than
 * testing StaffingUpdateService alone.
 */
class JmsStaffingEventConsumerTest {

    private BrokerService broker;
    private String brokerUrl;

    private JmsStaffingEventConsumer consumer;
    private StaffingUpdateService updateService;

    /**
     * Start an isolated broker and register the Ward Service subscriber.
     */
    @BeforeEach
    void setUp() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("ward-consumer-test");
        broker.setPersistent(false);
        broker.setUseJmx(false);

        // Use an automatically assigned port to avoid local port conflicts.
        TransportConnector connector =
                broker.addConnector("tcp://127.0.0.1:0");

        broker.start();
        broker.waitUntilStarted();

        brokerUrl = connector.getPublishableConnectString().toString();

        // This is the real application component that will process received events.
        updateService = new StaffingUpdateService();

        /*
         * Create a subscriber using the same production consumer class
         * used by WardServiceApp.
         *
         * The random client ID prevents different test executions from
         * competing for the same durable subscriber identity.
         */
        consumer = new JmsStaffingEventConsumer(
                brokerUrl,
                "ward-test-" + UUID.randomUUID(),
                "ward-test-subscription",
                "staffing-events-test",
                updateService
        );
    }

    @Test
    void receivesEventAndUpdatesWardState() throws Exception {

        // Arrange: build a valid staffing event for ward W-05.
        StaffingEvent event = new StaffingEvent(
                "event-456",
                StaffingEvent.EVENT_TYPE,
                StaffingEvent.SCHEMA_VERSION,
                "W-05",
                "Paediatrics",
                5,
                7,
                "next-24h",
                "2026-11-01T12:00:00Z"
        );

        // Convert the event into the same JSON representation used in production.
        String json = new ObjectMapper().writeValueAsString(event);

        // Act: send the JSON message to the topic.
        publish(json);

        /*
         * Message delivery is asynchronous. Do not assume that the listener
         * has updated application state immediately after publish() returns.
         *
         * Wait for the expected state, but stop after three seconds so the
         * test cannot wait forever if the consumer is broken.
         */
        long deadline = System.currentTimeMillis() + 3_000;

        while (updateService.findByWardId("W-05").isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        // Read the state after the listener has had time to process the event.
        StaffingEvent received =
                updateService.findByWardId("W-05").orElse(null);

        // Assert: confirm the event was applied to Ward Service's state.
        assertNotNull(received);
        assertEquals("event-456", received.eventId());
        assertEquals(5, received.alertLevel());
        assertEquals(7, received.doctorsOnCall());
    }

    /**
     * Test helper that publishes a JSON string to the embedded broker.
     *
     * It deliberately acts as an independent producer so this test verifies
     * that the consumer can receive messages without relying on the
     * Staffing Service publisher implementation.
     */
    private void publish(String json) throws Exception {
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(brokerUrl);

        try (Connection connection = factory.createConnection()) {

            Session session = connection.createSession(
                    false, Session.AUTO_ACKNOWLEDGE
            );

            Topic topic = session.createTopic("staffing-events-test");

            MessageProducer producer = session.createProducer(topic);

            // Match the persistent delivery mode used by Staffing Service.
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            producer.send(session.createTextMessage(json));

            // Release the producer-side resources after sending the message.
            producer.close();
            session.close();
        }
    }

    /**
     * Stop the consumer before stopping the broker.
     *
     * Closing the subscriber first prevents it from attempting to reconnect
     * while the embedded broker is shutting down.
     */
    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.close();
        }

        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }
}