package co.wethinkcode.healthsafe.infrastructure.messaging;

import co.wethinkcode.healthsafe.application.StaffingEventPublisher;
import co.wethinkcode.healthsafe.messaging.StaffingEvent;
import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * ActiveMQ implementation of StaffingEventPublisher.
 *
 * This class is part of the infrastructure layer because it knows about
 * JMS, ActiveMQ connections, topics and message delivery.
 *
 * The application layer only knows about the StaffingEventPublisher
 * interface and does not need to know these JMS details.
 */
public class JmsStaffingEventPublisher
        implements StaffingEventPublisher, AutoCloseable {

    // JMS connections and sessions are kept open for reuse rather than
    // creating and destroying them for every staffing schedule.
    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;

    // Converts the Java event object into the JSON message body.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates the publisher using the project's standard broker settings.
     */
    public JmsStaffingEventPublisher() throws JMSException {
        this(MqConfig.BROKER_URL, MqConfig.TOPIC);
    }

    /**
     * Creates the publisher for a specific broker and topic.
     *
     * The second constructor is useful in tests because it allows the
     * publisher to connect to an embedded test broker instead of Docker.
     */
    public JmsStaffingEventPublisher(String brokerUrl, String topicName)
            throws JMSException {

        // The connection factory creates JMS connections to ActiveMQ.
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(brokerUrl);

        connection = factory.createConnection();

        // AUTO_ACKNOWLEDGE is not used to acknowledge messages here;
        // it is the session acknowledgement mode for this producer session.
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // A JMS Topic broadcasts a message to each active subscription.
        Topic topic = session.createTopic(topicName);

        // The producer sends messages to the configured topic.
        producer = session.createProducer(topic);

        /*
         * Persistent delivery asks ActiveMQ to store the message so that
         * a durable subscriber can receive it even if it is temporarily offline.
         */
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
    }

    /**
     * Serializes and publishes a staffing event.
     *
     * This method is synchronized because JMS Session objects are not
     * designed for concurrent use by multiple application threads.
     */
    @Override
    public synchronized void publish(StaffingEvent event) {
        try {
            // Convert the event contract into the JSON format understood
            // by the independent Ward Service application.
            String json = objectMapper.writeValueAsString(event);

            TextMessage message = session.createTextMessage(json);

            // Store useful metadata as message properties. These properties
            // help with broker inspection, filtering and readable consumer logs.
            message.setStringProperty("eventId", event.eventId());
            message.setStringProperty("eventType", event.eventType());
            message.setStringProperty("wardId", event.wardId());

            // Send the message to staffing-events-topic.
            producer.send(message);

            System.out.printf(
                    "[staffing-service] published event %s for ward %s%n",
                    event.eventId(),
                    event.wardId()
            );

        } catch (JMSException | JsonProcessingException e) {
            // Convert low-level messaging/serialization errors into an
            // application-level failure that the API layer can handle.
            throw new IllegalStateException(
                    "Could not publish staffing event to ActiveMQ", e
            );
        }
    }

    /**
     * Releases JMS resources when Staffing Service shuts down.
     *
     * Closing the producer, session and connection prevents resources
     * from being left open after the application stops.
     */
    @Override
    public synchronized void close() {
        try {
            producer.close();
        } catch (JMSException e) {
            System.err.println("[staffing-service] could not close JMS producer: "
                    + e.getMessage());
        }

        try {
            session.close();
        } catch (JMSException e) {
            System.err.println("[staffing-service] could not close JMS session: "
                    + e.getMessage());
        }

        try {
            connection.close();
        } catch (JMSException e) {
            System.err.println("[staffing-service] could not close JMS connection: "
                    + e.getMessage());
        }
    }
}