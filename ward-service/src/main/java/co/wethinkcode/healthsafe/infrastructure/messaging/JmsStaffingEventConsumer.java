package co.wethinkcode.healthsafe.infrastructure.messaging;

import co.wethinkcode.healthsafe.application.StaffingUpdateService;
import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Receives staffing events from the ActiveMQ topic.
 *
 * This class is responsible for the JMS lifecycle:
 * - Connect to the broker.
 * - Register a durable topic subscription.
 * - Receive messages asynchronously.
 * - Pass message content to StaffingUpdateService.
 * - Commit or roll back message processing.
 *
 * It does not validate the complete business contract or manage ward state.
 * Those tasks are delegated to StaffingUpdateService.
 */
public class JmsStaffingEventConsumer implements AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(JmsStaffingEventConsumer.class);

    // These resources are kept open while Ward Service is running.
    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;

    /**
     * Creates the real Ward Service subscription using shared configuration.
     */
    public JmsStaffingEventConsumer(StaffingUpdateService updateService)
            throws JMSException {

        this(
                MqConfig.BROKER_URL,
                MqConfig.CLIENT_ID,
                MqConfig.SUBSCRIPTION_NAME,
                MqConfig.TOPIC,
                updateService
        );
    }

    /**
     * Creates a subscriber using the supplied broker and topic settings.
     *
     * This constructor also allows tests to connect to an embedded
     * ActiveMQ broker rather than requiring the Docker container.
     */
    public JmsStaffingEventConsumer(
            String brokerUrl,
            String clientId,
            String subscriptionName,
            String topicName,
            StaffingUpdateService updateService
    ) throws JMSException {

        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(brokerUrl);

        connection = factory.createConnection();

        /*
         * JMS requires the client ID to be set before creating the session.
         * It identifies this durable subscriber to ActiveMQ.
         */
        connection.setClientID(clientId);

        /*
         * A transacted session lets the consumer explicitly decide when
         * message processing is complete.
         *
         * commit() acknowledges the processed message.
         * rollback() makes a failed message eligible for redelivery.
         */
        session = connection.createSession(true, Session.SESSION_TRANSACTED);

        Topic topic = session.createTopic(topicName);

        /*
         * A durable subscriber can receive persistent messages published
         * while it is offline, provided its subscription already exists.
         *
         * The client ID and subscription name must remain stable between
         * normal application restarts.
         */
        consumer = session.createDurableSubscriber(topic, subscriptionName);

        /*
         * Register an asynchronous listener instead of repeatedly polling
         * the topic. ActiveMQ invokes this callback when a message arrives.
         */
        consumer.setMessageListener(message ->
                receiveMessage(message, updateService)
        );

        // Start message delivery after the listener has been registered.
        connection.start();

        LOGGER.info(
                "Subscribed to topic {} using durable subscription {}",
                topicName,
                subscriptionName
        );
    }

    /**
     * Handles an individual message delivered by ActiveMQ.
     *
     * Valid messages are applied to the application state before being
     * committed. Invalid messages are discarded, while unexpected failures
     * are rolled back so that ActiveMQ can attempt redelivery.
     */
    private void receiveMessage(
            Message message,
            StaffingUpdateService updateService
    ) {
        try {
            // This consumer expects JSON text messages.
            if (!(message instanceof TextMessage textMessage)) {
                LOGGER.warn("Ignoring non-text staffing event");

                // The message has been deliberately discarded.
                session.commit();
                return;
            }

            // Apply the event and determine whether it changed the stored state.
            boolean applied = updateService.accept(textMessage.getText());

            /*
             * Commit only after application processing succeeds.
             * A committed message will not be delivered again to this subscription.
             */
            session.commit();

            if (applied) {
                LOGGER.info(
                        "Applied staffing event {} for ward {}",
                        textMessage.getStringProperty("eventId"),
                        textMessage.getStringProperty("wardId")
                );
            } else {
                // The event was valid, but its timestamp was not newer.
                LOGGER.info(
                        "Ignored duplicate or out-of-order event for ward {}",
                        textMessage.getStringProperty("wardId")
                );
            }

        } catch (StaffingUpdateService.InvalidStaffingEventException e) {

            /*
             * Invalid JSON or invalid event fields will not become valid
             * simply by receiving the same message again.
             *
             * Commit the invalid message so it does not enter an endless
             * redelivery cycle.
             */
            LOGGER.warn("Discarding invalid staffing event: {}", e.getMessage());
            commitOrLog();

        } catch (Exception e) {

            /*
             * This is an unexpected processing failure, not a known
             * contract-validation failure. Roll back so the broker can
             * redeliver the message according to its redelivery policy.
             */
            LOGGER.error("Could not process staffing event; rolling back", e);
            rollbackOrLog();
        }
    }

    /**
     * Acknowledges an invalid message after deliberately discarding it.
     *
     * If committing fails, attempt a rollback rather than silently leaving
     * the transaction in an uncertain state.
     */
    private void commitOrLog() {
        try {
            session.commit();
        } catch (JMSException e) {
            LOGGER.error("Could not commit invalid staffing event", e);
            rollbackOrLog();
        }
    }

    /**
     * Rolls back the current JMS transaction.
     *
     * ActiveMQ can then redeliver the message. The broker's redelivery policy
     * controls how many attempts are made before a message is moved to a
     * dead-letter destination.
     */
    private void rollbackOrLog() {
        try {
            session.rollback();
        } catch (JMSException e) {
            LOGGER.error("Could not roll back JMS session", e);
        }
    }

    /**
     * Releases the subscription and JMS resources when Ward Service stops.
     */
    @Override
    public void close() {

        try {
            consumer.close();
        } catch (JMSException e) {
            LOGGER.warn("Could not close staffing-event consumer", e);
        }

        try {
            session.close();
        } catch (JMSException e) {
            LOGGER.warn("Could not close JMS session", e);
        }

        try {
            connection.close();
        } catch (JMSException e) {
            LOGGER.warn("Could not close JMS connection", e);
        }
    }
}