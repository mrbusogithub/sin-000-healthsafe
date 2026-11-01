package co.wethinkcode.healthsafe.mq;

/**
 * Central configuration for the Staffing Service's ActiveMQ connection.
 *
 * Keeping the broker address and topic name in one place avoids scattering
 * connection details throughout the application.
 */
public final class MqConfig {

    // Allow the broker address to be changed without modifying Java source.
    // The localhost address is used when no environment variable is supplied.
    public static final String BROKER_URL = System.getenv()
            .getOrDefault("ACTIVEMQ_BROKER_URL", "tcp://localhost:61616");

    // Staffing events are broadcast to all interested topic subscribers.
    public static final String TOPIC = "staffing-events-topic";

    // Prevent this configuration class from being instantiated.
    private MqConfig() {
    }
}