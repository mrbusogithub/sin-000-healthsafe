package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.AlertLevel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Application service responsible for managing the current
 * hospital emergency alert level.
 *
 * HTTP request handling does not belong in this class.
 */
public class AlertLevelService {

    // The initial emergency level remains zero.
    private final AtomicReference<AlertLevel> currentLevel =
            new AtomicReference<>(new AlertLevel(0));

    /**
     * Returns the current alert level.
     */
    public AlertLevel getCurrentLevel() {
        return currentLevel.get();
    }

    /**
     * Validates and updates the current alert level.
     *
     * @param requestedLevel the requested level from the caller
     * @return the updated alert level
     */
    public AlertLevel updateLevel(Integer requestedLevel) {

        if (requestedLevel == null) {
            throw new IllegalArgumentException(
                    "missing 'level'"
            );
        }

        // AlertLevel performs the 0–8 domain validation.
        AlertLevel updatedLevel = new AlertLevel(requestedLevel);

        currentLevel.set(updatedLevel);

        return updatedLevel;
    }
}