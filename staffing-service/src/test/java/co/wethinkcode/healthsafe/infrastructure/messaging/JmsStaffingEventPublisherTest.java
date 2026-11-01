package co.wethinkcode.healthsafe.infrastructure.messaging;

import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JmsStaffingEventPublisher.
 *
 * These tests use an embedded ActiveMQ broker, so they do not depend on
 * the Docker Compose broker running on the developer's machine.
 *
 * The test verifies that the publisher:
 * - Connects to an ActiveMQ broker.
 * - Serializes a StaffingEvent as JSON.
 * - Publishes it to the requested topic.
 * - Uses persistent message delivery.
 */
class JmsStaffingEventPublisherTest {

    private BrokerService broker;
    private String brokerUrl;

    // A separate consumer is used to receive and inspect the published message.
    private Connection subscriberConnection;
    private Session subscriberSession;
    private MessageConsumer subscriber;

    private JmsStaffingEventPublisher publisher;

    /**
     * Starts a fresh, isolated broker before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("staffing-publisher-test");

        // This test broker is temporary; it does not need a persistent store.
        broker.setPersistent(false);
        broker.setUseJmx(false);

        /*
         * Port 0 asks the operating system to allocate an available port.
         * This avoids conflicts with a developer's local ActiveMQ container.
         */
        TransportConnector connector =
                broker.addConnector("tcp://127.0.0.1:0");

        broker.start();
        broker.waitUntilStarted();

        // Obtain the actual address assigned to the embedded broker.
        brokerUrl = connector.getPublishableConnectString().toString();

        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(brokerUrl);

        subscriberConnection = factory.createConnection();
        subscriberSession = subscriberConnection.createSession(
                false, Session.AUTO_ACKNOWLEDGE
        );

        // Create a normal topic consumer to observe messages sent by the publisher.
        Topic topic = subscriberSession.createTopic("staffing-events-test");

        subscriber = subscriberSession.createConsumer(topic);
        subscriberConnection.start();

        // Inject the embedded broker address and test topic into the publisher.
        publisher = new JmsStaffingEventPublisher(
                brokerUrl, "staffing-events-test"
        );
    }

    @Test
    void publishesPersistentJsonEventToTopic() throws Exception {

        // Arrange: create a representative staffing event.
        StaffingEvent event = new StaffingEvent(
                "event-123",
                StaffingEvent.EVENT_TYPE,
                StaffingEvent.SCHEMA_VERSION,
                "W-05",
                "Paediatrics",
                4,
                6,
                "next-24h",
                "2026-11-01T10:00:00Z"
        );

        // Act: ask the production publisher implementation to send the event.
        publisher.publish(event);

        // Wait for the message to arrive, with a timeout to prevent a hanging test.
        Message received = subscriber.receive(2_000);

        // Assert: a message was delivered and it is a JSON text message.
        assertNotNull(received);
        assertInstanceOf(TextMessage.class, received);

        // Confirm that the publisher configured persistent delivery.
        assertEquals(
                DeliveryMode.PERSISTENT,
                received.getJMSDeliveryMode()
        );

        TextMessage textMessage = (TextMessage) received;

        // Check that important event fields are present in the JSON body.
        assertTrue(textMessage.getText().contains("W-05"));
        assertTrue(
                textMessage.getText().contains("STAFFING_SCHEDULE_UPDATED")
        );

        // Check the JMS metadata property as well as the JSON body.
        assertEquals("event-123", textMessage.getStringProperty("eventId"));
    }

    /**
     * Stop and release everything created for the test.
     *
     * This prevents broker threads, connections or sockets from leaking
     * into subsequent tests.
     */
    @AfterEach
    void tearDown() throws Exception {
        if (publisher != null) {
            publisher.close();
        }

        if (subscriber != null) {
            subscriber.close();
        }

        if (subscriberSession != null) {
            subscriberSession.close();
        }

        if (subscriberConnection != null) {
            subscriberConnection.close();
        }

        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }
}