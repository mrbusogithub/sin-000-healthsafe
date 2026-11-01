package co.wethinkcode.healthsafe.domain;

/**
 * Represents a valid hospital emergency alert level.
 *
 * The domain object enforces the allowed range so invalid
 * levels cannot be represented as valid AlertLevel instances.
 */
public record AlertLevel(int value) {

    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 8;

    public AlertLevel {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "level must be between "
                            + MIN_VALUE + " and " + MAX_VALUE
            );
        }
    }
}