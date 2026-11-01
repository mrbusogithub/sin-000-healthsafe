package co.wethinkcode.healthsafe.mq;

/**
 * ActiveMQ configuration used by Ward Service.
 *
 * Ward Service participates in two messaging flows:
 * - It consumes staffing updates from a topic in Iteration 3.
 * - It will publish equipment-failure alerts to a queue in Stage 4.
 *
 * Keeping both destination names here avoids duplicating broker settings
 * throughout the service.
 */
public final class MqConfig {

    // Broker address can be overridden when running in another environment.
    public static final String BROKER_URL = System.getenv()
            .getOrDefault("ACTIVEMQ_BROKER_URL", "tcp://localhost:61616");

    // Topic used to receive staffing schedule updates.
    public static final String TOPIC = "staffing-events-topic";

    /*
     * Durable subscriptions need a stable client ID and subscription name.
     * ActiveMQ uses these values to identify this subscriber between restarts.
     */
    public static final String CLIENT_ID = "healthsafe-ward-service";
    public static final String SUBSCRIPTION_NAME = "ward-service-staffing-updates";

    // Reserved for the Stage 4 equipment-alerting workflow.
    public static final String QUEUE = "equipment-failure-queue";

    // Configuration classes should not be instantiated.
    private MqConfig() {
    }
}